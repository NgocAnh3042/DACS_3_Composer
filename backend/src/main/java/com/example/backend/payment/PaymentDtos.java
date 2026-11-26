package com.example.backend.payment;

public final class PaymentDtos {
    private PaymentDtos(){}
    public record CreateLinkRequest(Long orderId){}
    public record CreateLinkResponse(String checkoutUrl,String paymentLinkId,String qrCode){}
    public record PaymentItem(String id,String userId,String orderId,double amount,String paymentLinkId,
            String transactionId,String paymentMethod,String description,String paymentStatus,long createdAt,long updatedAt){}
    public record WebhookResponse(boolean ok){}
    public record IgnoredWebhookResponse(boolean ok,boolean ignored){}
}
