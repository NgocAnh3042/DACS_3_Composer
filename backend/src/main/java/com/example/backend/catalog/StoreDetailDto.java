package com.example.backend.catalog;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record StoreDetailDto(
        String id, String name, double rating, String address, String deliveryTime, String distance,
        Double distanceKm, double shippingFee, String description, String coverImage, String avatarUrl,
        String email, String phone, String openTime, String closeTime, Double latitude, Double longitude,
        boolean isOpen) { }
