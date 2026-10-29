package com.sportify.demoqr.service;

import com.sportify.core.common.SecurityUtils;
import com.sportify.demoqr.config.DemoQrSettings;
import com.sportify.identity.entity.UserAccount;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Service
@Profile("(dev | qr-demo) & !prod & !production")
@ConditionalOnProperty(name = "app.demo.qr.enabled", havingValue = "true")
public class DemoQrService {
    public enum State { PENDING, CONFIRMED, CANCELED, EXPIRED }
    public enum Bank {
        VIETCOMBANK_DEMO("Vietcombank (demo)"), BIDV_DEMO("BIDV (demo)"),
        TECHCOMBANK_DEMO("Techcombank (demo)"), MB_BANK_DEMO("MB Bank (demo)");
        public final String label;
        Bank(String label) { this.label = label; }
    }

    public record View(UUID id, long paymentId, String reference, BigDecimal amount, String currency,
                       Bank bankCode, String bankLabel, Instant createdAt, Instant expiresAt,
                       Instant serverTime, State state, String mode) { }
    public record Created(View session, String confirmationUrl) {
        @Override public String toString() { return "Created[confirmationUrl=REDACTED]"; }
    }

    private static final int MAX_SESSIONS = 1000;
    private final DemoPaymentReader reader;
    private final DemoQrSettings settings;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<String, UUID> verifiers = new HashMap<>();
    private final Map<Long, ArrayDeque<Instant>> creates = new HashMap<>();
    private final Map<Long, ArrayDeque<Instant>> accesses = new HashMap<>();

    public DemoQrService(DemoPaymentReader reader, DemoQrSettings settings, Clock clock) {
        this.reader = reader;
        this.settings = settings;
        this.clock = clock;
    }

    /** One monitor serializes replacement, expiry and all terminal transitions in this demo process. */
    public synchronized Created create(long paymentId, Bank bank, UserAccount actor) {
        owner(actor);
        cleanup();
        limit(creates, actor.getId(), 5);
        if (bank == null) throw DemoQrException.unavailable();
        var snapshot = reader.read(paymentId, actor);
        if (sessions.size() >= MAX_SESSIONS) {
            throw new DemoQrException(HttpStatus.TOO_MANY_REQUESTS, "DEMO_CAPACITY", "Demo capacity reached. Try again later.");
        }
        sessions.values().stream().filter(s -> s.owner == actor.getId() && s.paymentId == paymentId)
                .forEach(s -> { expire(s); if (s.state == State.PENDING) s.state = State.CANCELED; });
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        Session session = new Session(UUID.randomUUID(), actor.getId(), snapshot.paymentId(), snapshot.amount(),
                bank, now, now.plusSeconds(settings.lifetimeSeconds()), digest(token));
        sessions.put(session.id, session);
        verifiers.put(session.verifier, session.id);
        return new Created(view(session), settings.publicBaseUrl() + "demo/qr/confirm#token=" + token);
    }

    public synchronized View get(String id, UserAccount actor) {
        owner(actor);
        limit(accesses, actor.getId(), 60);
        Session session;
        try { session = sessions.get(UUID.fromString(id)); }
        catch (IllegalArgumentException ex) { throw DemoQrException.unavailable(); }
        return view(authorized(session, actor));
    }

    /** Preview is authenticated and read-only with respect to confirmation. Scanning never confirms. */
    public synchronized View preview(String token, UserAccount actor) {
        return view(byToken(token, actor));
    }

    public synchronized View confirm(String token, UserAccount actor) {
        Session session = byToken(token, actor);
        if (session.state == State.CONFIRMED) return view(session);
        requirePending(session);
        try {
            var current = reader.read(session.paymentId, actor);
            if (current.amount().compareTo(session.amount) != 0) throw DemoQrException.unavailable();
        } catch (DemoQrException ex) {
            session.state = State.CANCELED;
            throw ex;
        }
        // The database read may cross the deadline. Sample server time again at the transition.
        expire(session);
        requirePending(session);
        session.state = State.CONFIRMED;
        return view(session);
    }

