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
 *   <li>Optional AI classification ({@link AssistantIntentClassifier}) of the redacted question; any failure or an
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
            "Tư vấn tự động của trung tâm đang tắt."),List.of());
        if(!optIn)return response(early,RULES_MODE,"CONSENT_REQUIRED",t(early,"Enable automated consultation before asking a question. No conversation history is stored.",
            "Hãy bật tư vấn tự động trước khi đặt câu hỏi. Hệ thống không lưu lịch sử trò chuyện."),List.of());
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
        if(classifier!=null && aiAllowed(memberId)) {
            try {
                var sports=jdbc.queryForList("SELECT name FROM sport WHERE is_active=1 ORDER BY id",String.class);
                var result=classifier.classify(AssistantAiPolicy.redact(question),sports);
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
                    "Mở một buổi học, kiểm tra số chỗ còn trống và gói tập phù hợp, rồi xem lại và xác nhận trong bước đặt chỗ. Vào My bookings để xem buổi nào còn được hủy. Trợ lý này không bao giờ tự đặt chỗ, hủy, thanh toán hay hoàn tiền."),
                List.of(new ConsultationSource("PRODUCT_GUIDANCE",0,t(vi,"Session and booking workflow","Quy trình đặt buổi học"),
                    t(vi,"Eligibility and credits are checked by the existing booking service.","Điều kiện đặt và số buổi trong gói được kiểm tra khi đặt chỗ."),"/member/classes")));
            case "CONTACT" -> response(vi,mode,"ANSWERED",t(vi,"Reception can help with questions the assistant cannot answer, such as account, payment or discount checks. Send a support request and reception will reply to you in My support requests.",
                    "Lễ tân có thể giúp những câu trợ lý không trả lời được, như tài khoản, thanh toán hay kiểm tra giảm giá. Hãy gửi yêu cầu hỗ trợ, lễ tân sẽ trả lời bạn trong mục My support requests."),
                List.of(new ConsultationSource("SUPPORT",0,t(vi,"Contact reception","Liên hệ lễ tân"),
                    t(vi,"Opens the support request form. Nothing is sent until you submit it.","Mở form yêu cầu hỗ trợ. Chưa có gì được gửi cho đến khi bạn bấm gửi."),"/member/support")));
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

    /** Lower-cased text without Vietnamese diacritics, so "bóng đá" and "bong da" match the same keyword. */
    static String plain(String text) {
        String stripped=Normalizer.normalize(text.replace('đ','d').replace('Đ','d'),Normalizer.Form.NFD).replaceAll("\\p{M}+","");
        return stripped.toLowerCase(Locale.ROOT);
    }

    /**
     * Returns UNKNOWN for private, injection and transaction requests, COACH for health questions, or null.
     * Accented keywords are matched on the original text; unaccented Vietnamese keywords on whole words of the plain
     * text, so that short words that differ only in accents ("đau" pain, "đầu" head) are not confused.
     */
    static String guard(String raw,String plain) {
        if(contains(raw,"password","secret","token","sql","ignore previous","other member","book for me","cancel for me","refund me",
            "ignore all","ignore your","ignore the","disregard","system prompt","jailbreak","developer mode","another member","member's",
            "drop table","delete from","insert into","select *","phone number","home address","email address",
            "mật khẩu","thành viên khác","người khác","số điện thoại","địa chỉ nhà","bỏ qua hướng dẫn","bỏ qua mọi","đặt giúp","đặt hộ",
            "hủy giúp","huỷ giúp","hủy hộ","huỷ hộ","hoàn tiền cho tôi","thanh toán giúp","thanh toán hộ")
            || matchesWord(raw,PRIVATE_WORDS) || matchesWord(plain,PRIVATE_PLAIN))return "UNKNOWN";
        // Personal health and injury questions always go to a human, even when they mention an FAQ topic.
        if(contains(raw,"injury","injured","pain","hurt","sore","medical","doctor","sick","illness","pregnan","heart","blood pressure",
            "asthma","diabetes","medication","medicine","diet","weight loss","lose weight","rehab","sprain","dizzy","surgery","physio",
            "đau","chấn thương","bị thương","bệnh","bác sĩ","thuốc","ăn kiêng","giảm cân","chóng mặt","phẫu thuật","mang thai","có bầu",
            "tim mạch","huyết áp","hen suyễn","tiểu đường","trị liệu","bong gân","ốm")
            || matchesWord(plain,HEALTH_PLAIN))return "COACH";
        return null;
    }

    /** Rule-based intent after the guard. */
    static String rules(String raw,String plain) {
        if(faq(raw,plain)!=null)return "FAQ";
        if(contains(raw,"homework","coach feedback","my feedback","my training","my progress","my results","my result",
            "bài tập về nhà","nhận xét","phản hồi của","kết quả tập","tiến độ")
            || matchesWord(plain,MY_TRAINING_PLAIN))return "MY_TRAINING";
        if(contains(raw,"my package","sessions left","sessions do i have","remaining sessions","sessions remaining","my membership",
            "my card","gói của tôi","gói tập của tôi","còn bao nhiêu buổi","còn mấy buổi","số buổi còn lại","thẻ của tôi","thẻ thành viên của tôi")
            || matchesWord(plain,MY_PACKAGES_PLAIN))return "MY_PACKAGES";
        if(contains(raw,"my booking","my next session","my next class","my upcoming","i booked","my schedule",
            "lịch của tôi","buổi đã đặt","lịch đã đặt","lớp đã đặt","buổi tới của tôi")
            || matchesWord(plain,MY_BOOKINGS_PLAIN))return "MY_BOOKINGS";
        if(contains(raw,"exercise","drill","training plan","workout plan","bài tập","giáo án","kế hoạch tập")
            || matchesWord(plain,COACH_PLAIN))return "COACH";
        if(contains(raw,"contact reception","talk to reception","talk to staff","speak to staff","human support","support request","complain",
            "liên hệ","lễ tân","gặp nhân viên","khiếu nại","hỗ trợ")
            || matchesWord(plain,CONTACT_PLAIN))return "CONTACT";
        if(contains(raw,"package","price","cost","giá","gói","học phí","chi phí","bao nhiêu tiền")
            || matchesWord(plain,PACKAGES_PLAIN))return "PACKAGES";
        if(contains(raw,"how to book","how do i book","booking rules","cancel","refund","payment",
            "cách đặt","đặt chỗ","đặt lịch","hủy","huỷ","hoàn tiền","thanh toán")
            || matchesWord(plain,BOOKING_PLAIN))return "BOOKING";
        if(contains(raw,"class","session","schedule","available","lịch","buổi","lớp","còn chỗ","khung giờ")
            || matchesWord(plain,SESSIONS_PLAIN))return "SESSIONS";
        return "UNKNOWN";
    }

    /** TODAY, TOMORROW or UPCOMING (the next 14 days). */
    static String day(String raw,String plain) {
        if(contains(raw,"tomorrow","ngày mai") || matchesWord(plain,Pattern.compile("\\bngay mai\\b")))return "TOMORROW";
        if(contains(raw,"today","hôm nay") || matchesWord(plain,Pattern.compile("\\bhom nay\\b")))return "TODAY";
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
            "Khởi động","Dành khoảng 5 đến 10 phút khởi động: bắt đầu bằng vận động nhẹ như đi bộ hoặc chạy chậm, thêm các động tác nhẹ nhàng như xoay tay, đá chân, rồi vài động tác chậm của môn bạn tập. Trong lớp, hãy khởi động theo hướng dẫn của Coach."),
        new Faq(2,"Cooling down and stretching","After practice, slow down gradually with about 5 minutes of easy movement, then do gentle stretches you already know. Hold each stretch comfortably without bouncing, and stop any stretch that feels uncomfortable. Your coach can show stretches for your sport.",
            List.of("cool down","cool-down","cooldown","cooling down","stretch","gian co","tha long"),
            "Thả lỏng và giãn cơ","Sau buổi tập, giảm dần cường độ với khoảng 5 phút vận động nhẹ, rồi giãn cơ nhẹ nhàng bằng những động tác bạn đã biết. Giữ mỗi động tác ở mức thoải mái, không nhún, và dừng ngay nếu thấy khó chịu. Coach có thể hướng dẫn động tác giãn cơ cho môn của bạn."),
        new Faq(3,"Drinking water","Bring your own water bottle and drink small amounts regularly before practice, during breaks and afterwards. Take a break whenever you feel unwell and tell your coach or reception.",
            List.of("water","hydrat","drink","uong nuoc"),
            "Uống nước","Mang theo bình nước riêng và uống từng ngụm nhỏ đều đặn trước buổi tập, trong giờ nghỉ và sau khi tập. Hãy nghỉ ngay khi thấy không khỏe và báo cho Coach hoặc lễ tân."),
        new Faq(4,"What to wear and bring","Wear comfortable sports clothing and clean indoor sports shoes suited to your sport, and bring a water bottle and a towel. Ask reception about equipment for a specific session.",
            List.of("wear","bring","shoes","clothes","clothing","mac gi","mang gi","quan ao","giay the thao"),
            "Trang phục và đồ cần mang","Mặc đồ thể thao thoải mái, đi giày thể thao trong nhà sạch, phù hợp với môn tập, mang theo bình nước và khăn. Hỏi lễ tân về dụng cụ cho từng buổi cụ thể."),
        new Faq(5,"How often to practise","Many people practise a few times a week and keep rest days between harder sessions. Increase time or intensity gradually. This is general guidance, not a personal plan; your coach can advise a schedule for you.",
            List.of("how often","rest day","rest days","rest between","times a week","times per week","may buoi mot tuan","bao nhieu lan mot tuan","nghi ngoi"),
            "Tập bao lâu một lần","Nhiều người tập vài lần mỗi tuần và có ngày nghỉ giữa các buổi tập nặng. Hãy tăng thời gian hoặc cường độ từ từ. Đây là hướng dẫn chung, không phải kế hoạch cá nhân; Coach có thể tư vấn lịch tập cho riêng bạn."),
        new Faq(6,"Your first class","For your first class, arrive about 10 to 15 minutes early, check in at reception, and tell the coach it is your first session. Open My bookings to confirm the date and time.",
            List.of("first class","first session","first time","first lesson","buoi dau tien","lan dau"),
            "Buổi học đầu tiên","Với buổi học đầu tiên, hãy đến sớm khoảng 10 đến 15 phút, check-in tại quầy lễ tân và báo Coach đây là buổi đầu của bạn. Mở My bookings để xác nhận ngày giờ."),
        new Faq(7,"Building good technique","Practise controlled technique at a comfortable pace before adding speed, distance or force, and pause to reset between repetitions. Ask your coach to check your technique during class.",
            List.of("technique","good form","proper form","ky thuat"),
            "Tập đúng kỹ thuật","Tập kỹ thuật có kiểm soát ở tốc độ thoải mái trước khi tăng tốc độ, khoảng cách hay lực, và nghỉ để chỉnh lại tư thế giữa các lần lặp. Nhờ Coach kiểm tra kỹ thuật của bạn trong giờ học."));
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
            vi?rs.getString("sport_name")+": giá gốc "+price(true,rs.getBigDecimal("price_amount"))+" VND, "+rs.getInt("session_count")+" buổi, hiệu lực "+rs.getInt("duration_days")+" ngày. Giảm giá và thanh toán được kiểm tra khi mua gói."
              :rs.getString("sport_name")+": "+rs.getBigDecimal("price_amount").toPlainString()+" VND base price, "+rs.getInt("session_count")+" sessions, valid "+rs.getInt("duration_days")+" days. Discounts and payment status are checked in the purchase flow.",
            "/member/packages/"+rs.getLong("id")),values.toArray());
        return response(vi,mode,sources.isEmpty()?"NO_ANSWER":"ANSWERED",sources.isEmpty()
            ?t(vi,"No matching active packages are available in the current catalog. Reception can confirm future offers.",
                "Hiện không có gói tập phù hợp đang mở bán. Lễ tân có thể cho bạn biết các ưu đãi sắp tới.")
            :t(vi,"Current active package base prices from the center catalog (up to 5, lowest price first). These are catalog prices, not a payment confirmation.",
                "Giá gốc các gói tập đang mở bán của trung tâm (tối đa 5 gói, giá thấp nhất trước). Đây là giá niêm yết, chưa phải xác nhận thanh toán."),sources);
    }
    private Consultation sessions(boolean vi,String mode,Long sportId,String day) {
        LocalDate today=LocalDate.now(clock);
        LocalDate from=day.equals("TOMORROW")?today.plusDays(1):today;
        LocalDate to=day.equals("UPCOMING")?today.plusDays(14):from;
        // Coach-led sessions are only listed while their Coach is active and still teaches the class.
        String sql="""
            SELECT s.id,c.name,sp.name sport_name,s.session_date,s.start_time,s.end_time,s.capacity-s.booked_count free_seats
            FROM class_session s JOIN sport sp ON sp.id=s.sport_id LEFT JOIN sport_class c ON c.id=s.class_id
            LEFT JOIN user_account cu ON cu.id=s.coach_id
            WHERE s.status='PUBLISHED' AND sp.is_active=1 AND (c.id IS NULL OR c.is_active=1)
                AND (s.training_type<>'COACH_LED' OR (cu.status='ACTIVE' AND (c.id IS NULL OR c.coach_id=s.coach_id)))
                AND s.booked_count<s.capacity AND s.session_date BETWEEN ? AND ?
                AND (s.session_date>? OR s.start_time>CAST(? AS time))
            """;
        var values=new ArrayList<Object>(List.of(Date.valueOf(from),Date.valueOf(to),Date.valueOf(today),Time.valueOf(LocalTime.now(clock))));
        if(sportId!=null){sql+=" AND s.sport_id=?";values.add(sportId);}
        sql+=" ORDER BY s.session_date,s.start_time,s.id";
        var sources=jdbc.query(sql,(rs,row)->new ConsultationSource("CLASS_SESSION",rs.getLong("id"),
            rs.getString("name")==null?rs.getString("sport_name")+t(vi," practice"," - tự tập"):rs.getString("name"),
            vi?rs.getString("sport_name")+": "+date(true,rs.getDate("session_date"))+" "+time(true,rs.getTime("start_time"))+"-"+time(true,rs.getTime("end_time"))+" (giờ Việt Nam), còn "+rs.getInt("free_seats")+" chỗ. Chưa kiểm tra gói tập của bạn."
              :rs.getString("sport_name")+": "+rs.getDate("session_date")+" "+rs.getTime("start_time")+"-"+rs.getTime("end_time")+" (Vietnam time), "+rs.getInt("free_seats")+" places remaining. Package eligibility has not been checked.",
            "/member/sessions/"+rs.getLong("id")),values.toArray());
        var visibleIds=visibility.filter(sources.stream().map(ConsultationSource::id).toList());
        var visible=sources.stream().filter(source->visibleIds.contains(source.id())).limit(5).toList();
        String range=vi?"từ "+from.format(VI_DATE)+" đến "+to.format(VI_DATE):"for "+from+" through "+to;
        return response(vi,mode,visible.isEmpty()?"NO_ANSWER":"ANSWERED",visible.isEmpty()
            ?t(vi,"No matching published sessions with available places were found "+range+". Reception can confirm other dates.",
                "Không tìm thấy buổi học đã công bố còn chỗ "+range+". Lễ tân có thể kiểm tra giúp các ngày khác.")
            :t(vi,"Published sessions with available places "+range+" (up to 5, earliest first). Open the source to check current eligibility.",
                "Các buổi học đã công bố còn chỗ "+range+" (tối đa 5 buổi, sớm nhất trước). Mở từng buổi để kiểm tra điều kiện đặt."),visible);
    }

    /** F6-04: the Member's own active packages, membership card and number of upcoming bookings. */
    private Consultation myPackages(boolean vi,String mode,long memberId) {
        LocalDate today=LocalDate.now(clock);
        var sources=new ArrayList<ConsultationSource>(jdbc.query("""
            SELECT TOP (5) r.id,p.name,sp.name sport_name,r.remaining_sessions,r.total_sessions,r.end_date
            FROM sport_package_registration r JOIN sport_package p ON p.id=r.package_id JOIN sport sp ON sp.id=p.sport_id
            WHERE r.member_id=? AND r.status='ACTIVE' AND r.end_date>=? ORDER BY r.end_date,r.id
            """,(rs,row)->new ConsultationSource("MY_PACKAGE",rs.getLong("id"),rs.getString("name"),
                vi?rs.getString("sport_name")+": còn "+rs.getInt("remaining_sessions")+"/"+rs.getInt("total_sessions")+" buổi, hiệu lực đến "+date(true,rs.getDate("end_date"))+"."
                  :rs.getString("sport_name")+": "+rs.getInt("remaining_sessions")+" of "+rs.getInt("total_sessions")+" sessions available, valid until "+rs.getDate("end_date")+".",
                "/member/membership"),memberId,today));
        sources.addAll(jdbc.query("""
            SELECT TOP (1) c.id,t.name,t.discount_percentage,c.end_date FROM member_card c JOIN membership_card_tier t ON t.id=c.tier_id
            WHERE c.member_id=? AND c.status='ACTIVE' AND c.start_date<=? AND (c.end_date IS NULL OR c.end_date>=?)
            ORDER BY t.discount_percentage DESC,c.end_date DESC
            """,(rs,row)->new ConsultationSource("MY_CARD",rs.getLong("id"),rs.getString("name"),
                vi?"Giảm "+rs.getInt("discount_percentage")+"% khi mua gói tập"+(rs.getDate("end_date")==null?", không thời hạn.":", hiệu lực đến "+date(true,rs.getDate("end_date"))+".")
                  :rs.getInt("discount_percentage")+"% package discount"+(rs.getDate("end_date")==null?", no end date.":", valid until "+rs.getDate("end_date")+"."),
                "/member/membership/cards"),memberId,today,today));
        Integer upcoming=jdbc.queryForObject("""
            SELECT COUNT(*) FROM booking b JOIN class_session s ON s.id=b.session_id
            WHERE b.member_id=? AND b.status='CONFIRMED' AND s.status<>'CANCELLED'
                AND (s.session_date>? OR (s.session_date=? AND s.start_time>CAST(? AS time)))
            """,Integer.class,memberId,Date.valueOf(today),Date.valueOf(today),Time.valueOf(LocalTime.now(clock)));
        int count=upcoming==null?0:upcoming;
        if(sources.isEmpty())return response(vi,mode,"ANSWERED",t(vi,"You have no active sport package or membership card. Browse the package catalog to buy one.",
                "Bạn chưa có gói tập hay thẻ thành viên nào đang hoạt động. Hãy xem danh mục gói tập để mua."),
            List.of(new ConsultationSource("PRODUCT_GUIDANCE",0,t(vi,"Sport packages","Gói tập"),t(vi,"Current packages and prices.","Các gói tập và giá hiện tại."),"/member/packages")));
        return response(vi,mode,"ANSWERED",t(vi,"Your active packages and card from your own records. You also have "+count
            +" upcoming confirmed booking(s); a session is used from your package when your attendance is recorded.",
            "Gói tập và thẻ thành viên đang hoạt động của bạn, lấy từ dữ liệu của chính bạn. Bạn có "+count
            +" buổi đã đặt sắp tới; mỗi buổi chỉ bị trừ vào gói khi bạn được điểm danh."),sources);
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
                vi?date(true,rs.getDate("session_date"))+" "+time(true,rs.getTime("start_time"))+"-"+time(true,rs.getTime("end_time"))+" (giờ Việt Nam), mã buổi "+rs.getString("session_code")+"."
                  :rs.getDate("session_date")+" "+rs.getTime("start_time")+"-"+rs.getTime("end_time")+" (Vietnam time), session "+rs.getString("session_code")+".",
                "/member/bookings"),memberId,Date.valueOf(today),Date.valueOf(today),Time.valueOf(LocalTime.now(clock)));
        return response(vi,mode,"ANSWERED",sources.isEmpty()
                ?t(vi,"You have no upcoming confirmed bookings. Find a class to book one.","Bạn chưa có buổi nào đã đặt sắp tới. Hãy tìm lớp để đặt.")
                :t(vi,"Your next confirmed bookings from your own records (up to 5, earliest first).","Các buổi bạn đã đặt sắp tới, lấy từ dữ liệu của chính bạn (tối đa 5 buổi, sớm nhất trước)."),
            sources.isEmpty()?List.of(new ConsultationSource("PRODUCT_GUIDANCE",0,t(vi,"Find a class","Tìm lớp học"),
                t(vi,"Published classes with available places.","Các lớp đã công bố còn chỗ."),"/member/classes")):sources);
    }

    /** F6-04: the Member's latest coach feedback and open homework, shown as recorded by the Coach. */
    private Consultation myTraining(boolean vi,String mode,long memberId) {
        LocalDate today=LocalDate.now(clock);
        var sources=new ArrayList<ConsultationSource>(jdbc.query("""
            SELECT TOP (1) r.id,r.comments,r.created_at,u.full_name coach_name
            FROM coach_feedback r JOIN session_plan p ON p.id=r.plan_id AND p.member_id=r.member_id JOIN user_account u ON u.id=r.coach_id
            WHERE r.member_id=? AND p.is_template=0 ORDER BY r.created_at DESC,r.id DESC
            """,(rs,row)->new ConsultationSource("COACH_FEEDBACK",rs.getLong("id"),
                vi?"Nhận xét gần nhất của Coach ("+rs.getTimestamp("created_at").toLocalDateTime().toLocalDate().format(VI_DATE)+")"
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
            """,(rs,row)->new ConsultationSource("HOMEWORK",rs.getLong("id"),t(vi,"Homework: ","Bài tập về nhà: ")+rs.getString("title"),
                vi?rs.getString("class_name")+", hạn "+date(true,rs.getDate("due_date"))+". "+rs.getString("content")
                  :rs.getString("class_name")+", due "+rs.getDate("due_date")+". "+rs.getString("content"),
                "/member/training/classes/"+rs.getLong("class_id")),Date.valueOf(today),memberId));
        return response(vi,mode,"ANSWERED",sources.isEmpty()
                ?t(vi,"No coach feedback or open homework is recorded for you yet.","Chưa có nhận xét hay bài tập về nhà nào của Coach dành cho bạn.")
                :t(vi,"Your latest coach feedback and open homework, shown as your coach recorded them. Ask your coach if you need clarification.",
                    "Nhận xét gần nhất và bài tập về nhà còn hạn của bạn, giữ nguyên văn như Coach đã ghi. Hãy hỏi Coach nếu cần giải thích thêm."),
            sources.isEmpty()?List.of(askCoach(vi)):sources);
    }

    private static ConsultationSource askCoach(boolean vi) {
        return new ConsultationSource("HUMAN_COACH",0,t(vi,"Ask your coach","Hỏi Coach của bạn"),
            t(vi,"Private human conversation; no automated coach reply.","Trao đổi riêng với Coach; không có trả lời tự động."),"/member/training/questions");
    }
    private static Consultation faqAnswer(boolean vi,String mode,Faq faq) {
        return response(vi,mode,"ANSWERED",vi?"Câu hỏi chung về tập luyện (hướng dẫn cố định của trung tâm, không phải tư vấn cá nhân hay y tế): "+faq.viAnswer()
                :"General exercise FAQ (curated center guidance, not personal or medical advice): "+faq.answer(),
            List.of(new ConsultationSource("CURATED_FAQ",faq.id(),vi?"FAQ trung tâm: "+faq.viTitle():"Center FAQ: "+faq.title(),
                vi?"Câu trả lời cố định từ FAQ của trung tâm ("+FAQ_VERSION+"). Hãy hỏi Coach để được tư vấn cho riêng bạn."
                  :"Fixed answer from the curated center FAQ ("+FAQ_VERSION+"). Ask your coach for advice about your own training.","/member/training/questions")));
    }
    private static Consultation coach(boolean vi,String mode) {
        return response(vi,mode,"NO_ANSWER",t(vi,"I cannot assess personal training or health concerns. Ask your assigned human coach about your practice plan.",
                "Trợ lý không đánh giá được vấn đề tập luyện cá nhân hay sức khỏe. Hãy hỏi Coach phụ trách của bạn về kế hoạch tập."),
            List.of(askCoach(vi)));
    }
    private static Consultation unknown(boolean vi,String mode) {
        return response(vi,mode,"NO_ANSWER",t(vi,"I do not have a grounded answer to that question. I can show published sessions, active package prices, your own packages, bookings and coach feedback, explain how to check booking eligibility, or answer a few general exercise questions such as warming up. Contact reception for other center information.",
            "Tôi không có câu trả lời dựa trên dữ liệu của trung tâm cho câu hỏi này. Tôi có thể cho bạn xem các buổi học đã công bố, giá gói tập, gói tập, lịch đã đặt và nhận xét của Coach dành cho bạn, hướng dẫn cách kiểm tra điều kiện đặt chỗ, hoặc trả lời vài câu hỏi chung như cách khởi động. Hãy liên hệ lễ tân để biết thông tin khác."),List.of());
    }
    private static Consultation signIn(boolean vi,String mode) {
        return response(vi,mode,"NO_ANSWER",t(vi,"Sign in as a Member to ask about your own packages, bookings or training.",
            "Hãy đăng nhập bằng tài khoản Member để hỏi về gói tập, lịch đã đặt hay việc tập luyện của bạn."),List.of());
    }
    private static Consultation response(boolean vi,String mode,String status,String answer,List<ConsultationSource> sources) {
        return new Consultation(mode,status,answer,false,sources,vi?"vi":"en");
    }
}
