package com.example.dacs_3_composer.data.repository

import com.example.dacs_3_composer.data.cache.LocalJsonCache
import com.example.dacs_3_composer.data.remote.FoodHubClient
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.JsonArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

data class CachedList<T>(
    val items: List<T> = emptyList(),
    val refreshing: Boolean = false,
    val updatedAt: Long? = null,
    val refreshError: Boolean = false,
    val stale: Boolean = false
)

data class CachedObject<T>(
    val value: T? = null,
    val refreshing: Boolean = false,
    val updatedAt: Long? = null,
    val refreshError: Boolean = false,
    val stale: Boolean = false
)

/** Emits account-scoped local data first, then revalidates through the existing API contract. */
object StaleListRepository {
    private val cache by lazy { LocalJsonCache.get() }

    fun <T : Any> observe(
        resource: String,
        itemClass: Class<T>,
        filters: Map<String, String> = emptyMap(),
        ttlMs: Long = ttlFor(resource)
    ): Flow<CachedList<T>> = flow {
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        val filterKey = filters.toSortedMap().entries.joinToString("&") { "${it.key}=${it.value}" }
        val key = "$uid:$resource:$filterKey"
        val cached = withContext(Dispatchers.IO) { cache.read(key) }
        var current = emptyList<T>()
        var savedAt: Long? = null
        if (cached != null) {
            current = decode(cached.payload, itemClass)
            savedAt = cached.savedAt
        }
        emit(CachedList(current, refreshing = true, updatedAt = savedAt, stale = savedAt == null || System.currentTimeMillis() - savedAt > ttlMs))

        try {
            val fresh = withContext(Dispatchers.IO) {
                val response = FoodHubClient.api.list(resource, filters)
                if (!response.isSuccessful) error("API lỗi ${response.code()}")
                val rows = response.body()?.getAsJsonArray("data") ?: JsonArray()
                rows.map { FoodHubClient.gson.fromJson(it, itemClass) }
            }
            val now = System.currentTimeMillis()
            withContext(Dispatchers.IO) { cache.write(key, FoodHubClient.gson.toJson(fresh), now) }
            emit(CachedList(fresh, refreshing = false, updatedAt = now, stale = false))
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emit(CachedList(current, refreshing = false, updatedAt = savedAt, refreshError = true, stale = current.isNotEmpty()))
        }
    }

    fun <T : Any> observeObject(
        resource: String,
        id: String,
        itemClass: Class<T>,
        ttlMs: Long = 5 * 60_000L
    ): Flow<CachedObject<T>> = flow {
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        val key = "$uid:$resource:item:$id"
        val cached = withContext(Dispatchers.IO) { cache.read(key) }
        var current = cached?.let { runCatching { FoodHubClient.gson.fromJson(it.payload, itemClass) }.getOrNull() }
        var savedAt = cached?.savedAt
        emit(CachedObject(current, refreshing = true, updatedAt = savedAt, stale = savedAt == null || System.currentTimeMillis() - savedAt > ttlMs))
        try {
            val fresh = withContext(Dispatchers.IO) {
                val response = FoodHubClient.api.get(resource, id)
                if (!response.isSuccessful) error("API lỗi ${response.code()}")
                val data = response.body()?.get("data") ?: error("API không trả dữ liệu")
                FoodHubClient.gson.fromJson(data, itemClass)
            }
            val now = System.currentTimeMillis()
            withContext(Dispatchers.IO) { cache.write(key, FoodHubClient.gson.toJson(fresh), now) }
            current = fresh
            savedAt = now
            emit(CachedObject(fresh, refreshing = false, updatedAt = savedAt, stale = false))
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emit(CachedObject(current, refreshing = false, updatedAt = savedAt, refreshError = true, stale = current != null))
        }
    }

    private fun <T> decode(json: String, itemClass: Class<T>): List<T> = try {
        val array = FoodHubClient.gson.fromJson(json, JsonArray::class.java) ?: JsonArray()
        array.map { FoodHubClient.gson.fromJson(it, itemClass) }
    } catch (_: Exception) { emptyList() }

    private fun ttlFor(resource: String): Long = when (resource) {
        "orders" -> 30_000L
        "stores", "dishes", "categories" -> 5 * 60_000L
        else -> 60_000L
    }
}
