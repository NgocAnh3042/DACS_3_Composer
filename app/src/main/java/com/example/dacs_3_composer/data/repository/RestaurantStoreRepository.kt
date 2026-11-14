package com.example.dacs_3_composer.data.repository

import com.example.dacs_3_composer.core.model.RestaurantDetail
import com.example.dacs_3_composer.data.remote.FoodHubClient
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.JsonObject

object RestaurantStoreRepository {
    suspend fun ensureStore(): RestaurantDetail {
        val existing = FoodHubClient.api.get("stores", "mine")
        if (existing.isSuccessful) {
            val data = existing.body()?.get("data") ?: error("API không trả thông tin cửa hàng")
            return FoodHubClient.gson.fromJson(data, RestaurantDetail::class.java)
        }
        if (existing.code() != 404) error(existing.errorBody()?.string() ?: "Không tải được cửa hàng (HTTP ${existing.code()})")

        val user = FirebaseAuth.getInstance().currentUser ?: error("Chưa đăng nhập")
        val payload = JsonObject().apply {
            addProperty("name", user.displayName?.takeIf { it.isNotBlank() } ?: "Cửa hàng của tôi")
            addProperty("email", user.email ?: "")
            addProperty("avatarUrl", user.photoUrl?.toString() ?: "")
            addProperty("openTime", "08:00")
            addProperty("closeTime", "22:00")
        }
        val created = FoodHubClient.api.create("stores/mine", payload)
        if (!created.isSuccessful) error(created.errorBody()?.string() ?: "Không tạo được cửa hàng (HTTP ${created.code()})")
        return FoodHubClient.get("stores", "mine")
    }
}
