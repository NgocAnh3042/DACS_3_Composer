package com.example.backend.notification;

import java.time.LocalDateTime;

public final class NotificationDtos {
    private NotificationDtos() { }
    public record Item(String id,String title,String content,LocalDateTime time,String type,boolean isRead) { }
    public record DeviceTokenRequest(String token,String platform) { }
    public record OkResponse(boolean ok) { }
}
