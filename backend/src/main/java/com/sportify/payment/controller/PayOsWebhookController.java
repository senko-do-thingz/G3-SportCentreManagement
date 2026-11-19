package com.sportify.payment.controller;

import com.sportify.payment.service.PayOsWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PayOsWebhookController {
    private final PayOsWebhookService webhooks;

    @PostMapping(value = "/api/v1/payments/payos/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    public PayOsWebhookService.WebhookResult receive(@RequestBody PayOsWebhookService.WebhookEvent event) {
        return webhooks.accept(event);
    }
}
