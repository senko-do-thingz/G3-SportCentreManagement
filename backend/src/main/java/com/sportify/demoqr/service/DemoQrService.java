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
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Supplier;

/** Durable confirmation intent only. Financial writers are deliberately absent. */
@Service
@Profile("qr-simulator & !prod & !production")
@ConditionalOnProperty(name = "app.payment.simulator.enabled", havingValue = "true")
public class DemoQrService {
    public enum State { PENDING, AUTHORIZED_PENDING_SETTLEMENT, CANCELED, EXPIRED, SETTLED }
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
    private record Outcome<T>(T value, DemoQrException error) { }
    private final DemoPaymentReader reader;
    private final DemoQrSettings settings;
    private final SqlQrStore store;
    private final SecureRandom random = new SecureRandom();

    public DemoQrService(DemoPaymentReader reader, DemoQrSettings settings, SqlQrStore store) {
        this.reader = reader; this.settings = settings; this.store = store;
    }

    public Created create(long paymentId, Bank bank, UUID requestId, UserAccount actor) {
        budget(actor, true);
        if (paymentId <= 0 || bank == null || requestId == null) throw DemoQrException.unavailable();
        return store.transaction(() -> {
            var snapshot = reader.read(paymentId, actor);
            if (store.requestExists(actor.getId(), requestId)) {
                throw new DemoQrException(HttpStatus.CONFLICT, "SIMULATOR_REISSUE_REQUIRED",
                        "Creation already processed. Start a new session; previous tokens cannot be recovered.");
            }
            store.replace(paymentId, actor.getId());
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            UUID id = store.insert(paymentId, actor.getId(), snapshot.amount(), bank, digest(token), requestId, settings.lifetimeSeconds());
            return new Created(view(store.findId(id, actor.getId())), settings.publicBaseUrl() + "demo/qr/confirm#token=" + token);
        });
    }

    public View get(String id, UserAccount actor) { return byId(id, actor, false); }
    public View cancel(String id, UserAccount actor) { return byId(id, actor, true); }

    private View byId(String id, UserAccount actor, boolean cancel) {
        budget(actor, false);
        UUID parsed;
        try { parsed = UUID.fromString(id); }
        catch (IllegalArgumentException | NullPointerException ignored) { throw DemoQrException.unavailable(); }
        return committed(() -> {
            var candidate = store.findId(parsed, actor.getId());
            reader.requireOwner(candidate.paymentId(), actor);
            var session = store.expire(store.lock(parsed, actor.getId()));
            if (!cancel && (session.state() == State.PENDING || session.state() == State.AUTHORIZED_PENDING_SETTLEMENT)) {
                var error = invalidateIfStale(session, actor);
                if (error != null) return new Outcome<>(null, error);
            }
            return new Outcome<>(view(cancel ? store.cancel(session) : session), null);
        });
    }

    public View preview(String token, UserAccount actor) { return byToken(token, actor, false); }
    public View confirm(String token, UserAccount actor) { return byToken(token, actor, true); }

    private View byToken(String token, UserAccount actor, boolean confirm) {
        budget(actor, false);
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw DemoQrException.unavailable();
        return committed(() -> {
            var candidate = store.findVerifier(digest(token), actor.getId());
            // Every path locks Payment before its session, matching existing financial writers.
            reader.requireOwner(candidate.paymentId(), actor);
            var session = store.expire(store.lock(candidate.id(), actor.getId()));
            if (!confirm) {
                if (session.state() == State.PENDING || session.state() == State.AUTHORIZED_PENDING_SETTLEMENT) {
                    var error = invalidateIfStale(session, actor);
                    if (error != null) return new Outcome<>(null, error);
                }
                return new Outcome<>(view(session), null);
            }
            if (session.state() != State.PENDING && session.state() != State.AUTHORIZED_PENDING_SETTLEMENT) {
                return new Outcome<>(null, stateError(session));
            }
            var error = invalidateIfStale(session, actor);
            if (error != null) return new Outcome<>(null, error);
            // Expiry is also checked in the actual SQL UPDATE, after the payment validation and lock wait.
            session = store.authorize(session);
            return session.state() == State.AUTHORIZED_PENDING_SETTLEMENT
                    ? new Outcome<>(view(session), null) : new Outcome<>(null, stateError(session));
        });
    }

    private DemoQrException invalidateIfStale(SqlQrStore.Session session, UserAccount actor) {
        try {
            var current = reader.read(session.paymentId(), actor);
            if (current.amount().compareTo(session.amount()) != 0 || !current.currency().equals(session.currency())) {
                throw DemoQrException.unavailable();
            }
            return null;
        } catch (DemoQrException error) {
            store.cancel(session);
            return error;
        }
    }

    /** Commit expiry/revocation before reporting denial; throwing inside a transaction would resurrect state. */
    private View committed(Supplier<Outcome<View>> work) {
        var outcome = store.transaction(work);
        if (outcome.error() != null) throw outcome.error();
        return outcome.value();
    }

    private void budget(UserAccount actor, boolean create) {
        if (!SecurityUtils.isMember(actor) || actor.getId() == null || !"ACTIVE".equals(actor.getStatus())) {
            throw DemoQrException.unavailable();
        }
        if (!store.spend(actor.getId(), create)) throw new DemoQrException(HttpStatus.TOO_MANY_REQUESTS,
                "SIMULATOR_RATE_LIMIT", "Simulation rate limit reached. Retry after 60 seconds.");
    }

    private static DemoQrException stateError(SqlQrStore.Session session) {
        return new DemoQrException(HttpStatus.CONFLICT, "SIMULATOR_" + session.state(),
                "Simulation session is " + session.state() + ". Payment is not settled.");
    }

    private View view(SqlQrStore.Session session) {
        return new View(session.id(), session.paymentId(), "DEMO-" + session.paymentId(), session.amount(), session.currency(),
                session.bank(), session.bank().label, session.createdAt(), session.expiresAt(), store.now(), session.state(), "NON_PAYABLE");
    }

    static String digest(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
}
