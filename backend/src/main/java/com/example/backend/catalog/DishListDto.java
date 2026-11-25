package com.example.backend.catalog;

public record DishListDto(
        String id,
        String name,
        double price,
        String imageUrl,
        String category,
        String description,
        boolean available,
        String restaurantId) { }
