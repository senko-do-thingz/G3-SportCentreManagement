package com.sportify.catalog.controller;

import com.sportify.catalog.service.ClassNoticeService;
import com.sportify.core.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReleaseApiValidationTest {
    @Test void credentialDebugStringsNeverRevealThePass() {
        String token="SCIN1."+"A".repeat(43);
        var pass=new com.sportify.catalog.service.MemberCheckInPassService.Pass("MEM-TEST",token,java.time.Instant.EPOCH.plusSeconds(120),java.time.Instant.EPOCH);
        org.assertj.core.api.Assertions.assertThat(pass.toString()).doesNotContain(token).doesNotContain("MEM-TEST");
        org.assertj.core.api.Assertions.assertThat(new MemberCheckInPassController.Redeem(token,1L).toString()).doesNotContain(token);
    }
    @Test void missingRequiredOperationHeaderIsAClientError() throws Exception {
        var mvc=MockMvcBuilders.standaloneSetup(new ClassNoticeController(mock(ClassNoticeService.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/v1/coach/training/notices").contentType("application/json")
                .content("{\"classId\":1,\"type\":\"ANNOUNCEMENT\",\"title\":\"Reminder\",\"content\":\"Bring equipment\"}"))
            .andExpect(status().isBadRequest());
    }
}
