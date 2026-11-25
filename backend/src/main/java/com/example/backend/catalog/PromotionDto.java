package com.example.backend.catalog;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record PromotionDto(
        String id,
        String code,
        String imageUrl,
        String type,
        double value,
        double maxDiscount,
        double minOrderValue,
        long usageCount,
        long usageLimit,
        String description,
        String title,
        String status,
        String startDate,
        String endDate) { }
