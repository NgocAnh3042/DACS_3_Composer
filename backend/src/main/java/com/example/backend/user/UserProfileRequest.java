package com.example.backend.user;

public record UserProfileRequest(
        String fullName,
        String name,
        String email,
        String phoneNumber,
        String phone,
        String address,
        String avatarUrl,
        String role) { }
