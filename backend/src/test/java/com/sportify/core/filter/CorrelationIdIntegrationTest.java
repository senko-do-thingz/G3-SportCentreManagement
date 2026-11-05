package com.sportify.core.filter;

import com.sportify.AbstractIntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class CorrelationIdIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publicEndpoint_shouldEchoValidCorrelationId() throws Exception {
        mockMvc.perform(get("/api/v1/sports")
                        .header("X-Correlation-Id", "test-trace-id-1001"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "test-trace-id-1001"));
    }

    @Test
    void publicEndpoint_shouldGenerateCorrelationIdWhenMissing() throws Exception {
        mockMvc.perform(get("/api/v1/sports"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-Id"));
    }

    @Test
    void unauthorizedEndpoint_shouldIncludeCorrelationIdIn401Response() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header("X-Correlation-Id", "auth-fail-trace-2002"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Correlation-Id", "auth-fail-trace-2002"));
    }

    @Test
    void corsPreflight_shouldExposeAndAllowCorrelationId() throws Exception {
        mockMvc.perform(options("/api/v1/sports")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-Correlation-Id,Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("X-Correlation-Id")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, containsString("X-Correlation-Id")))
                .andExpect(header().exists("X-Correlation-Id"));
    }
}
