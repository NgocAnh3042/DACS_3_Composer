package com.example.backend.activity;

public final class ActivityLogDtos {
    private ActivityLogDtos() { }
    public record Item(String id,String title,String description,String details,String type,long timestamp,String imageUrl) { }
    public record CreateRequest(Long storeId,String type,String title,String description,String details) { }
    public record IdResponse(String id) { }
}
