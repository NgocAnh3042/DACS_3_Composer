package com.example.dacs_3_composer.core.model


data class ActivityLog(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val time: String = "", // e.g., "08:15 AM"
    val date: String = "", // e.g., "24 Tháng 5"
    val timestamp: Long = 0L,
    val type: String = "info", // "login", "menu", "profile", "order", "security"
    val details: String? = null,
    val imageUrl: String? = null,
    val extraInfo: Map<String, String>? = null
)
