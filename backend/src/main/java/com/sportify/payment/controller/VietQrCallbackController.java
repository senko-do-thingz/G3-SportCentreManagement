package com.sportify.payment.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sportify.payment.service.VietQrCallbackException;
import com.sportify.payment.service.VietQrCallbackTokenService;
import com.sportify.payment.service.VietQrTransactionCallback;
import com.sportify.payment.service.VietQrTransactionSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class VietQrCallbackController {

    private final VietQrCallbackTokenService tokens;
    private final VietQrTransactionSyncService transactions;
    private final com.sportify.payment.config.VietQrGatewayProperties properties;

    public record TokenResponse(@JsonProperty("access_token") String accessToken,
                                @JsonProperty("token_type") String tokenType,
                                @JsonProperty("expires_in") int expiresIn) {}

    public record TransactionResponseObject(@JsonProperty("reftransactionid") String refTransactionId) {}
    public record CallbackResponse(boolean error, String errorReason, String toastMessage,
                                   TransactionResponseObject object) {}

    @PostMapping(value = "/api/token_generate", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> issueCallbackToken(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (!properties.isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new CallbackResponse(true, "GATEWAY_DISABLED", "VietQR callback integration is disabled.", null));
        }
        String token = tokens.issueToken(authorization);
        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new CallbackResponse(true, "INVALID_CREDENTIALS", "Authorization is missing or invalid.", null));
        }
        return ResponseEntity.ok(new TokenResponse(token, "Bearer", properties.getCallbackTokenTtlSeconds()));
    }

    @PostMapping(value = "/bank/api/transaction-sync", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CallbackResponse> transactionSync(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) VietQrTransactionCallback callback) {
        if (!properties.isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new CallbackResponse(true, "GATEWAY_DISABLED", "VietQR callback integration is disabled.", null));
        }
        if (!tokens.isValidBearer(authorization)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new CallbackResponse(true, "INVALID_TOKEN", "Missing or invalid callback token.", null));
        }
        try {
            String paymentCode = transactions.accept(callback);
            return ResponseEntity.ok(new CallbackResponse(false, null, "Transaction processed successfully.",
                    new TransactionResponseObject(paymentCode)));
        } catch (VietQrCallbackException invalid) {
            return ResponseEntity.badRequest().body(new CallbackResponse(true, invalid.getErrorReason(), invalid.getMessage(), null));
        } catch (RuntimeException failure) {
            log.error("VietQR transaction callback failed", failure);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new CallbackResponse(true, "TRANSACTION_FAILED", "The transaction could not be processed.", null));
        }
    }
}
