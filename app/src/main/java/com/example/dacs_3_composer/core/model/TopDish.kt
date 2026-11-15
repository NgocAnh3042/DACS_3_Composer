package com.example.dacs_3_composer.core.model

data class TopDish(
    val rank: Int = 0,
    val dishName: String = "",
    val ordersCount: Int = 0,
    val imageUrl: String = "" // Đổi từ imageRes sang imageUrl để tải từ Firebase
)