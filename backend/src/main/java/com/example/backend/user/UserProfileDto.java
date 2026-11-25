package com.example.backend.user;

public record UserProfileDto(
        String id,
        String uid,
        String fullName,
        String name,
        String email,
        String phoneNumber,
        String phone,
        String address,
        String avatarUrl,
        String role,
        String status,
        boolean isAvailable) { }
