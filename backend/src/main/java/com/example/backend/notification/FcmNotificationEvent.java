package com.example.backend.notification;

import java.util.List;

public record FcmNotificationEvent(List<Long> recipientUserIds, String type, String title,
        String body, Long orderId) {
    public FcmNotificationEvent {
        recipientUserIds = List.copyOf(recipientUserIds);
    }
}
