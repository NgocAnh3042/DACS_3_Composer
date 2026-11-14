package com.example.dacs_3_composer.core.model

data class NotificationItem(
    val id: String,
    val title: String,
    val content: String,
    val time: String,
    val type: String, // "all" (Tất cả), "order" (Đơn hàng), "system" (Hệ thống)
    val isRead: Boolean = false
)