package com.sportify.catalog.controller;
import com.sportify.catalog.service.ClassLearningService;
import com.sportify.core.exception.GlobalExceptionHandler;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
@WebMvcTest(ClassLearningController.class)
@Import({SecurityConfig.class,CustomAuthenticationEntryPoint.class,CustomAccessDeniedHandler.class,JwtAuthenticationFilter.class,GlobalExceptionHandler.class})
class ClassLearningControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ClassLearningService service;
    @MockitoBean JwtService jwtService;
    @MockitoBean AuthenticationProvider authenticationProvider;
    @MockitoBean UserDetailsService userDetailsService;
    private CustomUserDetails actor(String role){return new CustomUserDetails(UserAccount.builder().id(42L).role(Role.builder().code(role).build()).status("ACTIVE").email("actor@test.invalid").build());}
    @Test void R2_API_01_memberReadRoutesEnforceRoleAndAuthentication()throws Exception{
        for(String path:List.of("classes","classes/1","questions","questions/1","guidance?query=swim")){
            String url="/api/v1/member/training/"+path;
            mvc.perform(get(url)).andExpect(status().isUnauthorized());
            mvc.perform(get(url).with(user(actor("COACH")))).andExpect(status().isForbidden());
            mvc.perform(get(url).with(user(actor("MEMBER")))).andExpect(status().isOk());
        }
    }
    @Test void R2_API_02_questionWritesUsePrincipalAndRejectWrongRoles()throws Exception{
        String ask="{\"classId\":1,\"subject\":\"Practice\",\"body\":\"How should I practise?\"}";
        String reply="{\"body\":\"Use your assigned steps\"}";
        mvc.perform(post("/api/v1/member/training/questions").contentType(MediaType.APPLICATION_JSON).content(ask)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/member/training/questions").with(user(actor("COACH"))).contentType(MediaType.APPLICATION_JSON).content(ask)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/member/training/questions").with(user(actor("MEMBER"))).contentType(MediaType.APPLICATION_JSON).content(ask)).andExpect(status().isCreated());
        verify(service).ask(42L,1L,"Practice","How should I practise?");
        for(String role:List.of("MEMBER","COACH")){
            String root=role.equals("COACH")?"coach":"member";
            mvc.perform(get("/api/v1/"+root+"/training/questions/1").with(user(actor(role)))).andExpect(status().isOk());
            mvc.perform(post("/api/v1/"+root+"/training/questions/1/messages").with(user(actor(role))).contentType(MediaType.APPLICATION_JSON).content(reply)).andExpect(status().isCreated());
        }
        mvc.perform(post("/api/v1/coach/training/questions/1/messages").with(user(actor("MEMBER"))).contentType(MediaType.APPLICATION_JSON).content(reply)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/coach/training/questions").with(user(actor("COACH")))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/coach/training/questions").with(user(actor("RECEPTIONIST")))).andExpect(status().isForbidden());
    }
    @Test void R2_API_03_blankQuestionAndReplyReturn400()throws Exception{
        mvc.perform(post("/api/v1/member/training/questions").with(user(actor("MEMBER"))).contentType(MediaType.APPLICATION_JSON).content("{\"classId\":1,\"subject\":\"\",\"body\":\"\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/coach/training/questions/1/messages").with(user(actor("COACH"))).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"\"}")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
