package com.example.backend.payment;

public interface PayosClient {
    PayosCreateResult createPaymentLink(PayosCreateRequest request);

    record PayosCreateRequest(long orderCode, int amount, String description, String cancelUrl,
            String returnUrl, String signature) { }
    record PayosCreateResult(String code, String checkoutUrl, String paymentLinkId, String qrCode) { }
}
