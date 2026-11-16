package com.sportify.catalog.controller;

import com.sportify.catalog.service.ClassNoticeService;
import com.sportify.core.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReleaseApiValidationTest {
    @Test void missingRequiredOperationHeaderIsAClientError() throws Exception {
        var mvc=MockMvcBuilders.standaloneSetup(new ClassNoticeController(mock(ClassNoticeService.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/v1/coach/training/notices").contentType("application/json")
                .content("{\"classId\":1,\"type\":\"ANNOUNCEMENT\",\"title\":\"Reminder\",\"content\":\"Bring equipment\"}"))
            .andExpect(status().isBadRequest());
    }
}
