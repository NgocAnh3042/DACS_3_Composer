package com.example.dacs_3_composer.core.model


enum class OrderStatus(val displayName: String) {
    PENDING_PAYMENT("Chờ thanh toán"),
    PENDING("Chờ xác nhận"),
    PROCESSING("Đang chuẩn bị"),
    ACCEPTED("Đã chuẩn bị xong"),
    SHIPPING("Đang giao hàng"),
    COMPLETED("Đã giao hàng"),
    CANCELLED("Đã hủy")
}

data class OrderItem(
    var dishId: String = "",
    var name: String = "",
    var quantity: Int = 0,
    var price: Double = 0.0
)

data class Order(
    var id: String = "",
    var time: String = "",
    var status: String = "PENDING",
    var paymentMethod: String = "CASH",
    var isPaid: Boolean = false,

    var totalDishPrice: Double = 0.0,
    var shippingFee: Double = 20000.0,
    var totalPrice: Double = 0.0,

    var appliedPromotionId: String? = null,
    var appliedPromotionTitle: String = "",
    var promotionDiscount: Double = 0.0,

    var userId: String = "",
    var customerName: String = "",
    var customerPhone: String = "",
    var customerAddress: String = "",
    var customerLat: Double? = null,
    var customerLng: Double? = null,

    var restaurantId: String = "",
    var restaurantName: String = "",
    var restaurantLat: Double? = null,
    var restaurantLng: Double? = null,

    var shipperId: String = "",
    var items: List<OrderItem> = emptyList(),
    var completedAt: String? = null
) {
    constructor() : this(
        id = "", time = "", status = "PENDING", paymentMethod = "CASH", isPaid = false,
        totalDishPrice = 0.0, shippingFee = 20000.0, totalPrice = 0.0,
        appliedPromotionId = null, appliedPromotionTitle = "", promotionDiscount = 0.0,
        userId = "", customerName = "", customerPhone = "", customerAddress = "",
        customerLat = null, customerLng = null,
        restaurantId = "", restaurantName = "",
        restaurantLat = null, restaurantLng = null,
        shipperId = "", items = emptyList()
    )
}
