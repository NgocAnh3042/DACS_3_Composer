package com.example.backend.payment;

public record PaymentSettledEvent(long customerId,long ownerId,long orderId) { }
