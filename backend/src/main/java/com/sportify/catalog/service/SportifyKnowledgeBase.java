package com.sportify.catalog.service;

import com.sportify.catalog.dto.TrainingGuidanceResponse.Consultation;
import com.sportify.catalog.dto.TrainingGuidanceResponse.ConsultationSource;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.*;

/**
 * Verified knowledge base of Sportify center workflows, role permissions and navigation guidance.
 * Provides grounded instructions across Manager, Receptionist, Coach and Member roles.
 */
@Component
public class SportifyKnowledgeBase {

    public record TopicGuidance(
            String topicCode,
            List<String> keywords,
            List<String> plainKeywords,
            String enTitle,
            String viTitle,
            Map<String, String> enRoleAnswers,
            Map<String, String> viRoleAnswers,
            String sourceKind,
            String managerPath,
            String receptionistPath,
            String coachPath,
            String memberPath
    ) {
        public String getAnswer(String role, boolean vi) {
            String normalizedRole = (role == null || role.isBlank()) ? "MEMBER" : role.trim().toUpperCase(Locale.ROOT);
            if (normalizedRole.startsWith("ROLE_")) {
                normalizedRole = normalizedRole.substring(5);
            }
            Map<String, String> answers = vi ? viRoleAnswers : enRoleAnswers;
            if (answers.containsKey(normalizedRole)) {
                return answers.get(normalizedRole);
            }
            return answers.getOrDefault("DEFAULT", vi ? viRoleAnswers.get("DEFAULT") : enRoleAnswers.get("DEFAULT"));
        }

        public String getPathForRole(String role) {
            String normalizedRole = (role == null || role.isBlank()) ? "MEMBER" : role.trim().toUpperCase(Locale.ROOT);
            if (normalizedRole.startsWith("ROLE_")) {
                normalizedRole = normalizedRole.substring(5);
            }
            return switch (normalizedRole) {
                case "MANAGER" -> managerPath;
                case "RECEPTIONIST" -> receptionistPath;
                case "COACH" -> coachPath;
                default -> memberPath;
            };
        }
    }

    private static final List<TopicGuidance> TOPICS = new ArrayList<>();
    private static final Map<String, TopicGuidance> BY_CODE = new HashMap<>();

    static {
        initializeTopics();
    }

    public static List<TopicGuidance> getAllTopics() {
        return Collections.unmodifiableList(TOPICS);
    }

    public static TopicGuidance getTopic(String topicCode) {
        if (topicCode == null) return null;
        return BY_CODE.get(topicCode);
    }

