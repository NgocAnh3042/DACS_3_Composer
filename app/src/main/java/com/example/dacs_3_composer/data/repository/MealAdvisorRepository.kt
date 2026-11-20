package com.example.dacs_3_composer.data.repository

import com.example.dacs_3_composer.core.model.Dish
import com.example.dacs_3_composer.data.remote.FoodHubClient
import com.google.gson.JsonParser
import java.io.IOException

data class MealRecommendation(
    val dish: Dish,
    val restaurantName: String,
    val reason: String,
    val estimatedTotal: Double
)

data class MealAdvice(
    val message: String,
    val preferences: String,
    val budget: Double,
    val budgetScope: String,
    val deliveryFee: Double,
    val catalogLimited: Boolean,
    val recommendations: List<MealRecommendation>,
    val source: String? = null,
    val menuCursor: MenuCursor? = null,
    val hasMore: Boolean = false,
    val kind: String? = null
)

data class MenuCursor(val afterId: String, val storeId: String?, val ceiling: Double)
data class MealChatContext(val question: String, val dishIds: List<String>, val answer: String? = null, val menuCursor: MenuCursor? = null)

class MealAdvisorRepository {
    suspend fun suggest(message: String, budget: Long?, includeDelivery: Boolean, history: List<MealChatContext>): MealAdvice {
        val payload = com.google.gson.JsonObject().apply {
            addProperty("message", message)
            // Existing API contract uses a ceiling of 10 million VND.
            addProperty("budget", budget ?: 10_000_000L)
            add("history", FoodHubClient.gson.toJsonTree(history))
            addProperty("budgetScope", if (includeDelivery) "total" else "food")
        }
        val response = FoodHubClient.api.create("ai/meal-advisor", payload)
        if (!response.isSuccessful) {
            val error = runCatching {
                JsonParser.parseString(response.errorBody()?.string().orEmpty()).asJsonObject["error"].asString
            }.getOrNull()
            throw IOException(error ?: "Chưa thể tư vấn món. Vui lòng thử lại.")
        }
        val data = response.body()?.getAsJsonObject("data") ?: throw IOException("Máy chủ chưa trả gợi ý món.")
        return FoodHubClient.gson.fromJson(data, MealAdvice::class.java)
    }
}
