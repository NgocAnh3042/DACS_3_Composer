package com.example.dacs_3_composer.core.model

import com.google.firebase.Timestamp

data class Promotion(
    val id: String = "",
    val code: String = "",
    val type: String = "percentage", // "percentage", "fixed", "free_shipping"
    val value: Double = 0.0,
    val maxDiscount: Double = 0.0,
    val minOrderValue: Double = 0.0,
    val usageCount: Long = 0,
    val usageLimit: Long = 0, // 0 for unlimited
    val startDate: Timestamp? = null,
    val endDate: Timestamp? = null,
    val description: String = "",
    val title: String = "",
    val status: String = "active" // "active", "expired"
)