    public synchronized View cancel(String id, UserAccount actor) {
        owner(actor);
        limit(accesses, actor.getId(), 60);
        Session session;
        try { session = sessions.get(UUID.fromString(id)); }
        catch (IllegalArgumentException ex) { throw DemoQrException.unavailable(); }
        session = authorized(session, actor);
        expire(session);
        if (session.state == State.PENDING) session.state = State.CANCELED;
        return view(session);
    }

    private Session byToken(String token, UserAccount actor) {
        owner(actor);
        limit(accesses, actor.getId(), 60);
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw DemoQrException.unavailable();
        return authorized(sessions.get(verifiers.get(digest(token))), actor);
    }

    private Session authorized(Session session, UserAccount actor) {
        if (session == null || session.owner != actor.getId()) throw DemoQrException.unavailable();
        expire(session);
        return session;
    }

    private static void owner(UserAccount actor) {
        if (!SecurityUtils.isMember(actor) || actor.getId() == null || !"ACTIVE".equals(actor.getStatus())) {
            throw DemoQrException.unavailable();
        }
    }

    private void requirePending(Session session) {
        if (session.state != State.PENDING) throw new DemoQrException(HttpStatus.CONFLICT,
                "DEMO_" + session.state.name(), "Demo session is " + session.state.name() + ". The real payment remains unchanged.");
    }

    private void expire(Session session) {
        if (session.state == State.PENDING && !clock.instant().isBefore(session.expiresAt)) session.state = State.EXPIRED;
    }

    private View view(Session session) {
        expire(session);
        return new View(session.id, session.paymentId, "DEMO-" + session.paymentId, session.amount, "VND",
                session.bank, session.bank.label, session.createdAt, session.expiresAt, clock.instant(), session.state, "NON_PAYABLE");
    }

    private void cleanup() {
        Instant cutoff = clock.instant().minusSeconds(600);
        sessions.values().removeIf(s -> {
            expire(s);
            if (s.expiresAt.isBefore(cutoff)) { verifiers.remove(s.verifier); return true; }
            return false;
        });
        creates.values().removeIf(q -> q.isEmpty() || q.peekLast().isBefore(clock.instant().minusSeconds(60)));
        accesses.values().removeIf(q -> q.isEmpty() || q.peekLast().isBefore(clock.instant().minusSeconds(60)));
    }

    private void limit(Map<Long, ArrayDeque<Instant>> buckets, long owner, int max) {
        cleanup();
        if (!buckets.containsKey(owner) && buckets.size() >= MAX_SESSIONS) {
            throw new DemoQrException(HttpStatus.TOO_MANY_REQUESTS, "DEMO_RATE_LIMIT", "Demo rate limit reached. Try again later.");
        }
        var bucket = buckets.computeIfAbsent(owner, ignored -> new ArrayDeque<>());
        Instant now = clock.instant();
        while (!bucket.isEmpty() && !bucket.peekFirst().isAfter(now.minusSeconds(60))) bucket.removeFirst();
        if (bucket.size() >= max) throw new DemoQrException(HttpStatus.TOO_MANY_REQUESTS,
                "DEMO_RATE_LIMIT", "Demo rate limit reached. Try again later.");
        bucket.addLast(now);
    }

    private static String digest(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable"); }
    }

    private static final class Session {
        private final UUID id;
        private final long owner;
        private final long paymentId;
        private final BigDecimal amount;
        private final Bank bank;
        private final Instant createdAt;
        private final Instant expiresAt;
        private final String verifier;
        private State state = State.PENDING;

        private Session(UUID id, long owner, long paymentId, BigDecimal amount, Bank bank,
                        Instant createdAt, Instant expiresAt, String verifier) {
            this.id = id; this.owner = owner; this.paymentId = paymentId; this.amount = amount;
            this.bank = bank; this.createdAt = createdAt; this.expiresAt = expiresAt; this.verifier = verifier;
        }
    }
}
