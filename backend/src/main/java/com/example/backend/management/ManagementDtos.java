package com.example.backend.management;

import com.example.backend.user.UserProfileDto;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public final class ManagementDtos {
    private ManagementDtos() { }

    public record StoreRequest(
            @Size(max=180) String name, @Size(max=2000) String description,
            @Size(max=500) String address, @Size(max=32) String phone, @Size(max=254) String email,
            @Size(max=32) String openTime, @Size(max=32) String closeTime,
            @Size(max=1024) String coverImage, @Size(max=1024) String avatarUrl,
            Boolean isOpen, Boolean orderNotify, Boolean soundNotify, Boolean pushNotify,
            Boolean emailNotify, Boolean promoNotify, BigDecimal latitude, BigDecimal longitude) { }

    public record StoreMine(String id, String name, double rating, String address, String deliveryTime,
            String distance, String description, String coverImage, String avatarUrl, String email,
            String phone, String openTime, String closeTime, boolean orderNotify, boolean soundNotify,
            boolean pushNotify, boolean emailNotify, boolean promoNotify, Double latitude, Double longitude,
            boolean isOpen) { }

    public record DishRequest(@NotNull Long restaurantId, @Size(max=120) String category,
            @NotBlank @Size(max=180) String name, @NotNull @DecimalMin("0") BigDecimal price,
            @Size(max=1024) String imageUrl, @Size(max=2000) String description, Boolean available) { }

    public record PromotionRequest(Long storeId, @NotBlank @Size(max=64) String code,
            @NotBlank @Size(max=180) String title, @Size(max=2000) String description,
            @NotBlank String type, @NotNull @DecimalMin("0") BigDecimal value,
            @DecimalMin("0") BigDecimal maxDiscount, @DecimalMin("0") BigDecimal minOrderValue,
            Integer usageLimit, String startDate, String endDate, String status,
            @Size(max=1500) String imageUrl) { }

    public record StatusRequest(@Size(max=32) String status) { }
    public record UserPatch(@Size(max=160) String name, @Size(max=160) String fullName,
            @Size(max=254) String email, @Size(max=32) String phone,
            @Size(max=32) String phoneNumber, @Size(max=32) String role, Boolean isAvailable) { }
    public record IdResponse(String id) { }
    public record OkResponse(boolean ok) { }
}
