package com.example.dacs_3_composer.core.model

import androidx.compose.runtime.Immutable

//Gọi để thêm sửa xóa cho firebase
@Immutable
data class Dish(
    val id: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val imageUrl: String = "",
    val category: String = "Tất cả",
    val description: String = "",
    val available: Boolean = true,
    val restaurantId: String = ""
)
