package com.sportify.identity.controller;

import com.sportify.identity.repository.UserAvatarStore;
import com.sportify.identity.security.CustomAccessDeniedHandler;
import com.sportify.identity.security.CustomAuthenticationEntryPoint;
import com.sportify.identity.security.JwtAuthenticationFilter;
import com.sportify.identity.security.JwtService;
import com.sportify.identity.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserAvatarController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtAuthenticationFilter.class})
class UserAvatarControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserAvatarStore avatarStore;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private AuthenticationProvider authenticationProvider;
    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    void guestReadsUploadedPhotoWithStoredTypeAndLongCache() throws Exception {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G'};
        when(avatarStore.find(7L)).thenReturn(Optional.of(new UserAvatarStore.StoredAvatar("image/png", png)));

        mockMvc.perform(get("/api/v1/users/7/avatar").param("v", "1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(png))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "max-age=31536000, public"));
    }

    @Test
    void missingPhotoReturns404() throws Exception {
        when(avatarStore.find(8L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/users/8/avatar")).andExpect(status().isNotFound());
    }
}