    /**
     * Finds the best matching topic for a user question.
     */
    public static Optional<TopicGuidance> findTopic(String rawQuestion, String plainQuestion) {
        if (rawQuestion == null || rawQuestion.isBlank()) {
            return Optional.empty();
        }
        String lowerRaw = rawQuestion.toLowerCase(Locale.ROOT);
        String lowerPlain = plainQuestion != null ? plainQuestion.toLowerCase(Locale.ROOT) : plain(lowerRaw);

        for (TopicGuidance topic : TOPICS) {
            for (String kw : topic.keywords()) {
                if (lowerRaw.contains(kw)) {
                    return Optional.of(topic);
                }
            }
            for (String pkw : topic.plainKeywords()) {
                if (lowerPlain.contains(pkw)) {
                    return Optional.of(topic);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Builds a Consultation result from the knowledge base for a specific role and language.
     */
    public static Consultation buildConsultation(TopicGuidance topic, String role, boolean vi, String mode) {
        String answer = topic.getAnswer(role, vi);
        String title = vi ? topic.viTitle() : topic.enTitle();
        String path = topic.getPathForRole(role);
        List<ConsultationSource> sources = new ArrayList<>();
        if (path != null && !path.isBlank()) {
            sources.add(new ConsultationSource(topic.sourceKind(), 0, title, answer, path));
        } else {
            sources.add(new ConsultationSource(topic.sourceKind(), 0, title, answer, ""));
        }
        return new Consultation(mode, "ANSWERED", answer, false, sources, vi ? "vi" : "en", null);
    }

    public static String plain(String text) {
        if (text == null) return "";
        String stripped = Normalizer.normalize(text.replace('\u0111', 'd').replace('\u0110', 'd'), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return stripped.toLowerCase(Locale.ROOT);
    }

    private static void initializeTopics() {
        List<TopicGuidance> topics = TOPICS;
        // 1. MEMBER CREATION & REGISTRATION
        topics.add(new TopicGuidance(
                "USER_CREATION_MEMBER",
                List.of("create member", "create a member", "create new member", "register member", "register a new member",
                        "register new member", "enroll member", "enroll new member", "add member", "add a member",
                        "how can i create a new member", "how to create a member", "how to create member",
                        "t\u1EA1o h\u1ED9i vi\u00EAn", "t\u1EA1o th\u00E0nh vi\u00EAn", "\u0111\u0103ng k\u00FD h\u1ED9i vi\u00EAn",
                        "\u0111\u0103ng k\u00FD th\u00E0nh vi\u00EAn", "th\u00EAm h\u1ED9i vi\u00EAn", "th\u00EAm th\u00E0nh vi\u00EAn"),
                List.of("create member", "create a member", "create new member", "register member", "enroll member",
                        "add member", "how can i create a new member", "how to create a member",
                        "tao hoi vien", "tao thanh vien", "dang ky hoi vien", "dang ky thanh vien", "them hoi vien", "them thanh vien"),
                "Member Account Registration",
                "\u0110\u0103ng k\u00FD t\u00E0i kho\u1EA3n h\u1ED9i vi\u00EAn",
                Map.of(
                        "MANAGER", "Member accounts are enrolled at the front desk by Receptionists at /reception/register or self-registered online by members at /register. In Manager Workspace > Users (/manager), Managers can view, filter, activate, deactivate existing member accounts and reset member passwords, but staff creation in User Management is specifically for Coach and Receptionist accounts.",
                        "RECEPTIONIST", "As Receptionist, navigate to Receptionist Workspace > Register and renew (/reception/register) or Member search (/reception). Select New Member registration, enter the member's full name, email address, phone number, and submit to enroll the member. You can also assign an initial sport package or membership card.",
                        "COACH", "Member registration is handled at the front desk by Receptionists at /reception/register or self-registered online by members at /register. Coaches do not have administrative permission to create accounts.",
                        "MEMBER", "You can register a new account on the public Sportify website by visiting the Register page (/register) and submitting your full name, email, phone number, and password, or by visiting the center front desk where a Receptionist can enroll you.",
                        "DEFAULT", "Members can register online at /register or be enrolled in person at the front desk by a Receptionist at /reception/register."
                ),
                Map.of(
                        "MANAGER", "T\u00E0i kho\u1EA3n h\u1ED9i vi\u00EAn \u0111\u01B0\u1EE3c l\u1EC5 t\u00E2n ghi danh t\u1EA1i qu\u1EA7y (/reception/register) ho\u1EB7c ng\u01B0\u1EDDi d\u00F9ng t\u1EF1 \u0111\u0103ng k\u00FD t\u1EA1i /register. T\u1EA1i Manager Workspace > Users (/manager), Manager qu\u1EA3n l\u00FD danh s\u00E1ch, k\u00EDch ho\u1EA1t, v\u00F4 hi\u1EC7u h\u00F3a v\u00E0 \u0111\u1EB7t l\u1EA1i m\u1EADt kh\u1EA9u cho h\u1ED9i vi\u00EAn.",
                        "RECEPTIONIST", "V\u1EDBi vai tr\u00F2 L\u1EC5 t\u00E2n, v\u00E0o Receptionist Workspace > Register and renew (/reception/register) ho\u1EB7c Member search (/reception). Ch\u1ECDn \u0111\u0103ng k\u00FD h\u1ED9i vi\u00EAn m\u1EDBi, nh\u1EADp h\u1ECD t\u00EAn, email, s\u1ED1 \u0111i\u1EC7n tho\u1EA1i v\u00E0 ho\u00E0n t\u1EA5t ghi danh.",
                        "COACH", "Vi\u1EC7c t\u1EA1o t\u00E0i kho\u1EA3n h\u1ED9i vi\u00EAn do L\u1EC5 t\u00E2n th\u1EF1c hi\u1EC7n t\u1EA1i qu\u1EA7y (/reception/register) ho\u1EB7c ng\u01B0\u1EDDi d\u00F9ng t\u1EF1 \u0111\u0103ng k\u00FD t\u1EA1i /register. Coach kh\u00F4ng c\u00F3 quy\u1EC1n qu\u1EA3n tr\u1ECB t\u1EA1o t\u00E0i kho\u1EA3n.",
                        "MEMBER", "B\u1EA1n c\u00F3 th\u1EC3 \u0111\u0103ng k\u00FD t\u00E0i kho\u1EA3n m\u1EDBi tr\u00EAn trang web Sportify t\u1EA1i /register ho\u1EB7c \u0111\u1EBFn qu\u1EA7y l\u1EC5 t\u00E2n \u0111\u1EC3 \u0111\u01B0\u1EE3c h\u1ED7 tr\u1EE3 ghi danh.",
                        "DEFAULT", "H\u1ED9i vi\u00EAn \u0111\u0103ng k\u00FD tr\u1EF1c tuy\u1EBFn t\u1EA1i /register ho\u1EB7c \u0111\u01B0\u1EE3c l\u1EC5 t\u00E2n ghi danh t\u1EA1i qu\u1EA7y /reception/register."
                ),
                "USER_MANAGEMENT",
                "/manager",
                "/reception/register",
                "/coach",
                "/register"
        ));

        // 2. STAFF CREATION (COACH & RECEPTIONIST)
        topics.add(new TopicGuidance(
                "USER_CREATION_STAFF",
                List.of("create receptionist", "create a receptionist", "create new receptionist", "create coach",
                        "create a coach", "create new coach", "create staff", "create a staff", "add receptionist",
                        "add a receptionist", "add coach", "add a coach", "add staff", "new receptionist", "new coach",
                        "how can i create a new receptionist or coach", "how can i create a new receptionist", "how can i create a new coach",
                        "t\u1EA1o l\u1EC5 t\u00E2n", "t\u1EA1o hu\u1EA5n luy\u1EC7n vi\u00EAn", "t\u1EA1o hlv", "t\u1EA1o coach", "t\u1EA1o nh\u00E2n vi\u00EAn",
                        "th\u00EAm l\u1EC5 t\u00E2n", "th\u00EAm hu\u1EA5n luy\u1EC7n vi\u00EAn", "th\u00EAm hlv", "th\u00EAm coach", "th\u00EAm nh\u00E2n vi\u00EAn"),
                List.of("create receptionist", "create coach", "create staff", "add receptionist", "add coach",
                        "add staff", "new receptionist", "new coach", "how can i create a new receptionist or coach",
                        "tao le tan", "tao huan luyen vien", "tao hlv", "tao coach", "tao nhan vien",
                        "them le tan", "them huan luyen vien", "them hlv", "them coach", "them nhan vien"),
                "Staff Account Creation",
                "T\u1EA1o t\u00E0i kho\u1EA3n nh\u00E2n vi\u00EAn (HLV / L\u1EC5 t\u00E2n)",
                Map.of(
                        "MANAGER", "As Manager, follow these steps to create a new Coach or Receptionist account (Spori only guides and does not create accounts for you):\n1. Open [User Management](/manager).\n2. Click 'Add account' to open the staff creation form.\n3. Enter the staff member's full name, email address, phone number, and a temporary password.\n4. Select the role (Coach or Receptionist). For a Coach, select at least one sport they are qualified to teach.\n5. Click Submit to create and activate the account.",
                        "RECEPTIONIST", "Creating staff accounts (Receptionist and Coach) is an administrative operation restricted to Managers only at /manager. Receptionists do not have permission to create staff accounts. Please contact your Manager for staff account requests.",
                        "COACH", "Staff account creation is an administrative operation restricted to Managers only at /manager. Coaches do not have permission to create accounts.",
                        "MEMBER", "Staff account creation (Receptionist and Coach) is strictly an administrative action restricted to Managers only. Members do not have permission to access staff management.",
                        "DEFAULT", "Creating staff accounts (Receptionist and Coach) is a Manager-only administrative action performed at [User Management](/manager)."
                ),
                Map.of(
                        "MANAGER", "V\u1EDBi vai tr\u00F2 Manager, h\u00E3y l\u00E0m theo c\u00E1c b\u01B0\u1EDBc sau \u0111\u1EC3 t\u1EA1o t\u00E0i kho\u1EA3n Coach ho\u1EB7c L\u1EC5 t\u00E2n (Spori ch\u1EC9 h\u01B0\u1EDBng d\u1EABn v\u00E0 kh\u00F4ng t\u1EF1 t\u1EA1o t\u00E0i kho\u1EA3n thay b\u1EA1n):\n1. M\u1EDF [User Management](/manager).\n2. B\u1EA5m n\u00FAt 'Add account' \u0111\u1EC3 m\u1EDF bi\u1EC3u m\u1EABu t\u1EA1o nh\u00E2n vi\u00EAn.\n3. Nh\u1EADp h\u1ECD t\u00EAn, email, s\u1ED1 \u0111i\u1EC7n tho\u1EA1i v\u00E0 m\u1EADt kh\u1EA9u t\u1EA1m.\n4. Ch\u1ECDn vai tr\u00F2 (Coach ho\u1EB7c Receptionist). V\u1EDBi Coach, ch\u1ECDn \u00EDt nh\u1EA5t m\u1ED9t m\u00F4n th\u1EC3 thao chuy\u00EAn m\u00F4n.\n5. B\u1EA5m Submit \u0111\u1EC3 ho\u00E0n t\u1EA5t.",
                        "RECEPTIONIST", "T\u1EA1o t\u00E0i kho\u1EA3n nh\u00E2n vi\u00EAn (L\u1EC5 t\u00E2n v\u00E0 Coach) thu\u1ED9c quy\u1EC1n qu\u1EA3n tr\u1ECB c\u1EE7a Manager. L\u1EC5 t\u00E2n kh\u00F4ng c\u00F3 quy\u1EC1n th\u1EF1c hi\u1EC7n. Vui l\u00F2ng li\u00EAn h\u1EC7 Manager.",
                        "COACH", "Vi\u1EC7c t\u1EA1o t\u00E0i kho\u1EA3n nh\u00E2n vi\u00EAn thu\u1ED9c th\u1EA9m quy\u1EC1n c\u1EE7a Manager t\u1EA1i /manager. Coach kh\u00F4ng c\u00F3 quy\u1EC1n t\u1EA1o t\u00E0i kho\u1EA3n.",
                        "MEMBER", "T\u1EA1o t\u00E0i kho\u1EA3n nh\u00E2n vi\u00EAn l\u00E0 t\u00E1c v\u1EE5 qu\u1EA3n tr\u1ECB ch\u1EC9 d\u00E0nh cho Manager. H\u1ED9i vi\u00EAn kh\u00F4ng c\u00F3 quy\u1EC1n th\u1EF1c hi\u1EC7n.",
                        "DEFAULT", "T\u1EA1o t\u00E0i kho\u1EA3n nh\u00E2n vi\u00EAn (L\u1EC5 t\u00E2n / Coach) do Manager th\u1EF1c hi\u1EC7n t\u1EA1i Manager Workspace > Users (/manager)."
                ),
                "STAFF_MANAGEMENT",
                "/manager",
                "/reception",
                "/coach",
                "/member"
        ));

        // 3. SPORT PACKAGE MANAGEMENT
        topics.add(new TopicGuidance(
                "SPORT_PACKAGES",
                List.of("manage sport packages", "manage sport package", "manage packages", "manage package",
                        "create sport package", "create package", "edit sport package", "edit package",
                        "qu\u1EA3n l\u00FD g\u00F3i t\u1EADp", "t\u1EA1o g\u00F3i t\u1EADp", "s\u1EEDa g\u00F3i t\u1EADp"),
                List.of("manage sport packages", "manage packages", "create sport package", "create package",
                        "edit package", "quan ly goi tap", "tao goi tap", "sua goi tap"),
                "Sport Package Management",
                "Qu\u1EA3n l\u00FD g\u00F3i t\u1EADp th\u1EC3 thao",
                Map.of(
                        "MANAGER", "As Manager, navigate to Manager Workspace > Sport packages (/manager/packages). Click 'Create package' to open the configuration form: enter package name, select sport, set base price (in VND), session count, duration (days), and description. Save to publish. You can also edit existing packages to modify prices or activate/deactivate them.",
                        "RECEPTIONIST", "Package configuration is managed by Managers at /manager/packages. Receptionists can register packages for members at Front Desk > Register and renew (/reception/register).",
                        "COACH", "Sport package pricing and rules are configured by Managers at /manager/packages.",
                        "MEMBER", "You can browse active sport packages and view details by navigating to Member Workspace > My packages (/member/packages). Select any package to review its benefits and purchase.",
                        "DEFAULT", "Managers create and edit sport packages at /manager/packages. Members browse and purchase packages at /member/packages."
                ),
                Map.of(
                        "MANAGER", "V\u1EDBi vai tr\u00F2 Manager, v\u00E0o Manager Workspace > Sport packages (/manager/packages). B\u1EA5m 'Create package': nh\u1EADp t\u00EAn g\u00F3i, ch\u1ECDn m\u00F4n, gi\u00E1 g\u1ED1c (VND), s\u1ED1 bu\u1ED5i, th\u1EDDi h\u1EA1n (ng\u00E0y) v\u00E0 m\u00F4 t\u1EA3. B\u1EA1n c\u0169ng c\u00F3 th\u1EC3 ch\u1EC9nh s\u1EEDa gi\u00E1 ho\u1EB7c tr\u1EA1ng th\u00E1i ho\u1EA1t \u0111\u1ED9ng c\u1EE7a c\u00E1c g\u00F3i hi\u1EC7n c\u00F3.",
                        "RECEPTIONIST", "C\u1EA5u h\u00ECnh g\u00F3i t\u1EADp do Manager qu\u1EA3n l\u00FD t\u1EA1i /manager/packages. L\u1EC5 t\u00E2n c\u00F3 th\u1EC3 \u0111\u0103ng k\u00FD g\u00F3i cho h\u1ED9i vi\u00EAn t\u1EA1i /reception/register.",
                        "COACH", "G\u00F3i t\u1EADp do Manager thi\u1EBFt l\u1EADp t\u1EA1i /manager/packages.",
                        "MEMBER", "H\u1ED9i vi\u00EAn xem danh m\u1EE5c g\u00F3i t\u1EADp t\u1EA1i Member Workspace > My packages (/member/packages) \u0111\u1EC3 ch\u1ECDn g\u00F3i v\u00E0 mua.",
                        "DEFAULT", "Manager qu\u1EA3n l\u00FD g\u00F3i t\u1EADp t\u1EA1i /manager/packages. H\u1ED9i vi\u00EAn xem v\u00E0 mua g\u00F3i t\u1EA1i /member/packages."
                ),
                "SPORT_PACKAGES",
                "/manager/packages",
                "/reception/register",
                "/coach",
                "/member/packages"
        ));

        // 4. MEMBERSHIP CARDS MANAGEMENT
        topics.add(new TopicGuidance(
                "MEMBERSHIP_CARDS",
                List.of("manage membership cards", "membership cards", "membership card", "card tiers", "membership tier",
                        "qu\u1EA3n l\u00FD th\u1EBB", "th\u1EBB th\u00E0nh vi\u00EAn", "th\u1EBB h\u1ED9i vi\u00EAn", "h\u1EA1ng th\u1EBB"),
                List.of("manage membership cards", "membership cards", "card tiers", "membership tier",
                        "quan ly the", "the thanh vien", "the hoi vien", "hang the"),
                "Membership Cards & Tiers",
                "Qu\u1EA3n l\u00FD th\u1EBB v\u00E0 h\u1EA1ng th\u1EBB th\u00E0nh vi\u00EAn",
                Map.of(
                        "MANAGER", "As Manager, navigate to Manager Workspace > Membership cards (/manager/cards). You can view and edit membership tiers (Silver, Gold, Platinum), configure package discount percentages, card fees, duration, and member benefits.",
                        "RECEPTIONIST", "Receptionists can issue or renew membership cards for members at Front Desk > Register and renew (/reception/register).",
                        "COACH", "Membership card tiers are managed by Managers at /manager/cards.",
                        "MEMBER", "You can check your active membership card, tier discount, and benefits at Member Workspace > My membership (/member/membership), or renew your card at Membership cards (/member/membership/cards).",
                        "DEFAULT", "Membership tiers are configured by Managers at /manager/cards and managed by Receptionists at /reception/register."
                ),
                Map.of(
                        "MANAGER", "V\u1EDBi vai tr\u00F2 Manager, v\u00E0o Manager Workspace > Membership cards (/manager/cards) \u0111\u1EC3 xem v\u00E0 thi\u1EBFt l\u1EADp c\u00E1c h\u1EA1ng th\u1EBB (Silver, Gold, Platinum), ph\u1EA7n tr\u0103m gi\u1EA3m gi\u00E1 g\u00F3i t\u1EADp, gi\u00E1 th\u1EBB v\u00E0 quy\u1EC1n l\u1EE3i.",
                        "RECEPTIONIST", "L\u1EC5 t\u00E2n ph\u00E1t h\u00E0nh ho\u1EB7c gia h\u1EA1n th\u1EBB th\u00E0nh vi\u00EAn cho kh\u00E1ch t\u1EA1i /reception/register.",
                        "COACH", "H\u1EA1ng th\u1EBB th\u00E0nh vi\u00EAn do Manager qu\u1EA3n l\u00FD t\u1EA1i /manager/cards.",
                        "MEMBER", "H\u1ED9i vi\u00EAn ki\u1EC3m tra th\u1EBB, h\u1EA1ng th\u1EBB v\u00E0 quy\u1EC1n gi\u1EA3m gi\u00E1 t\u1EA1i /member/membership ho\u1EB7c gia h\u1EA1n t\u1EA1i /member/membership/cards.",
                        "DEFAULT", "Manager thi\u1EBFt l\u1EADp h\u1EA1ng th\u1EBB t\u1EA1i /manager/cards. L\u1EC5 t\u00E2n c\u1EA5p th\u1EBB t\u1EA1i /reception/register."
                ),
                "MEMBERSHIP_CARDS",
                "/manager/cards",
                "/reception/register",
                "/coach",
                "/member/membership/cards"
        ));

        // 5. CLASS MANAGEMENT
        topics.add(new TopicGuidance(
                "CLASS_MANAGEMENT",
                List.of("create class", "create a class", "create new class", "edit class", "manage classes",
                        "how can i create a new class", "how to create a class",
                        "t\u1EA1o l\u1EDBp", "t\u1EA1o l\u1EDBp h\u1ECDc", "th\u00EAm l\u1EDBp", "s\u1EEDa l\u1EDBp", "qu\u1EA3n l\u00FD l\u1EDBp"),
                List.of("create class", "create a class", "create new class", "edit class", "manage classes",
                        "how can i create a new class", "how to create a class",
                        "tao lop", "tao lop hoc", "them lop", "sua lop", "quan ly lop"),
                "Class Management",
                "Qu\u1EA3n l\u00FD v\u00E0 t\u1EA1o l\u1EDBp h\u1ECDc",
                Map.of(
                        "MANAGER", "As Manager, navigate to Manager Workspace > Classes (/manager/classes). Click 'Create class' (/manager/classes/new). Fill in the class name, select sport, difficulty level (Beginner, Intermediate, Advanced), age group, default qualified coach, and maximum member capacity. Save the class, then schedule its sessions in Schedule and coaches.",
                        "RECEPTIONIST", "Class creation is restricted to Managers at /manager/classes. Receptionists can assist members in booking published sessions at /reception/bookings.",
                        "COACH", "Class creation belongs to Managers at /manager/classes. Coaches can check their assigned teaching classes at /coach.",
                        "MEMBER", "Class setup is handled by center management. Members can explore and book published classes at Member Workspace > Schedules & classes (/member/schedules).",
                        "DEFAULT", "Managers create and edit classes at Manager Workspace > Classes (/manager/classes)."
                ),
                Map.of(
                        "MANAGER", "V\u1EDBi vai tr\u00F2 Manager, v\u00E0o Manager Workspace > Classes (/manager/classes). B\u1EA5m 'Create class' (/manager/classes/new). Nh\u1EADp t\u00EAn l\u1EDBp, m\u00F4n, tr\u00ECnh \u0111\u1ED9, nh\u00F3m tu\u1ED5i, Coach ph\u1EE5 tr\u00E1ch v\u00E0 s\u1ED1 h\u1ECDc vi\u00EAn t\u1ED1i \u0111a. L\u01B0u l\u1EDBp r\u1ED3i l\u00EAn l\u1ECBch session t\u1EA1i Schedule and coaches.",
                        "RECEPTIONIST", "T\u1EA1o l\u1EDBp thu\u1ED9c quy\u1EC1n Manager t\u1EA1i /manager/classes. L\u1EC5 t\u00E2n c\u00F3 th\u1EC3 h\u1ED7 tr\u1EE3 \u0111\u1EB7t l\u1EDBp cho h\u1ED9i vi\u00EAn t\u1EA1i /reception/bookings.",
                        "COACH", "T\u1EA1o l\u1EDBp do Manager th\u1EF1c hi\u1EC7n. Coach xem c\u00E1c l\u1EDBp \u0111\u01B0\u1EE3c ph\u00E2n c\u00F4ng t\u1EA1i /coach.",
                        "MEMBER", "Vi\u1EC7c t\u1EA1o l\u1EDBp do ban qu\u1EA3n l\u00FD th\u1EF1c hi\u1EC7n. B\u1EA1n c\u00F3 th\u1EC3 xem c\u00E1c l\u1EDBp \u0111\u00E3 m\u1EDF t\u1EA1i /member/schedules.",
                        "DEFAULT", "Manager t\u1EA1o v\u00E0 ch\u1EC9nh s\u1EEDa l\u1EDBp h\u1ECDc t\u1EA1i /manager/classes."
                ),
                "CLASS_MANAGEMENT",
                "/manager/classes",
                "/reception/bookings",
                "/coach",
                "/member/classes"
        ));

        // 6. SCHEDULE AND COACH ASSIGNMENT
        topics.add(new TopicGuidance(
                "SCHEDULE_AND_COACHES",
                List.of("assign coach", "assign a coach", "assign coach to a class", "schedule session", "create session",
                        "plan session", "publish session", "reschedule", "how can i assign a coach to a class",
                        "ph\u00E2n c\u00F4ng coach", "ph\u00E2n c\u00F4ng hu\u1EA5n luy\u1EC7n vi\u00EAn", "x\u1EBFp l\u1ECBch", "t\u1EA1o bu\u1ED5i h\u1ECDc", "xu\u1EA5t b\u1EA3n bu\u1ED5i h\u1ECDc"),
                List.of("assign coach", "assign a coach", "assign coach to a class", "schedule session", "create session",
                        "publish session", "how can i assign a coach to a class",
                        "phan cong coach", "phan cong huan luyen vien", "xep lich", "tao buoi hoc", "xuat ban buoi hoc"),
                "Schedule & Coach Assignment",
                "X\u1EBFp l\u1ECBch v\u00E0 ph\u00E2n c\u00F4ng Hu\u1EA5n luy\u1EC7n vi\u00EAn",
                Map.of(
                        "MANAGER", "As Manager, navigate to Manager Workspace > Schedule and coaches (/manager/schedule). Choose the class, pick the session date, start time, end time, facility/room, and assign a qualified Coach. The system validates the coach's sport qualification and checks against schedule conflicts. Once configured, publish the session so members can book it.",
                        "RECEPTIONIST", "Session planning and coach assignment are managed by Managers at /manager/schedule. Receptionists can view schedules and assist member bookings at /reception/bookings.",
                        "COACH", "Coaches view their assigned teaching schedule and upcoming sessions at Coach Workspace > Teaching schedule (/coach) and learner rosters at /coach/roster.",
                        "MEMBER", "You can check session schedules and coach details at Member Workspace > Schedules & classes (/member/schedules).",
                        "DEFAULT", "Managers plan sessions and assign coaches at Manager Workspace > Schedule and coaches (/manager/schedule)."
                ),
                Map.of(
                        "MANAGER", "V\u1EDBi vai tr\u00F2 Manager, v\u00E0o Manager Workspace > Schedule and coaches (/manager/schedule). Ch\u1ECDn l\u1EDBp, ng\u00E0y, gi\u1EDD b\u1EAFt \u0111\u1EA7u, gi\u1EDD k\u1EBFt th\u00FAc, ph\u00F2ng v\u00E0 ch\u1ECDn Coach ph\u00F9 h\u1EE3p chuy\u00EAn m\u00F4n. H\u1EC7 th\u1ED1ng t\u1EF1 \u0111\u1ED9ng ki\u1EC3m tra tr\u00F9ng l\u1ECBch. B\u1EA5m Publish \u0111\u1EC3 xu\u1EA5t b\u1EA3n bu\u1ED5i h\u1ECDc cho h\u1ED9i vi\u00EAn \u0111\u1EB7t ch\u1ED7.",
                        "RECEPTIONIST", "X\u1EBFp l\u1ECBch v\u00E0 ph\u00E2n c\u00F4ng Coach do Manager th\u1EF1c hi\u1EC7n t\u1EA1i /manager/schedule. L\u1EC5 t\u00E2n xem l\u1ECBch \u0111\u1EC3 h\u1ED7 tr\u1EE3 kh\u00E1ch t\u1EA1i /reception/bookings.",
                        "COACH", "Coach ki\u1EC3m tra l\u1ECBch d\u1EA1y \u0111\u01B0\u1EE3c ph\u00E2n c\u00F4ng t\u1EA1i /coach v\u00E0 danh s\u00E1ch h\u1ECDc vi\u00EAn t\u1EA1i /coach/roster.",
                        "MEMBER", "H\u1ED9i vi\u00EAn xem l\u1ECBch h\u1ECDc v\u00E0 th\u00F4ng tin Coach t\u1EA1i /member/schedules.",
                        "DEFAULT", "Manager x\u1EBFp l\u1ECBch v\u00E0 ph\u00E2n c\u00F4ng Coach t\u1EA1i /manager/schedule."
                ),
                "SCHEDULE_MANAGEMENT",
                "/manager/schedule",
                "/reception/bookings",
                "/coach",
                "/member/schedules"
        ));

        // 7. ATTENDANCE OVERVIEW & CHECK-IN
        topics.add(new TopicGuidance(
                "ATTENDANCE_OVERVIEW",
                List.of("view attendance", "attendance overview", "check attendance", "attendance records", "how can i view attendance",
                        "xem \u0111i\u1EC3m danh", "t\u1ED5ng quan \u0111i\u1EC3m danh", "ki\u1EC3m tra \u0111i\u1EC3m danh"),
                List.of("view attendance", "attendance overview", "check attendance", "attendance records", "how can i view attendance",
                        "xem diem danh", "tong quan diem danh", "kiem tra diem danh"),
                "Attendance Overview & Records",
                "T\u1ED5ng quan v\u00E0 theo d\u00F5i \u0111i\u1EC3m danh",
                Map.of(
                        "MANAGER", "As Manager, navigate to Manager Workspace > Attendance overview (/manager/training/attendance). You can review all session attendances, view confirmed learner rosters, and see attendance breakdown (Attended, Absent, Excused) across the center. For missing historical records, use Missed attendance (/manager/training/attendance/backfill).",
                        "RECEPTIONIST", "Receptionists record daily member arrivals at Front Desk > Check-in (/reception/check-in) using member code or QR scanning.",
                        "COACH", "Coaches mark and view session attendance for their assigned classes at Coach Workspace > Session attendance (/coach/training/attendance).",
                        "MEMBER", "You can review your personal attendance history and attended sessions at Member Workspace > Training progress (/member/training).",
                        "DEFAULT", "Managers view center attendance at /manager/training/attendance. Coaches mark attendance at /coach/training/attendance."
                ),
                Map.of(
                        "MANAGER", "V\u1EDBi vai tr\u00F2 Manager, v\u00E0o Manager Workspace > Attendance overview (/manager/training/attendance) \u0111\u1EC3 xem t\u1ED5ng quan \u0111i\u1EC3m danh, danh s\u00E1ch h\u1ECDc vi\u00EAn \u0111\u00E3 x\u00E1c nh\u1EADn v\u00E0 t\u1EF7 l\u1EC7 c\u00F3 m\u1EB7t/v\u1EAFng. N\u1EBFu s\u00F3t l\u01B0u \u0111i\u1EC3m danh, v\u00E0o /manager/training/attendance/backfill \u0111\u1EC3 b\u1ED5 sung.",
                        "RECEPTIONIST", "L\u1EC5 t\u00E2n ghi nh\u1EADn kh\u00E1ch \u0111\u1EBFn trung t\u00E2m t\u1EA1i Front Desk > Check-in (/reception/check-in).",
                        "COACH", "Coach \u0111i\u1EC3m danh h\u1ECDc vi\u00EAn t\u1EA1i /coach/training/attendance.",
                        "MEMBER", "H\u1ED9i vi\u00EAn theo d\u00F5i l\u1ECBch s\u1EED tham gia bu\u1ED5i h\u1ECDc t\u1EA1i /member/training.",
                        "DEFAULT", "Manager xem t\u1ED5ng quan \u0111i\u1EC3m danh t\u1EA1i /manager/training/attendance."
                ),
                "ATTENDANCE",
                "/manager/training/attendance",
                "/reception/check-in",
                "/coach/training/attendance",
                "/member/training"
        ));

        // 8. MISSED ATTENDANCE BACKFILL
        topics.add(new TopicGuidance(
                "MISSED_ATTENDANCE",
                List.of("missed attendance", "backfill attendance", "recover attendance", "missing attendance",
                        "\u0111i\u1EC3m danh b\u00F9", "qu\u00EAn \u0111i\u1EC3m danh", "s\u00F3t \u0111i\u1EC3m danh"),
                List.of("missed attendance", "backfill attendance", "recover attendance", "missing attendance",
                        "diem danh bu", "quen diem danh", "sot diem danh"),
                "Missed Attendance Recovery",
                "\u0110i\u1EC3m danh b\u00F9 d\u00E0nh cho Manager",
                Map.of(
                        "MANAGER", "As Manager, navigate to Manager Workspace > Missed attendance (/manager/training/attendance/backfill). This is a Manager-only recovery feature. Select the session booking that was missed, pick the verified status (Attended, Late, Absent, Excused), provide a required explanation reason, and save. The backfill is recorded in the Activity Log for audit compliance.",
                        "RECEPTIONIST", "Attendance backfill is a Manager-only administrative action at /manager/training/attendance/backfill. Please report missed records to your Manager.",
                        "COACH", "If attendance was missed for a past session, please notify your Manager to backfill the record at /manager/training/attendance/backfill.",
                        "MEMBER", "If an attendance record was missed, notify reception or your coach so management can verify and backfill it.",
                        "DEFAULT", "Missed attendance recovery is performed by Managers at /manager/training/attendance/backfill with a mandatory reason."
                ),
                Map.of(
                        "MANAGER", "V\u1EDBi vai tr\u00F2 Manager, v\u00E0o Manager Workspace > Missed attendance (/manager/training/attendance/backfill). Ch\u1ECDn bu\u1ED5i h\u1ECDc b\u1ECB s\u00F3t, ch\u1ECDn tr\u1EA1ng th\u00E1i \u0111i\u1EC3m danh, nh\u1EADp l\u00FD do b\u1EAFt bu\u1ED9c v\u00E0 l\u01B0u. Thao t\u00E1c \u0111\u01B0\u1EE3c ghi v\u00E0o Activity Log \u0111\u1EC3 ki\u1EC3m to\u00E1n.",
                        "RECEPTIONIST", "\u0110i\u1EC3m danh b\u00F9 l\u00E0 quy\u1EC1n c\u1EE7a Manager t\u1EA1i /manager/training/attendance/backfill.",
                        "COACH", "N\u1EBFu s\u00F3t \u0111i\u1EC3m danh bu\u1ED5i \u0111\u00E3 qua, Coach h\u00E3y b\u00E1o Manager \u0111\u1EC3 th\u1EF1c hi\u1EC7n \u0111i\u1EC3m danh b\u00F9 t\u1EA1i /manager/training/attendance/backfill.",
                        "MEMBER", "N\u1EBFu bu\u1ED5i h\u1ECDc c\u1EE7a b\u1EA1n ch\u01B0a \u0111\u01B0\u1EE3c ghi nh\u1EADn \u0111i\u1EC3m danh, vui l\u00F2ng b\u00E1o l\u1EC5 t\u00E2n ho\u1EB7c Coach.",
                        "DEFAULT", "Manager th\u1EF1c hi\u1EC7n \u0111i\u1EC3m danh b\u00F9 t\u1EA1i /manager/training/attendance/backfill k\u00E8m l\u00FD do."
                ),
                "ATTENDANCE",
                "/manager/training/attendance/backfill",
                "/reception",
                "/coach",
                "/member/training"
        ));

        // 9. FINANCIAL OVERVIEW & FAILED PAYMENTS
        topics.add(new TopicGuidance(
                "FINANCIAL_MANAGEMENT",
                List.of("financial overview", "failed payments", "check failed payments", "view transactions",
                        "revenue reports", "how can i check failed payments", "how to check failed payments",
                        "t\u1ED5ng quan t\u00E0i ch\u00EDnh", "thanh to\u00E1n l\u1ED7i", "ki\u1EC3m tra thanh to\u00E1n", "b\u00E1o c\u00E1o doanh thu", "giao d\u1ECBch"),
                List.of("financial overview", "failed payments", "check failed payments", "view transactions",
                        "revenue reports", "how can i check failed payments",
                        "tong quan tai chinh", "thanh toan loi", "kiem tra thanh toan", "bao cao doanh thu", "giao dich"),
                "Financial Overview & Transactions",
                "T\u1ED5ng quan t\u00E0i ch\u00EDnh v\u00E0 giao d\u1ECBch",
                Map.of(
                        "MANAGER", "As Manager, navigate to Manager Workspace > Financial overview (/manager/finance) for summary KPIs. To inspect failed or pending payments and manage refunds, open Transactions and refunds (/manager/transactions). To analyze earnings by date, sport, or package and export data, open Revenue reports (/manager/reports).",
                        "RECEPTIONIST", "Receptionists verify and confirm pending payments at Front Desk > Payment requests (/reception/payments). For failed payments or refund requests, check /reception/refunds.",
                        "COACH", "Financial reports and payment inspection are restricted to Manager and Receptionist roles.",
                        "MEMBER", "You can view your payment status, instructions, and receipts at Member Workspace > My payments (/member/payments).",
                        "DEFAULT", "Managers view financial overview and transactions at /manager/finance and /manager/transactions."
                ),
                Map.of(
                        "MANAGER", "V\u1EDBi vai tr\u00F2 Manager, v\u00E0o Manager Workspace > Financial overview (/manager/finance) \u0111\u1EC3 xem KPI t\u00E0i ch\u00EDnh. \u0110\u1EC3 ki\u1EC3m tra thanh to\u00E1n th\u1EA5t b\u1EA1i ho\u1EB7c duy\u1EC7t ho\u00E0n ti\u1EC1n, v\u00E0o Transactions and refunds (/manager/transactions). V\u00E0o Revenue reports (/manager/reports) \u0111\u1EC3 xu\u1EA5t b\u00E1o c\u00E1o.",
                        "RECEPTIONIST", "L\u1EC5 t\u00E2n x\u00E1c nh\u1EADn v\u00E0 x\u1EED l\u00FD c\u00E1c y\u00EAu c\u1EA7u thanh to\u00E1n t\u1EA1i /reception/payments.",
                        "COACH", "T\u00E0i ch\u00EDnh do Manager v\u00E0 L\u1EC5 t\u00E2n qu\u1EA3n l\u00FD.",
                        "MEMBER", "H\u1ED9i vi\u00EAn theo d\u00F5i tr\u1EA1ng th\u00E1i thanh to\u00E1n v\u00E0 bi\u00EAn lai t\u1EA1i /member/payments.",
                        "DEFAULT", "Manager qu\u1EA3n l\u00FD t\u00E0i ch\u00EDnh t\u1EA1i /manager/finance v\u00E0 /manager/transactions."
                ),
                "FINANCE",
                "/manager/finance",
                "/reception/payments",
                "/coach",
                "/member/payments"
        ));

        // 10. PAYMENT CONFIRMATION (RECEPTION)
        topics.add(new TopicGuidance(
                "PAYMENT_CONFIRMATION",
                List.of("confirm a payment", "confirm payment", "how can i confirm a payment", "verify payment",
                        "x\u00E1c nh\u1EADn thanh to\u00E1n", "duy\u1EC7t thanh to\u00E1n"),
                List.of("confirm a payment", "confirm payment", "how can i confirm a payment", "verify payment",
                        "xac nhan thanh toan", "duyet thanh toan"),
                "Payment Confirmation Workflow",
                "Quy tr\u00ECnh x\u00E1c nh\u1EADn thanh to\u00E1n",
                Map.of(
                        "MANAGER", "Payment requests are confirmed by the reception team at Front Desk > Payment requests (/reception/payments). Managers review overall transactions and approve refund requests at /manager/transactions.",
                        "RECEPTIONIST", "As Receptionist, navigate to Front Desk > Payment requests (/reception/payments). Locate the pending request, verify that the cash or VietQR bank transfer has been received with the correct reference code, and click 'Confirm payment'. You can then print or issue the receipt at /reception/payments/:id/receipt.",
                        "COACH", "Payment verification is handled by the reception team. Coaches do not have financial permissions.",
                        "MEMBER", "Payment confirmation is completed by the reception team upon receiving your payment. Track your payment status at Member Workspace > My payments (/member/payments).",
                        "DEFAULT", "Receptionists confirm member payments at Front Desk > Payment requests (/reception/payments)."
                ),
                Map.of(
                        "MANAGER", "L\u1EC5 t\u00E2n x\u00E1c nh\u1EADn thanh to\u00E1n t\u1EA1i /reception/payments. Manager theo d\u00F5i giao d\u1ECBch v\u00E0 duy\u1EC7t ho\u00E0n ti\u1EC1n t\u1EA1i /manager/transactions.",
                        "RECEPTIONIST", "V\u1EDBi vai tr\u00F2 L\u1EC5 t\u00E2n, v\u00E0o Front Desk > Payment requests (/reception/payments). T\u00ECm y\u00EAu c\u1EA7u thanh to\u00E1n ch\u1EDD, ki\u1EC3m tra ti\u1EC1n m\u1EB7t ho\u1EB7c chuy\u1EC3n kho\u1EA3n VietQR \u0111\u00FAng n\u1ED9i dung, r\u1ED3i b\u1EA5m 'Confirm payment'. Sau \u0111\u00F3 c\u00F3 th\u1EC3 in bi\u00EAn lai.",
                        "COACH", "X\u00E1c nh\u1EADn thanh to\u00E1n do L\u1EC5 t\u00E2n ph\u1EE5 tr\u00E1ch.",
                        "MEMBER", "Sau khi b\u1EA1n chuy\u1EC3n kho\u1EA3n ho\u1EB7c tr\u1EA3 ti\u1EC1n, l\u1EC5 t\u00E2n s\u1EBD x\u00E1c nh\u1EADn. B\u1EA1n ki\u1EC3m tra k\u1EBFt qu\u1EA3 t\u1EA1i /member/payments.",
                        "DEFAULT", "L\u1EC5 t\u00E2n x\u00E1c nh\u1EADn thanh to\u00E1n t\u1EA1i /reception/payments."
                ),
                "PAYMENTS",
                "/manager/transactions",
                "/reception/payments",
                "/coach",
                "/member/payments"
        ));

        // 11. ACTIVITY LOG
        topics.add(new TopicGuidance(
                "ACTIVITY_LOG",
                List.of("view activity logs", "view activity log", "how can i view activity logs", "activity log",
                        "audit log", "audit trail", "audit logs", "xem activity log", "nh\u1EADt k\u00FD ho\u1EA1t \u0111\u1ED9ng", "l\u1ECBch s\u1EED thao t\u00E1c"),
                List.of("view activity logs", "view activity log", "activity log", "audit log", "audit trail",
                        "xem activity log", "nhat ky hoat dong", "lich su thao tac"),
                "Activity Log & Audit Trail",
                "Nh\u1EADt k\u00FD ho\u1EA1t \u0111\u1ED9ng v\u00E0 ki\u1EC3m to\u00E1n",
                Map.of(
                        "MANAGER", "As Manager, navigate to Manager Workspace > Activity log (/manager/activity). You can search activity records by actor, action, or target ID, filter by date range, and view detailed audit records. Click 'View details' on any row to inspect complete event evidence, including actor information, request parameters, result status, error details, and verified reason.",
                        "RECEPTIONIST", "The Activity Log is an administrative audit trail restricted to Managers only at /manager/activity.",
                        "COACH", "The Activity Log is accessible only to Managers at /manager/activity.",
                        "MEMBER", "The Activity Log is an administrative feature restricted to Managers.",
                        "DEFAULT", "The center Activity Log is accessible exclusively to Managers at Manager Workspace > Activity log (/manager/activity)."
                ),
                Map.of(
                        "MANAGER", "V\u1EDBi vai tr\u00F2 Manager, v\u00E0o Manager Workspace > Activity log (/manager/activity). B\u1EA1n c\u00F3 th\u1EC3 t\u00ECm ki\u1EBFm theo ng\u01B0\u1EDDi th\u1EF1c hi\u1EC7n, h\u00E0nh \u0111\u1ED9ng, kho\u1EA3ng th\u1EDDi gian v\u00E0 b\u1EA5m 'View details' \u0111\u1EC3 xem chi ti\u1EBFt s\u1EF1 ki\u1EC7n, th\u00F4ng s\u1ED1 g\u1EEDi l\u00EAn, k\u1EBFt qu\u1EA3 v\u00E0 l\u00FD do x\u00E1c th\u1EF1c.",
                        "RECEPTIONIST", "Activity Log l\u00E0 nh\u1EADt k\u00FD ki\u1EC3m to\u00E1n d\u00E0nh ri\u00EAng cho Manager t\u1EA1i /manager/activity.",
                        "COACH", "Activity Log ch\u1EC9 d\u00E0nh cho Manager t\u1EA1i /manager/activity.",
                        "MEMBER", "Activity Log thu\u1ED9c quy\u1EC1n qu\u1EA3n tr\u1ECB c\u1EE7a Manager.",
                        "DEFAULT", "Manager xem nh\u1EADt k\u00FD ho\u1EA1t \u0111\u1ED9ng t\u1EA1i /manager/activity."
                ),
                "ACTIVITY_LOG",
                "/manager/activity",
                "/reception",
                "/coach",
                "/member"
        ));

        // 12. COACH SCHEDULE & ASSIGNED SESSIONS
        topics.add(new TopicGuidance(
                "COACH_SCHEDULE",
                List.of("view my schedule", "view assigned sessions", "how can i view my schedule", "how can i view assigned sessions",
                        "coach schedule", "teaching schedule", "l\u1ECBch d\u1EA1y", "ca d\u1EA1y c\u1EE7a t\u00F4i", "bu\u1ED5i d\u1EA1y"),
                List.of("view my schedule", "view assigned sessions", "coach schedule", "teaching schedule",
                        "lich day", "ca day cua toi", "buoi day"),
                "Coach Schedule & Sessions",
                "L\u1ECBch d\u1EA1y v\u00E0 bu\u1ED5i h\u1ECDc c\u1EE7a Hu\u1EA5n luy\u1EC7n vi\u00EAn",
                Map.of(
                        "COACH", "As Coach, navigate to Coach Workspace > Teaching schedule (/coach) to see your daily and weekly assigned sessions. You can also open Class roster (/coach/roster) to inspect the confirmed learner roster for each upcoming session.",
                        "MANAGER", "Managers schedule and assign coaches to class sessions at Manager Workspace > Schedule and coaches (/manager/schedule).",
                        "RECEPTIONIST", "Receptionists can review class sessions and assist member bookings at /reception/bookings.",
                        "MEMBER", "Members can review their booked class sessions at Member Workspace > My bookings (/member/bookings) and find new classes at /member/schedules.",
                        "DEFAULT", "Coaches check their assigned sessions at Coach Workspace > Teaching schedule (/coach)."
                ),
                Map.of(
                        "COACH", "V\u1EDBi vai tr\u00F2 Coach, v\u00E0o Coach Workspace > Teaching schedule (/coach) \u0111\u1EC3 xem l\u1ECBch d\u1EA1y theo tu\u1EA7n v\u00E0 ng\u00E0y. M\u1EDF Class roster (/coach/roster) \u0111\u1EC3 xem danh s\u00E1ch h\u1ECDc vi\u00EAn \u0111\u00E3 \u0111\u1EB7t ch\u1ED7.",
                        "MANAGER", "Manager ph\u00E2n c\u00F4ng Coach v\u00E0 x\u1EBFp l\u1ECBch t\u1EA1i /manager/schedule.",
                        "RECEPTIONIST", "L\u1EC5 t\u00E2n xem l\u1ECBch session t\u1EA1i /reception/bookings.",
                        "MEMBER", "H\u1ED9i vi\u00EAn xem c\u00E1c bu\u1ED5i h\u1ECDc \u0111\u00E3 \u0111\u1EB7t t\u1EA1i /member/bookings.",
                        "DEFAULT", "Coach xem l\u1ECBch d\u1EA1y t\u1EA1i /coach v\u00E0 danh s\u00E1ch h\u1ECDc vi\u00EAn t\u1EA1i /coach/roster."
                ),
                "COACH_SCHEDULE",
                "/manager/schedule",
                "/reception/bookings",
                "/coach",
                "/member/bookings"
        ));

        // 13. COACH ATTENDANCE & STATUSES
        topics.add(new TopicGuidance(
                "COACH_ATTENDANCE",
                List.of("mark attendance", "how can i mark attendance", "attendance statuses", "what attendance statuses are available",
                        "attendance status", "\u0111i\u1EC3m danh h\u1ECDc vi\u00EAn", "tr\u1EA1ng th\u00E1i \u0111i\u1EC3m danh", "c\u00E1ch \u0111i\u1EC3m danh"),
                List.of("mark attendance", "how can i mark attendance", "attendance statuses", "what attendance statuses are available",
                        "attendance status", "diem danh hoc vien", "trang thai diem danh", "cach diem danh"),
                "Coach Session Attendance",
                "\u0110i\u1EC3m danh bu\u1ED5i h\u1ECDc c\u1EE7a Coach",
                Map.of(
                        "COACH", "As Coach, navigate to Coach Workspace > Session attendance (/coach/training/attendance). Select your assigned session to view learner bookings. For each member, select one of the available statuses: PRESENT (attended), ABSENT (did not attend), or EXCUSED (excused absence). Click Save to record the attendance.",
                        "MANAGER", "Managers monitor center attendance at /manager/training/attendance and can perform missed attendance backfill at /manager/training/attendance/backfill.",
                        "RECEPTIONIST", "Receptionists record front-desk arrival check-in at /reception/check-in.",
                        "MEMBER", "Your attendance is marked by your Coach during session. View your attendance history at Member Workspace > Training progress (/member/training).",
                        "DEFAULT", "Coaches mark session attendance at Coach Workspace > Session attendance (/coach/training/attendance)."
                ),
                Map.of(
                        "COACH", "V\u1EDBi vai tr\u00F2 Coach, v\u00E0o Coach Workspace > Session attendance (/coach/training/attendance). Ch\u1ECDn bu\u1ED5i d\u1EA1y \u0111\u01B0\u1EE3c ph\u00E2n c\u00F4ng, ch\u1ECDn tr\u1EA1ng th\u00E1i cho t\u1EEBng h\u1ECDc vi\u00EAn: PRESENT (C\u00F3 m\u1EB7t), ABSENT (V\u1EAFng m\u1EB7t), ho\u1EB7c EXCUSED (Ngh\u1EC9 c\u00F3 ph\u00E9p). B\u1EA5m Save \u0111\u1EC3 l\u01B0u.",
                        "MANAGER", "Manager theo d\u00F5i \u0111i\u1EC3m danh t\u1EA1i /manager/training/attendance.",
                        "RECEPTIONIST", "L\u1EC5 t\u00E2n ghi nh\u1EADn check-in \u0111\u1EBFn trung t\u00E2m t\u1EA1i /reception/check-in.",
                        "MEMBER", "Coach \u0111i\u1EC3m danh trong bu\u1ED5i h\u1ECDc. B\u1EA1n xem l\u1ECBch s\u1EED t\u1EA1i /member/training.",
                        "DEFAULT", "Coach \u0111i\u1EC3m danh bu\u1ED5i h\u1ECDc t\u1EA1i /coach/training/attendance."
                ),
                "ATTENDANCE",
                "/manager/training/attendance",
                "/reception/check-in",
                "/coach/training/attendance",
                "/member/training"
        ));

        // 14. AI TRAINING DRILLS
        topics.add(new TopicGuidance(
                "AI_DRILLS",
                List.of("training drills", "generate training drills", "how can i generate training drills",
                        "ai drills", "ai-generated drills", "generate drills",
                        "b\u00E0i t\u1EADp ai", "t\u1EA1o b\u00E0i t\u1EADp", "gi\u00E1o \u00E1n ai"),
                List.of("training drills", "generate training drills", "how can i generate training drills",
                        "ai drills", "ai-generated drills", "generate drills",
                        "bai tap ai", "tao bai tap", "giao an ai"),
                "AI-Generated Training Drills",
                "T\u1EA1o b\u00E0i t\u1EADp v\u00E0 gi\u00E1o \u00E1n b\u1EB1ng AI",
                Map.of(
                        "COACH", "As Coach, navigate to Coach Workspace > Plans and feedback (/coach/training/plans). You can generate AI training drills customized by sport, level, and member goals. You can review the draft, adjust exercises, and assign the personalized plan to learners.",
                        "MEMBER", "You can view assigned training plans and AI-generated drills at Member Workspace > Training progress (/member/training). You can also set personal fitness goals at /member/training/goals to receive tailored recommendations.",
                        "MANAGER", "Managers oversee class programs and training progress across the center.",
                        "RECEPTIONIST", "Training drills are assigned by Coaches and followed by Members in their training workspaces.",
                        "DEFAULT", "Coaches generate and assign AI training drills at Coach Workspace > Plans and feedback (/coach/training/plans)."
                ),
                Map.of(
                        "COACH", "V\u1EDBi vai tr\u00F2 Coach, v\u00E0o Coach Workspace > Plans and feedback (/coach/training/plans). B\u1EA1n c\u00F3 th\u1EC3 t\u1EA1o gi\u00E1o \u00E1n b\u00E0i t\u1EADp AI ph\u00F9 h\u1EE3p v\u1EDBi m\u00F4n v\u00E0 tr\u00ECnh \u0111\u1ED9 h\u1ECDc vi\u00EAn, ch\u1EC9nh s\u1EEDa r\u1ED3i g\u00E1n cho h\u1ECDc vi\u00EAn.",
                        "MEMBER", "H\u1ED9i vi\u00EAn xem b\u00E0i t\u1EADp \u0111\u01B0\u1EE3c Coach g\u00E1n t\u1EA1i /member/training v\u00E0 \u0111\u1EB7t m\u1EE5c ti\u00EAu t\u1EADp luy\u1EC7n t\u1EA1i /member/training/goals.",
                        "MANAGER", "Manager theo d\u00F5i ch\u01B0\u01A1ng tr\u00ECnh \u0111\u00E0o t\u1EA1o to\u00E0n trung t\u00E2m.",
                        "RECEPTIONIST", "K\u1EBF ho\u1EA1ch t\u1EADp do Coach v\u00E0 H\u1ED9i vi\u00EAn qu\u1EA3n l\u00FD trong khu v\u1EF1c Training.",
                        "DEFAULT", "Coach t\u1EA1o v\u00E0 g\u00E1n b\u00E0i t\u1EADp AI t\u1EA1i /coach/training/plans."
                ),
                "TRAINING_PLANS",
                "/manager",
                "/reception",
                "/coach/training/plans",
                "/member/training"
        ));

        // 15. SUPPORT REQUESTS & RECEPTION CONTACT
        topics.add(new TopicGuidance(
                "SUPPORT_REQUESTS",
                List.of("respond to a support request", "how can i respond to a support request",
                        "support request", "support requests", "create support request",
                        "tr\u1EA3 l\u1EDDi y\u00EAu c\u1EA7u h\u1ED7 tr\u1EE3", "y\u00EAu c\u1EA7u h\u1ED7 tr\u1EE3", "t\u1EA1o y\u00EAu c\u1EA7u h\u1ED7 tr\u1EE3"),
                List.of("respond to a support request", "how can i respond to a support request",
                        "support request", "support requests", "create support request",
                        "tra loi yeu cau ho tro", "yeu cau ho tro", "tao yeu cau ho tro"),
                "Support Requests & Reception Assistance",
                "Y\u00EAu c\u1EA7u h\u1ED7 tr\u1EE3 v\u00E0 li\u00EAn h\u1EC7 L\u1EC5 t\u00E2n",
                Map.of(
                        "RECEPTIONIST", "As Receptionist, navigate to Front Desk > Support requests (/reception/support). Click any open inquiry submitted by a member, review the details, enter your response, and submit. Once answered, you can mark the support ticket resolved.",
                        "MEMBER", "As a Member, navigate to Member Workspace > Support (/member/support). Click 'New support request', select your topic, enter your question, and submit. The reception team will review and reply; you can follow replies directly in your support requests.",
                        "COACH", "Coaches answer learner questions regarding training at Coach Workspace > Member questions (/coach/training/questions). General support is handled by Receptionists at /reception/support.",
                        "MANAGER", "Support requests are managed by Receptionists at /reception/support.",
                        "DEFAULT", "Members contact reception at /member/support. Receptionists manage and reply to requests at /reception/support."
                ),
                Map.of(
                        "RECEPTIONIST", "V\u1EDBi vai tr\u00F2 L\u1EC5 t\u00E2n, v\u00E0o Front Desk > Support requests (/reception/support). M\u1EDF y\u00EAu c\u1EA7u c\u1EE7a h\u1ED9i vi\u00EAn, nh\u1EADp n\u1ED9i dung tr\u1EA3 l\u1EDDi v\u00E0 g\u1EEDi. Sau khi gi\u1EA3i quy\u1EBFt, chuy\u1EC3n tr\u1EA1ng th\u00E1i th\u00E0nh Resolved.",
                        "MEMBER", "V\u1EDBi vai tr\u00F2 H\u1ED9i vi\u00EAn, v\u00E0o Member Workspace > Support (/member/support). B\u1EA5m t\u1EA1o y\u00EAu c\u1EA7u, ch\u1ECDn ch\u1EE7 \u0111\u1EC1, nh\u1EADp c\u00E2u h\u1ECFi v\u00E0 g\u1EEDi. L\u1EC5 t\u00E2n s\u1EBD ph\u1EA3n h\u1ED3i l\u1EA1i tr\u1EF1c ti\u1EBFp cho b\u1EA1n.",
                        "COACH", "Coach tr\u1EA3 l\u1EDDi th\u1EAFc m\u1EAFc t\u1EADp luy\u1EC7n t\u1EA1i /coach/training/questions. C\u00E2u h\u1ECFi h\u1ED7 tr\u1EE3 chung do L\u1EC5 t\u00E2n x\u1EED l\u00FD t\u1EA1i /reception/support.",
                        "MANAGER", "Y\u00EAu c\u1EA7u h\u1ED7 tr\u1EE3 do L\u1EC5 t\u00E2n ti\u1EBFp nh\u1EADn t\u1EA1i /reception/support.",
                        "DEFAULT", "H\u1ED9i vi\u00EAn g\u1EEDi y\u00EAu c\u1EA7u h\u1ED7 tr\u1EE3 t\u1EA1i /member/support. L\u1EC5 t\u00E2n tr\u1EA3 l\u1EDDi t\u1EA1i /reception/support."
                ),
                "SUPPORT",
                "/manager",
                "/reception/support",
                "/coach/training/questions",
                "/member/support"
        ));

        // 16. COACH PROFILE & CERTIFICATIONS
        topics.add(new TopicGuidance(
                "COACH_PROFILE",
                List.of("coach profile", "customize my profile", "update my profile", "my certifications",
                        "add certification", "coach certification", "update bio", "update headline",
                        "h\u1ED3 s\u01A1 coach", "h\u1ED3 s\u01A1 hu\u1EA5n luy\u1EC7n vi\u00EAn", "ch\u1EE9ng ch\u1EC9 coach", "th\u00EAm ch\u1EE9ng ch\u1EC9"),
                List.of("coach profile", "customize my profile", "my certifications", "add certification",
                        "coach certification", "ho so coach", "ho so huan luyen vien", "chung chi coach", "them chung chi"),
                "Coach Profile & Certifications",
                "H\u1ED3 s\u01A1 v\u00E0 ch\u1EE9ng ch\u1EC9 Hu\u1EA5n luy\u1EC7n vi\u00EAn",
                Map.of(
                        "COACH", "As Coach, follow these steps to update your public coaching profile (Spori only guides and does not edit profiles for you):\n1. Open [My Profile](/coach/profile).\n2. Update your headline, years of coaching, specialties, biography, or upload a photo, then click 'Save profile'.\n3. Under 'Certifications & qualifications', choose a certification from the center catalog dropdown and click 'Add certification'.",
                        "MANAGER", "Managers can review and manage coach accounts and public visibility in [User Management](/manager). Coaches can also customize their own bio and certifications at /coach/profile.",
                        "RECEPTIONIST", "Coach profiles are managed by Managers at /manager and customized by Coaches at /coach/profile.",
                        "MEMBER", "You can view a Coach's public profile and certifications by clicking 'View coach profile' on any coach-led session in [Schedules & Classes](/member/schedules). To update your own member profile, open [My Profile](/member/profile).",
                        "DEFAULT", "Coaches customize their public profile and certifications at [My Profile](/coach/profile)."
                ),
                Map.of(
                        "COACH", "V\u1EDBi vai tr\u00F2 Coach, l\u00E0m theo c\u00E1c b\u01B0\u1EDBc sau \u0111\u1EC3 c\u1EADp nh\u1EADt h\u1ED3 s\u01A1 c\u00F4ng khai (Spori ch\u1EC9 h\u01B0\u1EDBng d\u1EABn v\u00E0 kh\u00F4ng t\u1EF1 ch\u1EC9nh s\u1EEDa h\u1ED3 s\u01A1 thay b\u1EA1n):\n1. M\u1EDF [My Profile](/coach/profile).\n2. Ch\u1EC9nh s\u1EEDa headline, s\u1ED1 n\u0103m kinh nghi\u1EC7m, chuy\u00EAn m\u00F4n, ti\u1EC3u s\u1EED ho\u1EB7c t\u1EA3i \u1EA3nh r\u1ED3i b\u1EA5m 'Save profile'.\n3. Trong m\u1EE5c 'Certifications & qualifications', ch\u1ECDn ch\u1EE9ng ch\u1EC9 t\u1EEB danh m\u1EE5c trung t\u00E2m v\u00E0 b\u1EA5m 'Add certification'.",
                        "MANAGER", "Manager qu\u1EA3n l\u00FD t\u00E0i kho\u1EA3n v\u00E0 hi\u1EC3n th\u1ECB h\u1ED3 s\u01A1 Coach t\u1EA1i [User Management](/manager). Coach c\u0169ng t\u1EF1 c\u1EADp nh\u1EADt h\u1ED3 s\u01A1 t\u1EA1i /coach/profile.",
                        "RECEPTIONIST", "H\u1ED3 s\u01A1 Coach do Manager qu\u1EA3n l\u00FD t\u1EA1i /manager v\u00E0 Coach t\u1EF1 t\u00F9y ch\u1EC9nh t\u1EA1i /coach/profile.",
                        "MEMBER", "B\u1EA1n c\u00F3 th\u1EC3 xem h\u1ED3 s\u01A1 Coach khi m\u1EDF chi ti\u1EBFt bu\u1ED5i h\u1ECDc t\u1EA1i [Schedules & Classes](/member/schedules), ho\u1EB7c c\u1EADp nh\u1EADt h\u1ED3 s\u01A1 c\u00E1 nh\u00E2n t\u1EA1i [My Profile](/member/profile).",
                        "DEFAULT", "Coach t\u1EF1 c\u1EADp nh\u1EADt h\u1ED3 s\u01A1 c\u00F4ng khai v\u00E0 ch\u1EE9ng ch\u1EC9 t\u1EA1i [My Profile](/coach/profile)."
                ),
                "COACH_PROFILE",
                "/manager",
                "/reception",
                "/coach/profile",
                "/member/profile"
        ));

        for (TopicGuidance t : topics) {
            BY_CODE.put(t.topicCode(), t);
        }
    }
}
