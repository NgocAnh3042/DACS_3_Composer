package com.example.dacs_3_composer.core.model


data class User(
    var uid: String = "",

    var fullName: String = "",

    var name: String = "", // Dùng cho Nhà hàng hoặc dữ liệu cũ

    var email: String = "",

    var phoneNumber: String = "",

    var phone: String = "",

    var address: String = "",
    var avatarUrl: String = "",
    var role: String = "",
    var vehicleName: String = "",
    var isAvailable: Boolean = false,
    var status: String = "ACTIVE"
) {
    // Constructor không tham số cho Firebase
    constructor() : this("")

    fun getDisplayName(): String {
        // Ưu tiên fullName, nếu rỗng dùng name, rỗng nữa dùng mặc định
        return fullName.ifBlank { name }.ifBlank { "Người dùng" }
    }
}
