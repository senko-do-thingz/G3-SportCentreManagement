package com.sportify.catalog.controller;

import com.sportify.catalog.service.AttendanceBackfillService;
import com.sportify.catalog.dto.TrainingWriteResponse.Created;
import com.sportify.core.exception.GlobalExceptionHandler;
import com.sportify.identity.entity.*;
import com.sportify.identity.security.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ManagerTrainingController.class)
@Import({SecurityConfig.class,CustomAuthenticationEntryPoint.class,CustomAccessDeniedHandler.class,JwtAuthenticationFilter.class,GlobalExceptionHandler.class})
class ManagerTrainingControllerTest {
    static final String PATH="/api/v1/manager/training/attendance/backfill";
    static final String BODY="{\"bookingId\":1,\"status\":\"PRESENT\",\"reason\":\"Verified missed roll call\"}";
    @Autowired MockMvc mvc;
    @MockitoBean AttendanceBackfillService service;
    @MockitoBean JwtService jwtService;
    @MockitoBean AuthenticationProvider authenticationProvider;
    @MockitoBean UserDetailsService userDetailsService;
    CustomUserDetails account(String role) {return new CustomUserDetails(UserAccount.builder().id(10L).email("fixture@test.invalid").role(Role.builder().code(role).build()).build());}
    @Test void deniesAnonymousOtherRolesAndDisabledManager() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
        for(String role:List.of("MEMBER","COACH","RECEPTIONIST")) {
            mvc.perform(get(PATH).with(user(account(role)))).andExpect(status().isForbidden());
            mvc.perform(post(PATH).with(user(account(role))).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
        }
        var disabled=account("MANAGER");disabled.getUserAccount().setStatus("INACTIVE");
        mvc.perform(post(PATH).with(user(disabled)).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
        mvc.perform(get(PATH).with(user(disabled))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void enabledManagerUsesAdditiveContractAndTrustedActorId() throws Exception {
        when(service.backfill(eq(10L),any())).thenReturn(new Created(55));
        mvc.perform(post(PATH).with(user(account("MANAGER"))).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(55));
        when(service.workspace(10L)).thenReturn(new AttendanceBackfillService.Workspace(false,null,"Disabled",List.of()));
        mvc.perform(get(PATH).with(user(account("MANAGER")))).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
    }
    @Test void rejectsInvalidDtoBeforeService() throws Exception {
        for(String body:List.of("{}", BODY.replace("PRESENT","UNKNOWN"), BODY.replace("\"bookingId\":1","\"bookingId\":0"),
            BODY.replace("Verified missed roll call"," "),BODY.replace("Verified missed roll call","x".repeat(501))))
            mvc.perform(post(PATH).with(user(account("MANAGER"))).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
