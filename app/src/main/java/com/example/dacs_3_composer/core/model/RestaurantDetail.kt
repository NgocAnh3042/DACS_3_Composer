package com.example.dacs_3_composer.core.model


data class RestaurantDetail(
    var id: String = "",
    val name: String = "",
    val rating: Double = 0.0,
    var address: String = "", // Chuyển thành var để tiện gán cập nhật chuỗi chữ từ Map
    val deliveryTime: String = "15-20 min",
    val distance: String = "0.5 km",
    val description: String = "",
    val coverImage: String = "",
    val avatarUrl: String = "",
    val email: String = "",
    val phone: String = "",
    val openTime: String = "",
    val closeTime: String = "",

    // Cài đặt thông báo
    val orderNotify: Boolean = true,
    val soundNotify: Boolean = true,
    val pushNotify: Boolean = true,
    val emailNotify: Boolean = false,
    val promoNotify: Boolean = false,

    // 🌟 BỔ SUNG 2 TRƯỜNG NÀY ĐỂ ĐỊNH VỊ QUÁN
    val latitude: Double? = null,
    val longitude: Double? = null,
    val distanceKm: Double? = null,
    val shippingFee: Double = 15000.0
) {
    // Constructor không tham số bắt buộc cho Firestore .toObject()
    constructor() : this(
        "", "", 0.0, "", "15-20 phút", "Chưa rõ vị trí", "", "", "", "", "", "", "",
        true, true, true, false, false, null, null, null, 15000.0
    )
}
