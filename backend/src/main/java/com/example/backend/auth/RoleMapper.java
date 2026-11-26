package com.example.backend.auth;

import com.example.backend.api.ApiException;
import java.util.Locale;
import org.springframework.http.HttpStatus;

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
            case "user", "customer" -> "customer";
            case "restaurant", "seller", "nhà hàng" -> "seller";
            case "shipper", "driver" -> "driver";
            case "admin" -> "customer";
            default -> throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Vai trò không hợp lệ");
        };
    }
}
