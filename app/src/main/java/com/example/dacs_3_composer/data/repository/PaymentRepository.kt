package com.example.dacs_3_composer.data.repository

import com.example.dacs_3_composer.data.remote.FoodHubClient
import com.example.dacs_3_composer.data.remote.PayOSResponse
import com.google.gson.JsonObject

class PaymentRepository {
    suspend fun createPaymentLink(orderId: String, amount: Double, description: String): PayOSResponse {
        val payload = JsonObject().apply { addProperty("orderId", orderId) }
        val response = FoodHubClient.api.create("payments/create-link", payload)
        if (!response.isSuccessful) error(response.errorBody()?.string() ?: "Không tạo được link PayOS")
        return FoodHubClient.gson.fromJson(response.body(), PayOSResponse::class.java)
    }
}
