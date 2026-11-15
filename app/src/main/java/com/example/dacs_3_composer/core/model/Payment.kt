package com.example.dacs_3_composer.core.model

data class Payment(
    val id: String = "",
    val userId: String = "", // ✅ Thêm userId để lọc lịch sử cho User
    val orderId: String = "",
    val amount: Double = 0.0,
    val paymentLinkId: String = "",
    val transactionId: String? = null,
    val paymentMethod: String = "PAYOS",
    val description: String = "",
    val paymentStatus: String = "PENDING",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
