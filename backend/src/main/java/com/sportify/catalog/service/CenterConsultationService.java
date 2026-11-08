package com.sportify.catalog.service;

import com.sportify.catalog.dto.TrainingGuidanceResponse.Consultation;
import com.sportify.catalog.dto.TrainingGuidanceResponse.ConsultationSource;
import com.sportify.core.exception.BusinessRuleException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Date;
import java.sql.Time;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Flow 6 Member assistant. A bounded knowledge service: it answers only from read-only center queries and fixed text,
 * never stores the conversation and never books, cancels or pays. Questions may be in English or Vietnamese.
 *
 * <p>Order of decisions for one question:
 * <ol>
 *   <li>Feature switch, Member consent and length checks.</li>
 *   <li>Keyword guard: private, injection and transaction requests are refused, and health questions go to a human
 *       Coach. These questions are never sent to an AI provider.</li>
 *   <li>Optional AI classification ({@link AssistantIntentClassifier}) from allowlisted keywords only
 *       ({@link AssistantAiPolicy#keywords}); the question text never leaves the server; any failure or an
 *       UNKNOWN result uses the keyword rules instead.</li>
 *   <li>The answer is built from center data. Personal answers read only the signed-in Member's own records.</li>
 * </ol>
 */
@Service
@Transactional(readOnly=true)
public class CenterConsultationService {
    static final String RULES_MODE="KNOWLEDGE_BASED";
    static final String AI_MODE="AI_ASSISTED";
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(CenterConsultationService.class);
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final boolean enabled;
    private final VisibleTrainingSessions visibility;
    private AssistantIntentClassifier classifier;

    public CenterConsultationService(JdbcTemplate jdbc, Clock clock,
        @Value("${app.training.consultation.enabled:true}") boolean enabled, VisibleTrainingSessions visibility) {
        this.jdbc=jdbc;this.clock=clock;this.enabled=enabled;this.visibility=visibility;
    }

    /** Optional AI classification; absent unless app.assistant.ai.enabled=true. */
    @Autowired(required=false)
    void setClassifier(AssistantIntentClassifier classifier) { this.classifier=classifier; }

    /** Answer without a signed-in Member: personal questions are not answered. */
    public Consultation answer(String question,boolean optIn) { return answer(null,question,optIn); }

    /**
     * Runs without a transaction so that no database connection is held while the optional AI classifier is called;
     * every query below is a single read.
     */
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public Consultation answer(Long memberId,String question,boolean optIn) {
        boolean early=question!=null && vietnamese(question);
        if(!enabled)return response(early,RULES_MODE,"UNAVAILABLE",t(early,"Automated center consultation is disabled.",
            "T\u01B0 v\u1EA5n t\u1EF1 \u0111\u1ED9ng c\u1EE7a trung t\u00E2m \u0111ang t\u1EAFt."),List.of());
        if(!optIn)return response(early,RULES_MODE,"CONSENT_REQUIRED",t(early,"Enable automated consultation before asking a question. No conversation history is stored.",
            "H\u00E3y b\u1EADt t\u01B0 v\u1EA5n t\u1EF1 \u0111\u1ED9ng tr\u01B0\u1EDBc khi \u0111\u1EB7t c\u00E2u h\u1ECFi. H\u1EC7 th\u1ED1ng kh\u00F4ng l\u01B0u l\u1ECBch s\u1EED tr\u00F2 chuy\u1EC7n."),List.of());
        if(question==null || question.isBlank() || question.length()>500)
            throw new BusinessRuleException("Enter a center question of 1-500 characters");
        // NFC so that accented keywords match whether the browser sent composed or decomposed characters.
        String raw=Normalizer.normalize(question.trim(),Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        String plain=plain(raw);
        boolean vi=vietnamese(raw,plain);
        String guard=guard(raw,plain);
        if(guard!=null)return guard.equals("COACH")?coach(vi,RULES_MODE):unknown(vi,RULES_MODE);

        String mode=RULES_MODE;
        String intent=rules(raw,plain);
        String day=day(raw,plain);
        Faq faq=faq(raw,plain);
        String aiSport=null;
        if(classifier!=null && memberId!=null) {
            try {
                var sports=jdbc.queryForList("SELECT name FROM sport WHERE is_active=1 ORDER BY id",String.class);
                // Fail closed: only allowlisted service keywords may leave the server, and none means no provider call.
                var keywords=AssistantAiPolicy.keywords(question,sports);
                var result=keywords.isEmpty() || !aiAllowed(memberId)?null:classifier.classify(keywords,sports);
                if(result!=null && !"UNKNOWN".equals(result.intent())) {
                    mode=AI_MODE;intent=result.intent();day=result.day();aiSport=result.sport();
                    if(intent.equals("FAQ"))faq=FAQS.stream().filter(item->item.id()==result.faqId()).findFirst().orElse(null);
                }
            } catch(RuntimeException failure) {
                // Never log the question or the provider output; the exception class is enough to diagnose outages.
                log.warn("Assistant AI classification failed, using keyword rules: {}",failure.getClass().getSimpleName());
            }
        }
        return switch(intent) {
            case "PRIVATE","UNKNOWN" -> unknown(vi,mode);
            case "COACH" -> coach(vi,mode);
            case "FAQ" -> faq==null?unknown(vi,mode):faqAnswer(vi,mode,faq);
            case "BOOKING" -> response(vi,mode,"ANSWERED",t(vi,"Open a session, check its current availability and suitable sport packages, then review and confirm in the booking flow. Use My bookings to check cancellation eligibility. This consultation never books, cancels, takes payment or issues refunds.",
                    "M\u1EDF m\u1ED9t bu\u1ED5i h\u1ECDc, ki\u1EC3m tra s\u1ED1 ch\u1ED7 c\u00F2n tr\u1ED1ng v\u00E0 g\u00F3i t\u1EADp ph\u00F9 h\u1EE3p, r\u1ED3i xem l\u1EA1i v\u00E0 x\u00E1c nh\u1EADn trong b\u01B0\u1EDBc \u0111\u1EB7t ch\u1ED7. V\u00E0o My bookings \u0111\u1EC3 xem bu\u1ED5i n\u00E0o c\u00F2n \u0111\u01B0\u1EE3c h\u1EE7y. Tr\u1EE3 l\u00FD n\u00E0y kh\u00F4ng bao gi\u1EDD t\u1EF1 \u0111\u1EB7t ch\u1ED7, h\u1EE7y, thanh to\u00E1n hay ho\u00E0n ti\u1EC1n."),
                List.of(new ConsultationSource("PRODUCT_GUIDANCE",0,t(vi,"Session and booking workflow","Quy tr\u00ECnh \u0111\u1EB7t bu\u1ED5i h\u1ECDc"),
                    t(vi,"Eligibility and credits are checked by the existing booking service.","\u0110i\u1EC1u ki\u1EC7n \u0111\u1EB7t v\u00E0 s\u1ED1 bu\u1ED5i trong g\u00F3i \u0111\u01B0\u1EE3c ki\u1EC3m tra khi \u0111\u1EB7t ch\u1ED7."),"/member/classes")));
            case "CONTACT" -> response(vi,mode,"ANSWERED",t(vi,"Reception can help with questions the assistant cannot answer, such as account, payment or discount checks. Send a support request and reception will reply to you in My support requests.",
                    "L\u1EC5 t\u00E2n c\u00F3 th\u1EC3 gi\u00FAp nh\u1EEFng c\u00E2u tr\u1EE3 l\u00FD kh\u00F4ng tr\u1EA3 l\u1EDDi \u0111\u01B0\u1EE3c, nh\u01B0 t\u00E0i kho\u1EA3n, thanh to\u00E1n hay ki\u1EC3m tra gi\u1EA3m gi\u00E1. H\u00E3y g\u1EEDi y\u00EAu c\u1EA7u h\u1ED7 tr\u1EE3, l\u1EC5 t\u00E2n s\u1EBD tr\u1EA3 l\u1EDDi b\u1EA1n trong m\u1EE5c My support requests."),
                List.of(new ConsultationSource("SUPPORT",0,t(vi,"Contact reception","Li\u00EAn h\u1EC7 l\u1EC5 t\u00E2n"),
                    t(vi,"Opens the support request form. Nothing is sent until you submit it.","M\u1EDF form y\u00EAu c\u1EA7u h\u1ED7 tr\u1EE3. Ch\u01B0a c\u00F3 g\u00EC \u0111\u01B0\u1EE3c g\u1EEDi cho \u0111\u1EBFn khi b\u1EA1n b\u1EA5m g\u1EEDi."),"/member/support")));
            case "MY_PACKAGES" -> memberId==null?signIn(vi,mode):myPackages(vi,mode,memberId);
            case "MY_BOOKINGS" -> memberId==null?signIn(vi,mode):myBookings(vi,mode,memberId);
            case "MY_TRAINING" -> memberId==null?signIn(vi,mode):myTraining(vi,mode,memberId);
            case "PACKAGES" -> packages(vi,mode,sport(raw,plain,aiSport));
            default -> sessions(vi,mode,sport(raw,plain,aiSport),day);
        };
    }

    /** Common Vietnamese words written without accents, for questions typed on a keyboard without Vietnamese input. */
    private static final Pattern VI_WORDS=Pattern.compile("\\b(toi|cua|khong|ko|bao nhieu|the nao|minh|duoc|nhung|gi|nao|vay|nhe|xin|lam sao|o dau|khi nao)\\b");

    /** True when the question is Vietnamese: it has Vietnamese accents or common unaccented Vietnamese words. */
    static boolean vietnamese(String question) {
        String raw=Normalizer.normalize(question.trim(),Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        return vietnamese(raw,plain(raw));
    }
    static boolean vietnamese(String raw,String plain) {
        return !raw.equals(plain) || VI_WORDS.matcher(plain).find();
    }
    private static String t(boolean vi,String english,String vietnamese) { return vi?vietnamese:english; }
    private static final java.time.format.DateTimeFormatter VI_DATE=java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static String date(boolean vi,java.sql.Date value) { return vi?value.toLocalDate().format(VI_DATE):value.toString(); }
    private static String time(boolean vi,Time value) { return vi?value.toLocalTime().toString():value.toString(); }
    private static String price(boolean vi,java.math.BigDecimal value) {
        return vi?String.format(Locale.GERMANY,"%,d",value.setScale(0,java.math.RoundingMode.HALF_UP).longValue()):value.toPlainString();
    }

    /** AI classifications one Member may trigger per window; further questions in the window use the keyword rules. */
    static final int AI_CALLS_PER_WINDOW=20;
    static final java.time.Duration AI_WINDOW=java.time.Duration.ofMinutes(10);
    private final java.util.concurrent.ConcurrentHashMap<Long,java.util.ArrayDeque<java.time.Instant>> aiCalls=new java.util.concurrent.ConcurrentHashMap<>();

    /** Bounds provider cost and abuse per Member. Questions without a signed-in Member never use AI. */
    boolean aiAllowed(Long memberId) {
        if(memberId==null)return false;
        var now=clock.instant();
        var calls=aiCalls.computeIfAbsent(memberId,id->new java.util.ArrayDeque<>());
        synchronized(calls) {
            while(!calls.isEmpty() && !calls.peekFirst().isAfter(now.minus(AI_WINDOW)))calls.pollFirst();
            if(calls.size()>=AI_CALLS_PER_WINDOW)return false;
            calls.addLast(now);
            return true;
        }
    }

    /** Rule-based intent of an already lower-cased question, including the guard; kept for tests and fallback. */
    static String intent(String text) {
        String plain=plain(text);
        String guard=guard(text,plain);
        return guard!=null?guard:rules(text,plain);
    }

    /** Lower-cased text without Vietnamese diacritics, so "b\u00F3ng \u0111\u00E1" and "bong da" match the same keyword. */
    static String plain(String text) {
        String stripped=Normalizer.normalize(text.replace('\u0111','d').replace('\u0110','d'),Normalizer.Form.NFD).replaceAll("\\p{M}+","");
        return stripped.toLowerCase(Locale.ROOT);
    }

    /**
     * Returns UNKNOWN for private, injection and transaction requests, COACH for health questions, or null.
     * Accented keywords are matched on the original text; unaccented Vietnamese keywords on whole words of the plain
     * text, so that short words that differ only in accents ("\u0111au" pain, "\u0111\u1EA7u" head) are not confused.
     */
    static String guard(String raw,String plain) {
        if(contains(raw,"password","secret","token","sql","ignore previous","other member","book for me","cancel for me","refund me",
            "ignore all","ignore your","ignore the","disregard","system prompt","jailbreak","developer mode","another member","member's",
            "drop table","delete from","insert into","select *","phone number","home address","email address",
            "m\u1EADt kh\u1EA9u","th\u00E0nh vi\u00EAn kh\u00E1c","ng\u01B0\u1EDDi kh\u00E1c","s\u1ED1 \u0111i\u1EC7n tho\u1EA1i","\u0111\u1ECBa ch\u1EC9 nh\u00E0","b\u1ECF qua h\u01B0\u1EDBng d\u1EABn","b\u1ECF qua m\u1ECDi","\u0111\u1EB7t gi\u00FAp","\u0111\u1EB7t h\u1ED9",
            "h\u1EE7y gi\u00FAp","hu\u1EF7 gi\u00FAp","h\u1EE7y h\u1ED9","hu\u1EF7 h\u1ED9","ho\u00E0n ti\u1EC1n cho t\u00F4i","thanh to\u00E1n gi\u00FAp","thanh to\u00E1n h\u1ED9")
            || matchesWord(raw,PRIVATE_WORDS) || matchesWord(plain,PRIVATE_PLAIN))return "UNKNOWN";
        // Personal health and injury questions always go to a human, even when they mention an FAQ topic.
        if(contains(raw,"injury","injured","pain","hurt","sore","medical","doctor","sick","illness","pregnan","heart","blood pressure",
            "asthma","diabetes","medication","medicine","diet","weight loss","lose weight","rehab","sprain","dizzy","surgery","physio",
            "\u0111au","ch\u1EA5n th\u01B0\u01A1ng","b\u1ECB th\u01B0\u01A1ng","b\u1EC7nh","b\u00E1c s\u0129","thu\u1ED1c","\u0103n ki\u00EAng","gi\u1EA3m c\u00E2n","ch\u00F3ng m\u1EB7t","ph\u1EABu thu\u1EADt","mang thai","c\u00F3 b\u1EA7u",
            "tim m\u1EA1ch","huy\u1EBFt \u00E1p","hen suy\u1EC5n","ti\u1EC3u \u0111\u01B0\u1EDDng","tr\u1ECB li\u1EC7u","bong g\u00E2n","\u1ED1m")
            || matchesWord(plain,HEALTH_PLAIN))return "COACH";
        return null;
    }

    /** Rule-based intent after the guard. */
    static String rules(String raw,String plain) {
        if(faq(raw,plain)!=null)return "FAQ";
        if(contains(raw,"homework","coach feedback","my feedback","my training","my progress","my results","my result",
            "b\u00E0i t\u1EADp v\u1EC1 nh\u00E0","nh\u1EADn x\u00E9t","ph\u1EA3n h\u1ED3i c\u1EE7a","k\u1EBFt qu\u1EA3 t\u1EADp","ti\u1EBFn \u0111\u1ED9")
            || matchesWord(plain,MY_TRAINING_PLAIN))return "MY_TRAINING";
        if(contains(raw,"my package","sessions left","sessions do i have","remaining sessions","sessions remaining","my membership",
            "my card","g\u00F3i c\u1EE7a t\u00F4i","g\u00F3i t\u1EADp c\u1EE7a t\u00F4i","c\u00F2n bao nhi\u00EAu bu\u1ED5i","c\u00F2n m\u1EA5y bu\u1ED5i","s\u1ED1 bu\u1ED5i c\u00F2n l\u1EA1i","th\u1EBB c\u1EE7a t\u00F4i","th\u1EBB th\u00E0nh vi\u00EAn c\u1EE7a t\u00F4i")
            || matchesWord(plain,MY_PACKAGES_PLAIN))return "MY_PACKAGES";
        if(contains(raw,"my booking","my next session","my next class","my upcoming","i booked","my schedule",
            "l\u1ECBch c\u1EE7a t\u00F4i","bu\u1ED5i \u0111\u00E3 \u0111\u1EB7t","l\u1ECBch \u0111\u00E3 \u0111\u1EB7t","l\u1EDBp \u0111\u00E3 \u0111\u1EB7t","bu\u1ED5i t\u1EDBi c\u1EE7a t\u00F4i")
            || matchesWord(plain,MY_BOOKINGS_PLAIN))return "MY_BOOKINGS";
        if(contains(raw,"exercise","drill","training plan","workout plan","b\u00E0i t\u1EADp","gi\u00E1o \u00E1n","k\u1EBF ho\u1EA1ch t\u1EADp")
            || matchesWord(plain,COACH_PLAIN))return "COACH";
        if(contains(raw,"contact reception","talk to reception","talk to staff","speak to staff","human support","support request","complain",
            "li\u00EAn h\u1EC7","l\u1EC5 t\u00E2n","g\u1EB7p nh\u00E2n vi\u00EAn","khi\u1EBFu n\u1EA1i","h\u1ED7 tr\u1EE3")
            || matchesWord(plain,CONTACT_PLAIN))return "CONTACT";
        if(contains(raw,"package","price","cost","gi\u00E1","g\u00F3i","h\u1ECDc ph\u00ED","chi ph\u00ED","bao nhi\u00EAu ti\u1EC1n")
            || matchesWord(plain,PACKAGES_PLAIN))return "PACKAGES";
        if(contains(raw,"how to book","how do i book","booking rules","cancel","refund","payment",
            "c\u00E1ch \u0111\u1EB7t","\u0111\u1EB7t ch\u1ED7","\u0111\u1EB7t l\u1ECBch","h\u1EE7y","hu\u1EF7","ho\u00E0n ti\u1EC1n","thanh to\u00E1n")
            || matchesWord(plain,BOOKING_PLAIN))return "BOOKING";
        if(contains(raw,"class","session","schedule","available","l\u1ECBch","bu\u1ED5i","l\u1EDBp","c\u00F2n ch\u1ED7","khung gi\u1EDD")
            || matchesWord(plain,SESSIONS_PLAIN))return "SESSIONS";
        return "UNKNOWN";
    }

    /** TODAY, TOMORROW or UPCOMING (the next 14 days). */
    static String day(String raw,String plain) {
        if(contains(raw,"tomorrow","ng\u00E0y mai") || matchesWord(plain,Pattern.compile("\\bngay mai\\b")))return "TOMORROW";
        if(contains(raw,"today","h\u00F4m nay") || matchesWord(plain,Pattern.compile("\\bhom nay\\b")))return "TODAY";
        return "UPCOMING";
    }

    private static Pattern words(String... words) {return Pattern.compile("\\b("+String.join("|",words)+")\\b");}
    /** Whole-word privacy and instruction terms, so "contact" never matches "act as". */
    private static final Pattern PRIVATE_WORDS=words("act as","pretend","admin","administrator","password","passwords");
    private static final Pattern PRIVATE_PLAIN=words("mat khau","thanh vien khac","nguoi khac","so dien thoai","dia chi nha","bo qua huong dan",
        "dat giup","dat ho","huy giup","huy ho","hoan tien cho toi","thanh toan giup","thanh toan ho");
    private static final Pattern HEALTH_PLAIN=words("chan thuong","bi thuong","bac si","an kieng","giam can","chong mat","phau thuat",
        "mang thai","huyet ap","hen suyen","tieu duong","tri lieu","bong gan","tim mach");
    private static final Pattern MY_TRAINING_PLAIN=words("bai tap ve nha","nhan xet","phan hoi cua","ket qua tap","tien do");
    private static final Pattern MY_PACKAGES_PLAIN=words("goi cua toi","goi tap cua toi","con bao nhieu buoi","con may buoi","so buoi con lai","the cua toi");
    private static final Pattern MY_BOOKINGS_PLAIN=words("lich cua toi","buoi da dat","lich da dat","lop da dat");
    private static final Pattern COACH_PLAIN=words("bai tap","giao an","ke hoach tap");
    private static final Pattern CONTACT_PLAIN=words("lien he","le tan","gap nhan vien","khieu nai","ho tro");
    private static final Pattern PACKAGES_PLAIN=words("bang gia","goi tap","hoc phi","chi phi","bao nhieu tien");
    private static final Pattern BOOKING_PLAIN=words("cach dat","dat cho","dat lich","huy lich","hoan tien","thanh toan");
    private static final Pattern SESSIONS_PLAIN=words("lich tap","lich hoc","con cho","khung gio","buoi tap","lop hoc");
    private static boolean matchesWord(String text,Pattern pattern){return pattern.matcher(text).find();}

    static final String FAQ_VERSION="center-faq-v1";
    /** Keywords are matched on the original text and, for unaccented Vietnamese keywords, on the plain text. */
    record Faq(int id,String title,String answer,List<String> keywords,String viTitle,String viAnswer) {}
    /** Small curated general-exercise FAQ with fixed, conservative answers. Never personal, medical or generated text. */
    static final List<Faq> FAQS=List.of(
        new Faq(1,"Warming up","Spend about 5 to 10 minutes warming up: start with easy movement such as walking or light jogging, add gentle dynamic movements such as arm circles and leg swings, then a few slow movements from your sport. In class, follow your coach's warm-up.",
            List.of("warm up","warm-up","warmup","warming up","khoi dong"),
            "Kh\u1EDFi \u0111\u1ED9ng","D\u00E0nh kho\u1EA3ng 5 \u0111\u1EBFn 10 ph\u00FAt kh\u1EDFi \u0111\u1ED9ng: b\u1EAFt \u0111\u1EA7u b\u1EB1ng v\u1EADn \u0111\u1ED9ng nh\u1EB9 nh\u01B0 \u0111i b\u1ED9 ho\u1EB7c ch\u1EA1y ch\u1EADm, th\u00EAm c\u00E1c \u0111\u1ED9ng t\u00E1c nh\u1EB9 nh\u00E0ng nh\u01B0 xoay tay, \u0111\u00E1 ch\u00E2n, r\u1ED3i v\u00E0i \u0111\u1ED9ng t\u00E1c ch\u1EADm c\u1EE7a m\u00F4n b\u1EA1n t\u1EADp. Trong l\u1EDBp, h\u00E3y kh\u1EDFi \u0111\u1ED9ng theo h\u01B0\u1EDBng d\u1EABn c\u1EE7a Coach."),
        new Faq(2,"Cooling down and stretching","After practice, slow down gradually with about 5 minutes of easy movement, then do gentle stretches you already know. Hold each stretch comfortably without bouncing, and stop any stretch that feels uncomfortable. Your coach can show stretches for your sport.",
            List.of("cool down","cool-down","cooldown","cooling down","stretch","gian co","tha long"),
            "Th\u1EA3 l\u1ECFng v\u00E0 gi\u00E3n c\u01A1","Sau bu\u1ED5i t\u1EADp, gi\u1EA3m d\u1EA7n c\u01B0\u1EDDng \u0111\u1ED9 v\u1EDBi kho\u1EA3ng 5 ph\u00FAt v\u1EADn \u0111\u1ED9ng nh\u1EB9, r\u1ED3i gi\u00E3n c\u01A1 nh\u1EB9 nh\u00E0ng b\u1EB1ng nh\u1EEFng \u0111\u1ED9ng t\u00E1c b\u1EA1n \u0111\u00E3 bi\u1EBFt. Gi\u1EEF m\u1ED7i \u0111\u1ED9ng t\u00E1c \u1EDF m\u1EE9c tho\u1EA3i m\u00E1i, kh\u00F4ng nh\u00FAn, v\u00E0 d\u1EEBng ngay n\u1EBFu th\u1EA5y kh\u00F3 ch\u1ECBu. Coach c\u00F3 th\u1EC3 h\u01B0\u1EDBng d\u1EABn \u0111\u1ED9ng t\u00E1c gi\u00E3n c\u01A1 cho m\u00F4n c\u1EE7a b\u1EA1n."),
        new Faq(3,"Drinking water","Bring your own water bottle and drink small amounts regularly before practice, during breaks and afterwards. Take a break whenever you feel unwell and tell your coach or reception.",
            List.of("water","hydrat","drink","uong nuoc"),
            "U\u1ED1ng n\u01B0\u1EDBc","Mang theo b\u00ECnh n\u01B0\u1EDBc ri\u00EAng v\u00E0 u\u1ED1ng t\u1EEBng ng\u1EE5m nh\u1ECF \u0111\u1EC1u \u0111\u1EB7n tr\u01B0\u1EDBc bu\u1ED5i t\u1EADp, trong gi\u1EDD ngh\u1EC9 v\u00E0 sau khi t\u1EADp. H\u00E3y ngh\u1EC9 ngay khi th\u1EA5y kh\u00F4ng kh\u1ECFe v\u00E0 b\u00E1o cho Coach ho\u1EB7c l\u1EC5 t\u00E2n."),
        new Faq(4,"What to wear and bring","Wear comfortable sports clothing and clean indoor sports shoes suited to your sport, and bring a water bottle and a towel. Ask reception about equipment for a specific session.",
            List.of("wear","bring","shoes","clothes","clothing","mac gi","mang gi","quan ao","giay the thao"),
            "Trang ph\u1EE5c v\u00E0 \u0111\u1ED3 c\u1EA7n mang","M\u1EB7c \u0111\u1ED3 th\u1EC3 thao tho\u1EA3i m\u00E1i, \u0111i gi\u00E0y th\u1EC3 thao trong nh\u00E0 s\u1EA1ch, ph\u00F9 h\u1EE3p v\u1EDBi m\u00F4n t\u1EADp, mang theo b\u00ECnh n\u01B0\u1EDBc v\u00E0 kh\u0103n. H\u1ECFi l\u1EC5 t\u00E2n v\u1EC1 d\u1EE5ng c\u1EE5 cho t\u1EEBng bu\u1ED5i c\u1EE5 th\u1EC3."),
        new Faq(5,"How often to practise","Many people practise a few times a week and keep rest days between harder sessions. Increase time or intensity gradually. This is general guidance, not a personal plan; your coach can advise a schedule for you.",
            List.of("how often","rest day","rest days","rest between","times a week","times per week","may buoi mot tuan","bao nhieu lan mot tuan","nghi ngoi"),
            "T\u1EADp bao l\u00E2u m\u1ED9t l\u1EA7n","Nhi\u1EC1u ng\u01B0\u1EDDi t\u1EADp v\u00E0i l\u1EA7n m\u1ED7i tu\u1EA7n v\u00E0 c\u00F3 ng\u00E0y ngh\u1EC9 gi\u1EEFa c\u00E1c bu\u1ED5i t\u1EADp n\u1EB7ng. H\u00E3y t\u0103ng th\u1EDDi gian ho\u1EB7c c\u01B0\u1EDDng \u0111\u1ED9 t\u1EEB t\u1EEB. \u0110\u00E2y l\u00E0 h\u01B0\u1EDBng d\u1EABn chung, kh\u00F4ng ph\u1EA3i k\u1EBF ho\u1EA1ch c\u00E1 nh\u00E2n; Coach c\u00F3 th\u1EC3 t\u01B0 v\u1EA5n l\u1ECBch t\u1EADp cho ri\u00EAng b\u1EA1n."),
        new Faq(6,"Your first class","For your first class, arrive about 10 to 15 minutes early, check in at reception, and tell the coach it is your first session. Open My bookings to confirm the date and time.",
            List.of("first class","first session","first time","first lesson","buoi dau tien","lan dau"),
            "Bu\u1ED5i h\u1ECDc \u0111\u1EA7u ti\u00EAn","V\u1EDBi bu\u1ED5i h\u1ECDc \u0111\u1EA7u ti\u00EAn, h\u00E3y \u0111\u1EBFn s\u1EDBm kho\u1EA3ng 10 \u0111\u1EBFn 15 ph\u00FAt, check-in t\u1EA1i qu\u1EA7y l\u1EC5 t\u00E2n v\u00E0 b\u00E1o Coach \u0111\u00E2y l\u00E0 bu\u1ED5i \u0111\u1EA7u c\u1EE7a b\u1EA1n. M\u1EDF My bookings \u0111\u1EC3 x\u00E1c nh\u1EADn ng\u00E0y gi\u1EDD."),
        new Faq(7,"Building good technique","Practise controlled technique at a comfortable pace before adding speed, distance or force, and pause to reset between repetitions. Ask your coach to check your technique during class.",
            List.of("technique","good form","proper form","ky thuat"),
            "T\u1EADp \u0111\u00FAng k\u1EF9 thu\u1EADt","T\u1EADp k\u1EF9 thu\u1EADt c\u00F3 ki\u1EC3m so\u00E1t \u1EDF t\u1ED1c \u0111\u1ED9 tho\u1EA3i m\u00E1i tr\u01B0\u1EDBc khi t\u0103ng t\u1ED1c \u0111\u1ED9, kho\u1EA3ng c\u00E1ch hay l\u1EF1c, v\u00E0 ngh\u1EC9 \u0111\u1EC3 ch\u1EC9nh l\u1EA1i t\u01B0 th\u1EBF gi\u1EEFa c\u00E1c l\u1EA7n l\u1EB7p. Nh\u1EDD Coach ki\u1EC3m tra k\u1EF9 thu\u1EADt c\u1EE7a b\u1EA1n trong gi\u1EDD h\u1ECDc."));
    static Faq faq(String text) { return faq(text,plain(text)); }
    static Faq faq(String raw,String plain) {
        for(var item:FAQS)for(var keyword:item.keywords())if(raw.contains(keyword) || plain.contains(keyword))return item;
        return null;
    }
    private static boolean contains(String text,String... words) {
        for(String word:words)if(text.contains(word))return true;
        return false;
    }

    /** Vietnamese sport names (unaccented) for the seeded sport codes. */
    private static final Map<String,List<String>> SPORT_SYNONYMS=Map.of(
        "FOOTBALL",List.of("bong da","da bong"),"BADMINTON",List.of("cau long"),"BASKETBALL",List.of("bong ro"),
        "VOLLEYBALL",List.of("bong chuyen"),"SWIMMING",List.of("boi loi","ho boi"),"TENNIS",List.of("quan vot"),
        "TABLE_TENNIS",List.of("bong ban"),"YOGA",List.of("yoga"));
    /** The sport named by the AI classifier, or the first active sport whose name or Vietnamese name is in the question. */
    private Long sport(String raw,String plain,String aiSport) {
        var rows=jdbc.query("SELECT id,code,name FROM sport WHERE is_active=1 ORDER BY id",
            (rs,row)->new Object[]{rs.getLong("id"),rs.getString("code"),rs.getString("name").toLowerCase(Locale.ROOT)});
        if(aiSport!=null)for(var row:rows)if(row[2].equals(aiSport.toLowerCase(Locale.ROOT)))return (Long)row[0];
        for(var row:rows) {
            if(raw.contains((String)row[2]))return (Long)row[0];
            for(String synonym:SPORT_SYNONYMS.getOrDefault((String)row[1],List.of()))
                if(matchesWord(plain,words(synonym)))return (Long)row[0];
        }
        return null;
    }

    private Consultation packages(boolean vi,String mode,Long sportId) {
        String sql="SELECT TOP(5) p.id,p.name,p.price_amount,p.session_count,p.duration_days,sp.name sport_name FROM sport_package p JOIN sport sp ON sp.id=p.sport_id WHERE p.is_active=1 AND sp.is_active=1";
        var values=new ArrayList<Object>();
        if(sportId!=null){sql+=" AND p.sport_id=?";values.add(sportId);}
        sql+=" ORDER BY p.price_amount,p.id";
        var sources=jdbc.query(sql,(rs,row)->new ConsultationSource("SPORT_PACKAGE",rs.getLong("id"),rs.getString("name"),
            vi?rs.getString("sport_name")+": gi\u00E1 g\u1ED1c "+price(true,rs.getBigDecimal("price_amount"))+" VND, "+rs.getInt("session_count")+" bu\u1ED5i, hi\u1EC7u l\u1EF1c "+rs.getInt("duration_days")+" ng\u00E0y. Gi\u1EA3m gi\u00E1 v\u00E0 thanh to\u00E1n \u0111\u01B0\u1EE3c ki\u1EC3m tra khi mua g\u00F3i."
              :rs.getString("sport_name")+": "+rs.getBigDecimal("price_amount").toPlainString()+" VND base price, "+rs.getInt("session_count")+" sessions, valid "+rs.getInt("duration_days")+" days. Discounts and payment status are checked in the purchase flow.",
            "/member/packages/"+rs.getLong("id")),values.toArray());
        return response(vi,mode,sources.isEmpty()?"NO_ANSWER":"ANSWERED",sources.isEmpty()
            ?t(vi,"No matching active packages are available in the current catalog. Reception can confirm future offers.",
                "Hi\u1EC7n kh\u00F4ng c\u00F3 g\u00F3i t\u1EADp ph\u00F9 h\u1EE3p \u0111ang m\u1EDF b\u00E1n. L\u1EC5 t\u00E2n c\u00F3 th\u1EC3 cho b\u1EA1n bi\u1EBFt c\u00E1c \u01B0u \u0111\u00E3i s\u1EAFp t\u1EDBi.")
            :t(vi,"Current active package base prices from the center catalog (up to 5, lowest price first). These are catalog prices, not a payment confirmation.",
                "Gi\u00E1 g\u1ED1c c\u00E1c g\u00F3i t\u1EADp \u0111ang m\u1EDF b\u00E1n c\u1EE7a trung t\u00E2m (t\u1ED1i \u0111a 5 g\u00F3i, gi\u00E1 th\u1EA5p nh\u1EA5t tr\u01B0\u1EDBc). \u0110\u00E2y l\u00E0 gi\u00E1 ni\u00EAm y\u1EBFt, ch\u01B0a ph\u1EA3i x\u00E1c nh\u1EADn thanh to\u00E1n."),sources);
    }
    private Consultation sessions(boolean vi,String mode,Long sportId,String day) {
        LocalDate today=LocalDate.now(clock);
        LocalDate from=day.equals("TOMORROW")?today.plusDays(1):today;
        LocalDate to=day.equals("UPCOMING")?today.plusDays(14):from;
        // Same rule as Flow 2 booking: a coach-led session is listed while its Coach is active and qualified for the
        // sport, even after the class default coach changes (substitute coach).
        String sql="""
            SELECT s.id,c.name,sp.name sport_name,s.session_date,s.start_time,s.end_time,s.capacity-s.booked_count free_seats
            FROM class_session s JOIN sport sp ON sp.id=s.sport_id LEFT JOIN sport_class c ON c.id=s.class_id
            LEFT JOIN user_account cu ON cu.id=s.coach_id
            WHERE s.status='PUBLISHED' AND sp.is_active=1 AND (c.id IS NULL OR c.is_active=1)
                AND (s.training_type<>'COACH_LED' OR (cu.status='ACTIVE' AND EXISTS(SELECT 1 FROM coach_sport cs WHERE cs.coach_id=s.coach_id AND cs.sport_id=s.sport_id)))
                AND s.booked_count<s.capacity AND s.session_date BETWEEN ? AND ?
                AND (s.session_date>? OR s.start_time>CAST(? AS time))
            """;
        var values=new ArrayList<Object>(List.of(Date.valueOf(from),Date.valueOf(to),Date.valueOf(today),Time.valueOf(LocalTime.now(clock))));
        if(sportId!=null){sql+=" AND s.sport_id=?";values.add(sportId);}
        sql+=" ORDER BY s.session_date,s.start_time,s.id";
        var sources=jdbc.query(sql,(rs,row)->new ConsultationSource("CLASS_SESSION",rs.getLong("id"),
            rs.getString("name")==null?rs.getString("sport_name")+t(vi," practice"," - t\u1EF1 t\u1EADp"):rs.getString("name"),
            vi?rs.getString("sport_name")+": "+date(true,rs.getDate("session_date"))+" "+time(true,rs.getTime("start_time"))+"-"+time(true,rs.getTime("end_time"))+" (gi\u1EDD Vi\u1EC7t Nam), c\u00F2n "+rs.getInt("free_seats")+" ch\u1ED7. Ch\u01B0a ki\u1EC3m tra g\u00F3i t\u1EADp c\u1EE7a b\u1EA1n."
              :rs.getString("sport_name")+": "+rs.getDate("session_date")+" "+rs.getTime("start_time")+"-"+rs.getTime("end_time")+" (Vietnam time), "+rs.getInt("free_seats")+" places remaining. Package eligibility has not been checked.",
            "/member/sessions/"+rs.getLong("id")),values.toArray());
        var visibleIds=visibility.filter(sources.stream().map(ConsultationSource::id).toList());
        var visible=sources.stream().filter(source->visibleIds.contains(source.id())).limit(5).toList();
        String range=vi?"t\u1EEB "+from.format(VI_DATE)+" \u0111\u1EBFn "+to.format(VI_DATE):"for "+from+" through "+to;
        return response(vi,mode,visible.isEmpty()?"NO_ANSWER":"ANSWERED",visible.isEmpty()
            ?t(vi,"No matching published sessions with available places were found "+range+". Reception can confirm other dates.",
                "Kh\u00F4ng t\u00ECm th\u1EA5y bu\u1ED5i h\u1ECDc \u0111\u00E3 c\u00F4ng b\u1ED1 c\u00F2n ch\u1ED7 "+range+". L\u1EC5 t\u00E2n c\u00F3 th\u1EC3 ki\u1EC3m tra gi\u00FAp c\u00E1c ng\u00E0y kh\u00E1c.")
            :t(vi,"Published sessions with available places "+range+" (up to 5, earliest first). Open the source to check current eligibility.",
                "C\u00E1c bu\u1ED5i h\u1ECDc \u0111\u00E3 c\u00F4ng b\u1ED1 c\u00F2n ch\u1ED7 "+range+" (t\u1ED1i \u0111a 5 bu\u1ED5i, s\u1EDBm nh\u1EA5t tr\u01B0\u1EDBc). M\u1EDF t\u1EEBng bu\u1ED5i \u0111\u1EC3 ki\u1EC3m tra \u0111i\u1EC1u ki\u1EC7n \u0111\u1EB7t."),visible);
    }

    /** F6-04: the Member's own active packages, membership card and number of upcoming bookings. */
    private Consultation myPackages(boolean vi,String mode,long memberId) {
        LocalDate today=LocalDate.now(clock);
        var sources=new ArrayList<ConsultationSource>(jdbc.query("""
            SELECT TOP (5) r.id,p.name,sp.name sport_name,r.remaining_sessions,r.total_sessions,r.end_date
            FROM sport_package_registration r JOIN sport_package p ON p.id=r.package_id JOIN sport sp ON sp.id=p.sport_id
            WHERE r.member_id=? AND r.status='ACTIVE' AND r.end_date>=? ORDER BY r.end_date,r.id
            """,(rs,row)->new ConsultationSource("MY_PACKAGE",rs.getLong("id"),rs.getString("name"),
                vi?rs.getString("sport_name")+": c\u00F2n "+rs.getInt("remaining_sessions")+"/"+rs.getInt("total_sessions")+" bu\u1ED5i, hi\u1EC7u l\u1EF1c \u0111\u1EBFn "+date(true,rs.getDate("end_date"))+"."
                  :rs.getString("sport_name")+": "+rs.getInt("remaining_sessions")+" of "+rs.getInt("total_sessions")+" sessions available, valid until "+rs.getDate("end_date")+".",
                "/member/membership"),memberId,today));
        sources.addAll(jdbc.query("""
            SELECT TOP (1) c.id,t.name,t.discount_percentage,c.end_date FROM member_card c JOIN membership_card_tier t ON t.id=c.tier_id
            WHERE c.member_id=? AND c.status='ACTIVE' AND c.start_date<=? AND (c.end_date IS NULL OR c.end_date>=?)
            ORDER BY t.discount_percentage DESC,c.end_date DESC
            """,(rs,row)->new ConsultationSource("MY_CARD",rs.getLong("id"),rs.getString("name"),
                vi?"Gi\u1EA3m "+rs.getInt("discount_percentage")+"% khi mua g\u00F3i t\u1EADp"+(rs.getDate("end_date")==null?", kh\u00F4ng th\u1EDDi h\u1EA1n.":", hi\u1EC7u l\u1EF1c \u0111\u1EBFn "+date(true,rs.getDate("end_date"))+".")
                  :rs.getInt("discount_percentage")+"% package discount"+(rs.getDate("end_date")==null?", no end date.":", valid until "+rs.getDate("end_date")+"."),
                "/member/membership/cards"),memberId,today,today));
        Integer upcoming=jdbc.queryForObject("""
            SELECT COUNT(*) FROM booking b JOIN class_session s ON s.id=b.session_id
            WHERE b.member_id=? AND b.status='CONFIRMED' AND s.status<>'CANCELLED'
                AND (s.session_date>? OR (s.session_date=? AND s.start_time>CAST(? AS time)))
            """,Integer.class,memberId,Date.valueOf(today),Date.valueOf(today),Time.valueOf(LocalTime.now(clock)));
        int count=upcoming==null?0:upcoming;
        if(sources.isEmpty())return response(vi,mode,"ANSWERED",t(vi,"You have no active sport package or membership card. Browse the package catalog to buy one.",
                "B\u1EA1n ch\u01B0a c\u00F3 g\u00F3i t\u1EADp hay th\u1EBB th\u00E0nh vi\u00EAn n\u00E0o \u0111ang ho\u1EA1t \u0111\u1ED9ng. H\u00E3y xem danh m\u1EE5c g\u00F3i t\u1EADp \u0111\u1EC3 mua."),
            List.of(new ConsultationSource("PRODUCT_GUIDANCE",0,t(vi,"Sport packages","G\u00F3i t\u1EADp"),t(vi,"Current packages and prices.","C\u00E1c g\u00F3i t\u1EADp v\u00E0 gi\u00E1 hi\u1EC7n t\u1EA1i."),"/member/packages")));
        return response(vi,mode,"ANSWERED",t(vi,"Your active packages and card from your own records. You also have "+count
            +" upcoming confirmed booking(s); a session is used from your package when your attendance is recorded.",
            "G\u00F3i t\u1EADp v\u00E0 th\u1EBB th\u00E0nh vi\u00EAn \u0111ang ho\u1EA1t \u0111\u1ED9ng c\u1EE7a b\u1EA1n, l\u1EA5y t\u1EEB d\u1EEF li\u1EC7u c\u1EE7a ch\u00EDnh b\u1EA1n. B\u1EA1n c\u00F3 "+count
            +" bu\u1ED5i \u0111\u00E3 \u0111\u1EB7t s\u1EAFp t\u1EDBi; m\u1ED7i bu\u1ED5i ch\u1EC9 b\u1ECB tr\u1EEB v\u00E0o g\u00F3i khi b\u1EA1n \u0111\u01B0\u1EE3c \u0111i\u1EC3m danh."),sources);
    }

    /** F6-04: the Member's own next confirmed bookings. */
    private Consultation myBookings(boolean vi,String mode,long memberId) {
        LocalDate today=LocalDate.now(clock);
        var sources=jdbc.query("""
            SELECT TOP (5) b.id,s.session_code,COALESCE(c.name,sp.name) class_name,s.session_date,s.start_time,s.end_time
            FROM booking b JOIN class_session s ON s.id=b.session_id JOIN sport sp ON sp.id=s.sport_id LEFT JOIN sport_class c ON c.id=s.class_id
            WHERE b.member_id=? AND b.status='CONFIRMED' AND s.status<>'CANCELLED'
                AND (s.session_date>? OR (s.session_date=? AND s.start_time>CAST(? AS time)))
            ORDER BY s.session_date,s.start_time,b.id
            """,(rs,row)->new ConsultationSource("MY_BOOKING",rs.getLong("id"),rs.getString("class_name"),
                vi?date(true,rs.getDate("session_date"))+" "+time(true,rs.getTime("start_time"))+"-"+time(true,rs.getTime("end_time"))+" (gi\u1EDD Vi\u1EC7t Nam), m\u00E3 bu\u1ED5i "+rs.getString("session_code")+"."
                  :rs.getDate("session_date")+" "+rs.getTime("start_time")+"-"+rs.getTime("end_time")+" (Vietnam time), session "+rs.getString("session_code")+".",
                "/member/bookings"),memberId,Date.valueOf(today),Date.valueOf(today),Time.valueOf(LocalTime.now(clock)));
        return response(vi,mode,"ANSWERED",sources.isEmpty()
                ?t(vi,"You have no upcoming confirmed bookings. Find a class to book one.","B\u1EA1n ch\u01B0a c\u00F3 bu\u1ED5i n\u00E0o \u0111\u00E3 \u0111\u1EB7t s\u1EAFp t\u1EDBi. H\u00E3y t\u00ECm l\u1EDBp \u0111\u1EC3 \u0111\u1EB7t.")
                :t(vi,"Your next confirmed bookings from your own records (up to 5, earliest first).","C\u00E1c bu\u1ED5i b\u1EA1n \u0111\u00E3 \u0111\u1EB7t s\u1EAFp t\u1EDBi, l\u1EA5y t\u1EEB d\u1EEF li\u1EC7u c\u1EE7a ch\u00EDnh b\u1EA1n (t\u1ED1i \u0111a 5 bu\u1ED5i, s\u1EDBm nh\u1EA5t tr\u01B0\u1EDBc)."),
            sources.isEmpty()?List.of(new ConsultationSource("PRODUCT_GUIDANCE",0,t(vi,"Find a class","T\u00ECm l\u1EDBp h\u1ECDc"),
                t(vi,"Published classes with available places.","C\u00E1c l\u1EDBp \u0111\u00E3 c\u00F4ng b\u1ED1 c\u00F2n ch\u1ED7."),"/member/classes")):sources);
    }

    /** F6-04: the Member's latest coach feedback and open homework, shown as recorded by the Coach. */
    private Consultation myTraining(boolean vi,String mode,long memberId) {
        LocalDate today=LocalDate.now(clock);
        var sources=new ArrayList<ConsultationSource>(jdbc.query("""
            SELECT TOP (1) r.id,r.comments,r.created_at,u.full_name coach_name
            FROM coach_feedback r JOIN session_plan p ON p.id=r.plan_id AND p.member_id=r.member_id JOIN user_account u ON u.id=r.coach_id
            WHERE r.member_id=? AND p.is_template=0 ORDER BY r.created_at DESC,r.id DESC
            """,(rs,row)->new ConsultationSource("COACH_FEEDBACK",rs.getLong("id"),
                vi?"Nh\u1EADn x\u00E9t g\u1EA7n nh\u1EA5t c\u1EE7a Coach ("+rs.getTimestamp("created_at").toLocalDateTime().toLocalDate().format(VI_DATE)+")"
                  :"Latest coach feedback ("+rs.getTimestamp("created_at").toLocalDateTime().toLocalDate()+")",
                rs.getString("coach_name")+": "+rs.getString("comments"),"/member/training/feedback"),memberId));
        sources.addAll(jdbc.query("""
            SELECT TOP (3) n.id,n.class_id,n.title,n.content,n.due_date,c.name class_name FROM class_training_notice n
            JOIN sport_class c ON c.id=n.class_id AND c.coach_id=n.coach_id AND c.is_active=1
            JOIN user_account u ON u.id=n.coach_id AND u.status='ACTIVE'
            WHERE n.status='PUBLISHED' AND n.notice_type='HOMEWORK' AND n.due_date>=? AND EXISTS(
                SELECT 1 FROM booking b JOIN class_session s ON s.id=b.session_id
                WHERE b.member_id=? AND b.status='CONFIRMED' AND s.status<>'CANCELLED'
                    AND s.class_id=n.class_id AND s.coach_id=n.coach_id AND s.training_type='COACH_LED')
            ORDER BY n.due_date,n.id
            """,(rs,row)->new ConsultationSource("HOMEWORK",rs.getLong("id"),t(vi,"Homework: ","B\u00E0i t\u1EADp v\u1EC1 nh\u00E0: ")+rs.getString("title"),
                vi?rs.getString("class_name")+", h\u1EA1n "+date(true,rs.getDate("due_date"))+". "+rs.getString("content")
                  :rs.getString("class_name")+", due "+rs.getDate("due_date")+". "+rs.getString("content"),
                "/member/training/classes/"+rs.getLong("class_id")),Date.valueOf(today),memberId));
        return response(vi,mode,"ANSWERED",sources.isEmpty()
                ?t(vi,"No coach feedback or open homework is recorded for you yet.","Ch\u01B0a c\u00F3 nh\u1EADn x\u00E9t hay b\u00E0i t\u1EADp v\u1EC1 nh\u00E0 n\u00E0o c\u1EE7a Coach d\u00E0nh cho b\u1EA1n.")
                :t(vi,"Your latest coach feedback and open homework, shown as your coach recorded them. Ask your coach if you need clarification.",
                    "Nh\u1EADn x\u00E9t g\u1EA7n nh\u1EA5t v\u00E0 b\u00E0i t\u1EADp v\u1EC1 nh\u00E0 c\u00F2n h\u1EA1n c\u1EE7a b\u1EA1n, gi\u1EEF nguy\u00EAn v\u0103n nh\u01B0 Coach \u0111\u00E3 ghi. H\u00E3y h\u1ECFi Coach n\u1EBFu c\u1EA7n gi\u1EA3i th\u00EDch th\u00EAm."),
            sources.isEmpty()?List.of(askCoach(vi)):sources);
    }

    private static ConsultationSource askCoach(boolean vi) {
        return new ConsultationSource("HUMAN_COACH",0,t(vi,"Ask your coach","H\u1ECFi Coach c\u1EE7a b\u1EA1n"),
            t(vi,"Private human conversation; no automated coach reply.","Trao \u0111\u1ED5i ri\u00EAng v\u1EDBi Coach; kh\u00F4ng c\u00F3 tr\u1EA3 l\u1EDDi t\u1EF1 \u0111\u1ED9ng."),"/member/training/questions");
    }
    private static Consultation faqAnswer(boolean vi,String mode,Faq faq) {
        return response(vi,mode,"ANSWERED",vi?"C\u00E2u h\u1ECFi chung v\u1EC1 t\u1EADp luy\u1EC7n (h\u01B0\u1EDBng d\u1EABn c\u1ED1 \u0111\u1ECBnh c\u1EE7a trung t\u00E2m, kh\u00F4ng ph\u1EA3i t\u01B0 v\u1EA5n c\u00E1 nh\u00E2n hay y t\u1EBF): "+faq.viAnswer()
                :"General exercise FAQ (curated center guidance, not personal or medical advice): "+faq.answer(),
            List.of(new ConsultationSource("CURATED_FAQ",faq.id(),vi?"FAQ trung t\u00E2m: "+faq.viTitle():"Center FAQ: "+faq.title(),
                vi?"C\u00E2u tr\u1EA3 l\u1EDDi c\u1ED1 \u0111\u1ECBnh t\u1EEB FAQ c\u1EE7a trung t\u00E2m ("+FAQ_VERSION+"). H\u00E3y h\u1ECFi Coach \u0111\u1EC3 \u0111\u01B0\u1EE3c t\u01B0 v\u1EA5n cho ri\u00EAng b\u1EA1n."
                  :"Fixed answer from the curated center FAQ ("+FAQ_VERSION+"). Ask your coach for advice about your own training.","/member/training/questions")));
    }
    private static Consultation coach(boolean vi,String mode) {
        return response(vi,mode,"NO_ANSWER",t(vi,"I cannot assess personal training or health concerns. Ask your assigned human coach about your practice plan.",
                "Tr\u1EE3 l\u00FD kh\u00F4ng \u0111\u00E1nh gi\u00E1 \u0111\u01B0\u1EE3c v\u1EA5n \u0111\u1EC1 t\u1EADp luy\u1EC7n c\u00E1 nh\u00E2n hay s\u1EE9c kh\u1ECFe. H\u00E3y h\u1ECFi Coach ph\u1EE5 tr\u00E1ch c\u1EE7a b\u1EA1n v\u1EC1 k\u1EBF ho\u1EA1ch t\u1EADp."),
            List.of(askCoach(vi)));
    }
    private static Consultation unknown(boolean vi,String mode) {
        return response(vi,mode,"NO_ANSWER",t(vi,"I do not have a grounded answer to that question. I can show published sessions, active package prices, your own packages, bookings and coach feedback, explain how to check booking eligibility, or answer a few general exercise questions such as warming up. Contact reception for other center information.",
            "T\u00F4i kh\u00F4ng c\u00F3 c\u00E2u tr\u1EA3 l\u1EDDi d\u1EF1a tr\u00EAn d\u1EEF li\u1EC7u c\u1EE7a trung t\u00E2m cho c\u00E2u h\u1ECFi n\u00E0y. T\u00F4i c\u00F3 th\u1EC3 cho b\u1EA1n xem c\u00E1c bu\u1ED5i h\u1ECDc \u0111\u00E3 c\u00F4ng b\u1ED1, gi\u00E1 g\u00F3i t\u1EADp, g\u00F3i t\u1EADp, l\u1ECBch \u0111\u00E3 \u0111\u1EB7t v\u00E0 nh\u1EADn x\u00E9t c\u1EE7a Coach d\u00E0nh cho b\u1EA1n, h\u01B0\u1EDBng d\u1EABn c\u00E1ch ki\u1EC3m tra \u0111i\u1EC1u ki\u1EC7n \u0111\u1EB7t ch\u1ED7, ho\u1EB7c tr\u1EA3 l\u1EDDi v\u00E0i c\u00E2u h\u1ECFi chung nh\u01B0 c\u00E1ch kh\u1EDFi \u0111\u1ED9ng. H\u00E3y li\u00EAn h\u1EC7 l\u1EC5 t\u00E2n \u0111\u1EC3 bi\u1EBFt th\u00F4ng tin kh\u00E1c."),List.of());
    }
    private static Consultation signIn(boolean vi,String mode) {
        return response(vi,mode,"NO_ANSWER",t(vi,"Sign in as a Member to ask about your own packages, bookings or training.",
            "H\u00E3y \u0111\u0103ng nh\u1EADp b\u1EB1ng t\u00E0i kho\u1EA3n Member \u0111\u1EC3 h\u1ECFi v\u1EC1 g\u00F3i t\u1EADp, l\u1ECBch \u0111\u00E3 \u0111\u1EB7t hay vi\u1EC7c t\u1EADp luy\u1EC7n c\u1EE7a b\u1EA1n."),List.of());
    }
    private static Consultation response(boolean vi,String mode,String status,String answer,List<ConsultationSource> sources) {
        return new Consultation(mode,status,answer,false,sources,vi?"vi":"en");
    }
}
