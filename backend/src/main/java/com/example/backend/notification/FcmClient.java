package com.example.backend.notification;

public interface FcmClient {
    FcmSendResult send(String registrationToken, String title, String body, String type, String orderId);
}
