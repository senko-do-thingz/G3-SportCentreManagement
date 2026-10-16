package com.sportify.identity.controller;
import com.sportify.identity.dto.*;
import com.sportify.identity.entity.*;
import com.sportify.identity.repository.ActivityLogRepository;
import com.sportify.identity.security.*;
import com.sportify.identity.service.ActivityLogService;
import com.sportify.identity.service.impl.ActivityLogServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.*;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(ActivityLogController.class)
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@Import({SecurityConfig.class,CustomAuthenticationEntryPoint.class,CustomAccessDeniedHandler.class,JwtAuthenticationFilter.class,ActivityLogServiceImpl.class})
class ActivityLogControllerTest {
    static final String URL="/api/v1/manager/activity-logs";
    @Autowired MockMvc mvc;
    @Autowired ActivityLogService service;
    @MockitoBean ActivityLogRepository logs;
    @MockitoBean JwtService jwt;
    @MockitoBean UserDetailsService users;
    @MockitoBean AuthenticationProvider authenticationProvider;
    void token(String role,String status,String type) {
        var p=new CustomUserDetails(UserAccount.builder().id(17L).email("be04@example.test").fullName("Audit Manager")
                .role(Role.builder().code(role).build()).status(status).build());
        when(jwt.extractUsername("fixture")).thenReturn("be04@example.test"); when(jwt.extractTokenType("fixture")).thenReturn(type);
        when(users.loadUserByUsername("be04@example.test")).thenReturn(p); when(jwt.isTokenValid("fixture",p)).thenReturn(true);
    }
    void manager(){token("MANAGER","ACTIVE","access");}
    @Test void managerDefaultsAndJson() throws Exception {
        manager(); var row=new ActivityLogResponse(91L,LocalDateTime.of(2026,10,16,9,0),17L,"Audit Manager","MANAGER","PASSWORD_CHANGED","USER_ACCOUNT",17L);
        when(logs.search(any())).thenReturn(new PageImpl<>(List.of(row),PageRequest.of(0,20),1));
        mvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION,"Bearer fixture")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(91)).andExpect(jsonPath("$.content[0].createdAt").value("2026-10-16T09:00:00"))
                .andExpect(jsonPath("$.content[0].actorName").value("Audit Manager")).andExpect(jsonPath("$.content[0].actorRole").value("MANAGER"))
                .andExpect(jsonPath("$.content[0].action").value("PASSWORD_CHANGED")).andExpect(jsonPath("$.content[0].shortDetail").value("Password changed"))
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist()).andExpect(jsonPath("$.content[0].details").doesNotExist())
                .andExpect(jsonPath("$.content[0].summary").doesNotExist()).andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.totalPages").value(1));
        verify(logs).search(ActivityLogQuery.parse(null,null,null,null,null,0,20));
    }
    @Test void combinedParameters() throws Exception {
        manager(); when(logs.search(any())).thenReturn(Page.empty(PageRequest.of(2,5)));
        mvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION,"Bearer fixture").param("actorId","17").param("action","LOGIN")
                .param("from","2026-10-01").param("to","2026-10-16").param("search","ADA").param("page","2").param("size","5"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(2)).andExpect(jsonPath("$.totalElements").value(0));
        verify(logs).search(ActivityLogQuery.parse(17L,"LOGIN","2026-10-01","2026-10-16","ADA",2,5));
    }
    @ParameterizedTest @CsvSource({"actorId,0","actorId,no","action,UNKNOWN","from,2026-02-30","to,invalid","page,-1","page,no","size,0","size,101","page,2147483647"})
    void invalidParameters(String field,String value) throws Exception {
        manager(); mvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION,"Bearer fixture").param(field,value))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400)).andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.message").isString()); verifyNoInteractions(logs);
    }
    @Test void reversedAndLongSearch() throws Exception {
        manager(); mvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION,"Bearer fixture").param("from","2026-10-17").param("to","2026-10-16"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.to").exists());
        mvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION,"Bearer fixture").param("search","x".repeat(201)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.search").exists()); verifyNoInteractions(logs);
    }
    @ParameterizedTest @ValueSource(strings={"MEMBER","COACH","RECEPTIONIST","ADMIN","manager"})
    void nonManagerForbidden(String role) throws Exception {
        token(role,"ACTIVE","access"); mvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION,"Bearer fixture"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403)).andExpect(jsonPath("$.content").doesNotExist());
        verifyNoInteractions(logs);
    }
    @Test void anonymous() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401)); verifyNoInteractions(logs);
    }
    @ParameterizedTest @CsvSource({"INACTIVE,access","ACTIVE,refresh"})
    void inactiveAndRefreshRejected(String status,String type) throws Exception {
        token("MANAGER",status,type); mvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION,"Bearer fixture")).andExpect(status().isUnauthorized());
        verifyNoInteractions(logs);
    }
    @Test void directServiceProtected() {
        var p=new CustomUserDetails(UserAccount.builder().role(Role.builder().code("MEMBER").build()).build());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()));
        try { assertThatThrownBy(()->service.list(null,null,null,null,null,0,20)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            verifyNoInteractions(logs); } finally {SecurityContextHolder.clearContext();}
    }
}
