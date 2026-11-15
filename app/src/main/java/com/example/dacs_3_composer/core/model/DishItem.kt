package com.example.dacs_3_composer.core.model

data class DishItem(
    val id: String = "",
    val name: String,
    val price: Double,
    val description: String,
    val imageUrl: String,
    val category: String,
    val isAvailable: Boolean = true,

    // 🌟 THÊM 2 DÒNG NÀY ĐỂ MANG THEO THÔNG TIN QUÁN ĂN
    val restaurantId: String = "",
    val restaurantName: String = ""
)