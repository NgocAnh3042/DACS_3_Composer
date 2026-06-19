package com.example.dacs_3_composer.data.model

enum class OrderStatus(val displayName: String) {
    PENDING_PAYMENT("Chờ thanh toán"),
    WAITING_RESTAURANT("Chờ nhà hàng nhận"),
    PENDING("Đang xử lý"),
    PROCESSING("Đang nấu"),
    ACCEPTED("Chờ shipper"),
    SHIPPING("Đang giao"),
    COMPLETED("Đã hoàn thành"),
    CANCELLED("Đã hủy")
}

data class OrderItem(
    var dishId: String = "",
    var name: String = "",
    var quantity: Int = 0,
    var price: Double = 0.0
)

data class Order(
    // 1. Thông tin định danh Đơn hàng
    var id: String = "",
    var time: String = "",
    var status: String = "PENDING_PAYMENT",
    var paymentMethod: String = "CASH", // "CASH" (COD) hoặc "ONLINE" (PayOS)
    var isPaid: Boolean = false, // ✅ Thêm trường xác nhận đã thanh toán

    // Cấu trúc tiền tệ và Khuyến mãi
    var totalDishPrice: Double = 0.0,    // Giá gốc tổng các món ăn
    var shippingFee: Double = 20000.0,   // Phí vận chuyển
    var totalPrice: Double = 0.0,        // Số tiền cuối cùng khách trả (Đã trừ discount)

    // BỘ BA TRƯỜNG THÔNG TIN KHUYẾN MÃI ĐƯỢC LƯU TRỰC TIẾP VÀO ĐƠN HÀNG
    var appliedPromotionId: String? = null,      // ID tài liệu trên Firebase (Document ID)
    var appliedPromotionTitle: String = "",      // Tên hiển thị của voucher (Ví dụ: "giảm giá 30k")
    var promotionDiscount: Double = 0.0,         // Số tiền thực tế được giảm (Ví dụ: 30000.0)

    // 2. Thông tin của KHÁCH HÀNG (Người mua)
    var userId: String = "",
    var customerName: String = "",
    var customerPhone: String = "",
    var customerAddress: String = "",
    var customerLat: Double? = null,
    var customerLng: Double? = null,

    // 3. Thông tin của NHÀ HÀNG (Người bán)
    var restaurantId: String = "",
    var restaurantName: String = "",
    var restaurantLat: Double? = null,
    var restaurantLng: Double? = null,

    // 4. Thông tin của SHIPPER (Người giao)
    var shipperId: String = "",

    // 5. Danh sách các món ăn trong đơn
    var items: List<OrderItem> = emptyList()
) {
    // Constructor không tham số bắt buộc để Firebase Firestore ánh xạ (mapping) tự động mượt mà
    constructor() : this(
        id = "", time = "", status = "PENDING_PAYMENT", paymentMethod = "CASH", isPaid = false,
        totalDishPrice = 0.0, shippingFee = 20000.0, totalPrice = 0.0,

        appliedPromotionId = null,
        appliedPromotionTitle = "",
        promotionDiscount = 0.0,

        userId = "", customerName = "", customerPhone = "", customerAddress = "",
        customerLat = null, customerLng = null,
        restaurantId = "", restaurantName = "",
        restaurantLat = null, restaurantLng = null,
        shipperId = "", items = emptyList()
    )
}