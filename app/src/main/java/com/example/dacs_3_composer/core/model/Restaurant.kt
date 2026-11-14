package com.example.dacs_3_composer.core.model

data class Restaurant(
    val name: String,
    val description: String,
    val rating: Float, // đánh giá
    val time: String,
    val distance: String, // khoảng cách
    val promo: String?, // khuyến mãi
    val imageRes: Int
)