package com.example.dacs_3_composer.data.repository

import android.util.Log
import com.example.dacs_3_composer.core.model.Dish
import com.example.dacs_3_composer.data.remote.FoodHubClient
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

data class PersonalizedDish(
    val dish: Dish,
    val restaurantName: String = "",
    val score: Double? = null
)

data class PersonalizedRecommendationsResult(
    val type: String = "popular",
    val title: String = "Món phổ biến hôm nay",
    val items: List<PersonalizedDish> = emptyList()
)

data class SearchHistoryEntry(
    val id: String = "",
    val query: String = "",
    val normalizedQuery: String = "",
    val searchedAt: String = ""
)

data class RecommendationPreferences(
    val searchPersonalizationEnabled: Boolean = true,
    val retentionDays: Int = 90
)

/** Signals HomeScreen to refresh recommendations once after a confirmed search or privacy change. */
object RecommendationRefreshBus {
    private val dirty = AtomicBoolean(false)
    fun markDirty() { dirty.set(true) }
    fun consumeDirty(): Boolean = dirty.getAndSet(false)
}

class RecommendationRepository {
    /**
     * Records a confirmed search query asynchronously.
     * Swallows network failures so normal food search is never interrupted.
     */
    suspend fun recordSearchHistory(query: String): Boolean {
        val cleaned = query.trim()
        if (cleaned.length < 2 || FirebaseAuth.getInstance().currentUser == null) return false
        return try {
            val payload = JsonObject().apply { addProperty("query", cleaned) }
            val response = FoodHubClient.api.create("search/history", payload)
            if (response.isSuccessful) {
                val recorded = response.body()
                    ?.getAsJsonObject("data")
                    ?.get("recorded")
                    ?.asBoolean ?: false
                if (recorded) RecommendationRefreshBus.markDirty()
                recorded
            } else {
                Log.w("RecommendationRepo", "Ghi lịch sử tìm kiếm thất bại: HTTP ${response.code()}")
                false
            }
        } catch (e: Exception) {
            Log.w("RecommendationRepo", "Không thể lưu lịch sử tìm kiếm: ${e.message}")
            false
        }
    }

    suspend fun getPersonalizedRecommendations(limit: Int = 10): PersonalizedRecommendationsResult {
        if (FirebaseAuth.getInstance().currentUser == null) {
            return PersonalizedRecommendationsResult()
        }
        val response = FoodHubClient.api.list(
            resource = "recommendations/personalized",
            filters = mapOf("limit" to limit.coerceIn(1, 20).toString())
        )
        if (!response.isSuccessful) {
            throw IOException("Không tải được gợi ý món ăn (HTTP ${response.code()})")
        }
        val body = response.body() ?: return PersonalizedRecommendationsResult()
        val type = body.get("type")?.asString ?: "popular"
        val title = body.get("title")?.asString ?: if (type == "personalized") "Dành riêng cho bạn" else "Món phổ biến hôm nay"
        val array = body.getAsJsonArray("data") ?: JsonArray()
        val items = array.mapNotNull { element ->
            runCatching {
                val obj = element.asJsonObject
                val dish = FoodHubClient.gson.fromJson(obj, Dish::class.java)
                val restaurantName = obj.get("restaurantName")?.asString.orEmpty()
                val score = if (obj.has("score") && !obj.get("score").isJsonNull) obj.get("score").asDouble else null
                PersonalizedDish(dish = dish, restaurantName = restaurantName, score = score)
            }.getOrNull()
        }
        return PersonalizedRecommendationsResult(type = type, title = title, items = items)
    }

    suspend fun getSearchHistory(limit: Int = 20): List<SearchHistoryEntry> {
        if (FirebaseAuth.getInstance().currentUser == null) return emptyList()
        val response = FoodHubClient.api.list("search/history", mapOf("limit" to limit.toString()))
        if (!response.isSuccessful) return emptyList()
        val array = response.body()?.getAsJsonArray("data") ?: JsonArray()
        return array.mapNotNull { runCatching { FoodHubClient.gson.fromJson(it, SearchHistoryEntry::class.java) }.getOrNull() }
    }

    suspend fun clearSearchHistory(): Boolean {
        if (FirebaseAuth.getInstance().currentUser == null) return false
        val response = FoodHubClient.api.delete("search", "history")
        if (response.isSuccessful) {
            RecommendationRefreshBus.markDirty()
            return true
        }
        return false
    }

    suspend fun getPreferences(): RecommendationPreferences {
        if (FirebaseAuth.getInstance().currentUser == null) return RecommendationPreferences()
        val response = FoodHubClient.api.list("recommendations/preferences")
        if (!response.isSuccessful) return RecommendationPreferences()
        val data = response.body()?.getAsJsonObject("data") ?: return RecommendationPreferences()
        return FoodHubClient.gson.fromJson(data, RecommendationPreferences::class.java)
    }

    suspend fun updatePreferences(enabled: Boolean): RecommendationPreferences {
        val payload = JsonObject().apply { addProperty("searchPersonalizationEnabled", enabled) }
        val response = FoodHubClient.api.update("recommendations", "preferences", payload)
        if (!response.isSuccessful) throw IOException("Không cập nhật được cài đặt cá nhân hóa (HTTP ${response.code()})")
        RecommendationRefreshBus.markDirty()
        val data = response.body()?.getAsJsonObject("data") ?: return RecommendationPreferences(enabled)
        return FoodHubClient.gson.fromJson(data, RecommendationPreferences::class.java)
    }
}
