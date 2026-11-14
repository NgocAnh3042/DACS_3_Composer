package com.example.dacs_3_composer.core.model

data class UserAddress(
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    val address: String = "",          // Chữ hiển thị cho người dùng đọc (ví dụ: 93 Nguyễn Đình Chiểu)
    val addressDetail: String = "",    // Thông tin bổ sung (Cổng, số tầng, ghi chú giao hàng)
    val latitude: Double = 16.0748,    // Số định vị Vĩ độ (Dành cho Map Shipper)
    val longitude: Double = 108.2240,  // Số định vị Kinh độ (Dành cho Map Shipper)
    val isDefault: Boolean = false
)

//Địa chỉ Customer
