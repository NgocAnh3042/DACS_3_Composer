package com.example.backend.auth;

import java.util.Locale;

public final class RoleMapper {
    private RoleMapper() { }

    public static String toAndroidRole(String databaseRole) {
        return switch (databaseRole) {
            case "customer" -> "user";
            case "seller" -> "restaurant";
            case "driver" -> "shipper";
            case "admin" -> "admin";
            default -> throw new IllegalArgumentException("Unknown database role");
        };
    }

    public static String selfRegistrationRole(String requestedRole) {
        String role = requestedRole == null ? "customer" : requestedRole.trim().toLowerCase(Locale.ROOT);
        return switch (role) {
            case "restaurant", "seller", "nhà hàng" -> "seller";
            case "shipper", "driver" -> "driver";
            default -> "customer";
        };
    }
}
