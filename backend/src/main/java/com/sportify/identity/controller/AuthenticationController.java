package com.sportify.identity.controller;

import com.sportify.identity.dto.AuthRequest;
import com.sportify.identity.dto.AuthResponse;
import com.sportify.identity.dto.RegisterRequest;
import com.sportify.identity.dto.RefreshTokenRequest;
import com.sportify.identity.service.AuthenticationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthenticationController {

    private final AuthenticationService service;
    private final com.sportify.identity.service.PasswordRecoveryService recovery;

    public record RecoveryEmail(String email) {}
    public record RecoveryOtp(String email, String otp) {}
    public record RecoveryPassword(String authorization, String newPassword, String confirmPassword) {}

    // Yêu cầu OTP luôn trả thông điệp công khai thống nhất để tránh lộ email đã đăng ký.
    @PostMapping("/forgot-password/request")
    public java.util.Map<String, String> requestRecovery(@RequestBody RecoveryEmail request) {
        try {
            recovery.request(request.email());
        } catch (IllegalStateException deliveryOrConfigurationFailure) {
            // Keep the same public response for known and unknown addresses.
        }
        return java.util.Map.of("message", "If an account exists and email is available, check your inbox for a code.");
    }

    @PostMapping("/forgot-password/verify")
    public java.util.Map<String, String> verifyRecovery(@RequestBody RecoveryOtp request) {
        return java.util.Map.of("authorization", recovery.verify(request.email(), request.otp()));
    }

    @PostMapping("/forgot-password/reset")
    public java.util.Map<String, String> resetPassword(@RequestBody RecoveryPassword request) {
        recovery.reset(request.authorization(), request.newPassword(), request.confirmPassword());
        return java.util.Map.of("message", "Password reset. You can now log in.");
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(service.register(request));
    }

    // Controller chỉ nhận và kiểm tra dữ liệu đầu vào; xác thực và phát JWT được giao cho service.
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> authenticate(@Valid @RequestBody AuthRequest request) {
        return ResponseEntity.ok(service.authenticate(request));
    }

    // Refresh token được xử lý ở backend; không cho frontend tự tạo hoặc ký lại access token.
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(service.refreshToken(request));
    }
}
