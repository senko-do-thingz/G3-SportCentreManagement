package com.sportify.catalog.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Service integrating with Google Gemini API for the Spori AI chatbot.
 */
@Service
@Slf4j
public class SporiAiService {

    public static final String DEFAULT_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/";
    public static final String DEFAULT_MODEL = "gemini-flash-lite-latest";
    public static final String FALLBACK_MODEL = "gemini-flash-lite-latest";
    public static final double TEMPERATURE = 0.1;

    public static final String MANDATORY_OUT_OF_SCOPE_REPLY =
            "Xin lỗi, câu hỏi của bạn không nằm trong phạm vi hỗ trợ của tôi. Vui lòng truy cập [Trang hỗ trợ](http://localhost:5173/member/support) để mở ticket gửi đến bộ phận lễ tân, chúng tôi sẽ giải đáp chi tiết cho bạn.";

    public static final String SYSTEM_INSTRUCTION = """
            Bạn là Trợ lý AI Hỗ trợ Người dùng của hệ thống quản lý trung tâm thể thao G3-SportCentreManagement (Spori AI). Nhiệm vụ duy nhất của bạn là hướng dẫn và hỗ trợ người dùng cuối.
            DANH TÍNH (IDENTITY): Khi người dùng hỏi bạn có phải là Gemini không (hoặc câu hỏi tương tự về danh tính Gemini): Bạn PHẢI trả lời rõ: "Tôi không phải là Gemini, tôi là Spori AI và chỉ được tích hợp API từ Gemini thôi." (nếu hỏi bằng tiếng Anh thì trả lời tương ứng: "I am not Gemini, I am Spori AI and only integrated with Gemini API.").
            NGUYÊN TẮC BẢO MẬT: KHÔNG BAO GIỜ cung cấp, giải thích, hoặc rò rỉ mã nguồn (source code), kiến trúc hệ thống, cơ sở dữ liệu.
            PHẠM VI NGHIỆP VỤ:\s
            - Member: Đặt sân, xem lịch tập, quản lý gói hội viên.
            - Receptionist: Check-in/out, thanh toán POS, tạo tài khoản, xử lý ticket.
            - Coach: Xem lịch dạy, danh sách học viên.
            - Manager: Báo cáo doanh thu, quản lý nhân sự, thiết lập giá sân.
            QUY TẮC TỪ CHỐI (OUT OF SCOPE): Từ chối BẤT KỲ chủ đề nào ngoài lề (ví dụ: tìm quán ăn, hỏi thời tiết, nhờ viết code...).
            CÂU TỪ CHỐI BẮT BUỘC: "Xin lỗi, câu hỏi của bạn không nằm trong phạm vi hỗ trợ của tôi. Vui lòng truy cập [Trang hỗ trợ](http://localhost:5173/member/support) để mở ticket gửi đến bộ phận lễ tân, chúng tôi sẽ giải đáp chi tiết cho bạn." (Giữ nguyên định dạng link này dù trả lời bằng ngôn ngữ nào).
            NGÔN NGỮ: Hỏi bằng ngôn ngữ nào, trả lời bằng ngôn ngữ đó.""";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final String endpoint;

    @Autowired
    public SporiAiService(
            @Value("${GEMINI_API_KEY:}") String apiKey,
            @Value("${app.spori-ai.gemini-model:gemini-1.5-flash}") String model,
            @Value("${app.spori-ai.endpoint:https://generativelanguage.googleapis.com/v1beta/models/}") String endpoint,
            @Value("${app.spori-ai.timeout-seconds:20}") int timeoutSeconds,
            ObjectMapper objectMapper,
            @Autowired(required = false) RestTemplate restTemplate) {

        this.apiKey = sanitizeApiKey(apiKey);
        this.model = (model == null || model.isBlank()) ? DEFAULT_MODEL : model.trim();
        this.endpoint = (endpoint == null || endpoint.isBlank()) ? DEFAULT_ENDPOINT : endpoint.trim();
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.restTemplate = (restTemplate != null) ? restTemplate : createDefaultRestTemplate(timeoutSeconds);
    }

