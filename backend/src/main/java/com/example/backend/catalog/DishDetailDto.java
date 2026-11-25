package com.example.backend.catalog;

public record DishDetailDto(
        String id,
        String name,
        double price,
        String imageUrl,
        String category,
        String description,
        boolean available,
        String restaurantId,
        String restaurantName,
        String restaurantAddress,
        String restaurantLogo,
        Boolean restaurantOpen,
        Double ratingAvg,
        Integer ratingCount) { }
