package com.example.backend.auth;

public record FirebasePrincipal(
        String firebaseUid,
        Long userId,
        String databaseRole,
        String androidRole,
        String email,
        boolean emailVerified) { }