    /**
     * Testing constructor.
     */
    public SporiAiService(RestTemplate restTemplate, ObjectMapper objectMapper, String apiKey, String model, String endpoint) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.apiKey = sanitizeApiKey(apiKey);
        this.model = (model == null || model.isBlank()) ? DEFAULT_MODEL : model.trim();
        this.endpoint = (endpoint == null || endpoint.isBlank()) ? DEFAULT_ENDPOINT : endpoint.trim();
    }

    public String getStatus() {
        if (apiKey.isEmpty()) {
            return "Gemini API: DISABLED. GEMINI_API_KEY is not configured in environment or .env file.";
        }
        return "Gemini API: CONNECTED. Spori AI is active using model: " + model;
    }

    /**
     * Sends a chat prompt to Gemini API with user role context and returns the assistant's reply.
     *
     * @param userMessage the raw message sent by the user
     * @param role        the authenticated user role (e.g., MEMBER, MANAGER, RECEPTIONIST, COACH)
     * @return the text response from Spori AI
     */
    public String chat(String userMessage, String role) {
        return chatWithHistory(userMessage, role, List.of());
    }

    /**
     * Sends a chat prompt along with conversation history to Gemini API.
     *
     * @param userMessage raw message from the user
     * @param role        authenticated user role
     * @param history     prior conversation turns (oldest first)
     * @return reply text from Gemini
     */
    public String chatWithHistory(String userMessage, String role, List<com.sportify.catalog.entity.AiMessage> history) {
        if (userMessage == null || userMessage.isBlank()) {
            throw new IllegalArgumentException("User message must not be blank");
        }

        if (apiKey.isEmpty()) {
            log.error("GEMINI_API_KEY is not configured in environment or .env file");
            throw new IllegalStateException("GEMINI_API_KEY is not configured");
        }

        String normalizedRole = normalizeRole(role);
        java.util.ArrayList<Map<String, Object>> contents = new java.util.ArrayList<>();

        if (history != null && !history.isEmpty()) {
            for (com.sportify.catalog.entity.AiMessage msg : history) {
                if ("USER".equalsIgnoreCase(msg.getSenderType())) {
                    contents.add(Map.of(
                            "role", "user",
                            "parts", List.of(Map.of("text", String.format("[User Role: %s] %s", normalizedRole, msg.getContent())))
                    ));
                } else if ("ASSISTANT".equalsIgnoreCase(msg.getSenderType())) {
                    contents.add(Map.of(
                            "role", "model",
                            "parts", List.of(Map.of("text", msg.getContent()))
                    ));
                }
            }
        }

        String hiddenPrompt = String.format("[User Role: %s] %s", normalizedRole, userMessage.trim());
        contents.add(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", hiddenPrompt))
        ));

        Map<String, Object> payload = Map.of(
                "systemInstruction", Map.of(
                        "parts", List.of(Map.of("text", SYSTEM_INSTRUCTION))
                ),
                "contents", contents,
                "generationConfig", Map.of(
                        "temperature", TEMPERATURE
                )
        );
        String targetUrl = endpoint + model + ":generateContent";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", apiKey);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(targetUrl, requestEntity, String.class);
            if (response == null || !response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new IllegalStateException("Gemini API call failed with status: " + (response != null ? response.getStatusCode() : "null"));
            }
            return extractTextFromResponse(response.getBody());
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() == 404 && !FALLBACK_MODEL.equals(model)) {
                log.warn("Gemini model {} returned 404 Not Found, retrying with fallback model {}", model, FALLBACK_MODEL);
                try {
                    String fallbackUrl = endpoint + FALLBACK_MODEL + ":generateContent";
                    ResponseEntity<String> retryResponse = restTemplate.postForEntity(fallbackUrl, requestEntity, String.class);
                    if (retryResponse != null && retryResponse.getStatusCode().is2xxSuccessful() && retryResponse.getBody() != null) {
                        return extractTextFromResponse(retryResponse.getBody());
                    }
                } catch (Exception retryEx) {
                    log.error("Fallback to model {} also failed: {}", FALLBACK_MODEL, retryEx.getMessage());
                }
            }
            log.error("Gemini API HTTP error: status={}, body={}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new IllegalStateException("Gemini API request failed with status: " + ex.getStatusCode(), ex);
        } catch (RestClientException ex) {
            log.error("Gemini API communication error: {}", ex.getMessage());
            throw new IllegalStateException("Failed to communicate with Gemini API", ex);
        }
    }

    /**
     * Builds payload according to Gemini generateContent API specification.
     *
     * @param promptWithRole the user prompt prefixed with hidden role indicator
     * @return the structured payload map
     */
    public Map<String, Object> buildGeminiPayload(String promptWithRole) {
        return Map.of(
                "systemInstruction", Map.of(
                        "parts", List.of(Map.of("text", SYSTEM_INSTRUCTION))
                ),
                "contents", List.of(
                        Map.of(
                                "role", "user",
                                "parts", List.of(Map.of("text", promptWithRole))
                        )
                ),
                "generationConfig", Map.of(
                        "temperature", TEMPERATURE
                )
        );
    }

    private String extractTextFromResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            JsonNode promptFeedback = root.path("promptFeedback");
            if (promptFeedback.hasNonNull("blockReason")) {
                String blockReason = promptFeedback.path("blockReason").asText();
                log.warn("Gemini prompt was blocked with reason: {}", blockReason);
                return MANDATORY_OUT_OF_SCOPE_REPLY;
            }

            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                throw new IllegalStateException("No candidate returned by Gemini API");
            }

            JsonNode candidate = candidates.get(0);
            JsonNode parts = candidate.path("content").path("parts");
            if (!parts.isArray() || parts.isEmpty()) {
                throw new IllegalStateException("No content parts in Gemini API candidate");
            }

            StringBuilder replyBuilder = new StringBuilder();
            for (JsonNode part : parts) {
                if (!part.path("thought").asBoolean(false)) {
                    replyBuilder.append(part.path("text").asText(""));
                }
            }

            String reply = replyBuilder.toString().strip();
            if (reply.isEmpty()) {
                throw new IllegalStateException("Empty reply returned by Gemini API");
            }
            return reply;
        } catch (JsonProcessingException e) {
            log.error("Failed to parse Gemini API response JSON: {}", responseBody, e);
            throw new IllegalStateException("Failed to parse Gemini API response", e);
        }
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return "MEMBER";
        }
        String clean = role.trim().toUpperCase(Locale.ROOT);
        if (clean.startsWith("ROLE_")) {
            clean = clean.substring(5);
        }
        return clean;
    }

    private static String sanitizeApiKey(String rawKey) {
        if (rawKey == null) {
            return "";
        }
        return rawKey.replace("\"", "").replace("'", "").trim();
    }

    private static RestTemplate createDefaultRestTemplate(int timeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(Math.min(10, timeoutSeconds)).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(timeoutSeconds).toMillis());
        return new RestTemplate(factory);
    }
}
