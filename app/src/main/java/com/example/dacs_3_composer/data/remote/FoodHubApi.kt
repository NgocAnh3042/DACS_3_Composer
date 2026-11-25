package com.example.dacs_3_composer.data.remote

import com.google.android.gms.tasks.Tasks
import com.example.dacs_3_composer.BuildConfig
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.QueryMap
import java.io.IOException
import java.lang.reflect.Type
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

interface FoodHubApi {
    @GET("{resource}") suspend fun list(@Path(value = "resource", encoded = true) resource: String, @QueryMap filters: Map<String, String> = emptyMap()): Response<JsonObject>
    @GET("{resource}/{id}") suspend fun get(@Path(value = "resource", encoded = true) resource: String, @Path(value = "id", encoded = true) id: String): Response<JsonObject>
    @POST("{resource}") suspend fun create(@Path(value = "resource", encoded = true) resource: String, @Body payload: JsonObject): Response<JsonObject>
    @PATCH("{resource}/{id}") suspend fun update(@Path(value = "resource", encoded = true) resource: String, @Path(value = "id", encoded = true) id: String, @Body payload: JsonObject): Response<JsonObject>
    @DELETE("{resource}/{id}") suspend fun delete(@Path(value = "resource", encoded = true) resource: String, @Path(value = "id", encoded = true) id: String): Response<JsonObject>
}

object FoodHubClient {
    // The selected product flavor supplies either the local ngrok URL or Alwaysdata.
    private val baseUrl = BuildConfig.FOODHUB_API_BASE_URL

    private data class CacheEntry(val json: JsonElement, val expiresAtMs: Long)

    private val responseCache = ConcurrentHashMap<String, CacheEntry>()

    @Volatile private var cachedTokenUid: String? = null
    @Volatile private var cachedIdToken: String? = null
    @Volatile private var cachedTokenExpiresAtMs: Long = 0L
    private val tokenLock = Any()

    private fun resolveFastIdToken(): String? {
        val user = FirebaseAuth.getInstance().currentUser ?: run {
            cachedTokenUid = null
            cachedIdToken = null
            cachedTokenExpiresAtMs = 0L
            responseCache.clear()
            return null
        }
        val uid = user.uid
        if (cachedTokenUid != null && cachedTokenUid != uid) {
            responseCache.clear()
        }
        val now = System.currentTimeMillis()
        val existing = cachedIdToken
        if (uid == cachedTokenUid && !existing.isNullOrBlank() && now < cachedTokenExpiresAtMs) {
            return existing
        }
        synchronized(tokenLock) {
            val nowLocked = System.currentTimeMillis()
            val lockedExisting = cachedIdToken
            if (uid == cachedTokenUid && !lockedExisting.isNullOrBlank() && nowLocked < cachedTokenExpiresAtMs) {
                return lockedExisting
            }
            val result = Tasks.await(user.getIdToken(false), 15, TimeUnit.SECONDS)
            val idToken = result.token
            if (idToken.isNullOrBlank()) throw IOException("Firebase không trả về ID token cho tài khoản hiện tại")
            val expSec = result.expirationTimestamp
            val validUntil = if (expSec > 0) (expSec * 1000L - 60_000L) else (nowLocked + 240_000L)
            cachedTokenUid = uid
            cachedIdToken = idToken
            cachedTokenExpiresAtMs = validUntil.coerceAtLeast(nowLocked + 30_000L)
            return idToken
        }
    }

    private fun computeCacheTtlMs(resource: String, filters: Map<String, String>): Long {
        if (filters["includeUnavailable"] == "1") return 0L
        return when (resource) {
            "stores", "dishes", "categories", "promotions", "vouchers", "addresses" -> 60_000L
            "users" -> 60_000L
            else -> 0L
        }
    }

    private fun invalidateCacheForResource(resource: String) {
        when (resource) {
            "dishes" -> responseCache.keys.removeIf { it.startsWith("dishes:") || it.startsWith("categories:") }
            "stores" -> responseCache.keys.removeIf { it.startsWith("stores:") || it.startsWith("dishes:") || it.startsWith("categories:") }
            "addresses" -> responseCache.keys.removeIf { it.startsWith("addresses:") }
            "users" -> responseCache.keys.removeIf { it.startsWith("users:") || it.startsWith("stores:") || it.startsWith("dishes:") }
            "promotions", "vouchers", "orders" -> responseCache.keys.removeIf { it.startsWith("promotions:") || it.startsWith("vouchers:") }
            else -> responseCache.keys.removeIf { it.startsWith("$resource:") }
        }
    }

    val gson: Gson = GsonBuilder()
        .registerTypeAdapter(Timestamp::class.java, object : JsonSerializer<Timestamp>, JsonDeserializer<Timestamp> {
            private val formats = listOf("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss'Z'")
            override fun serialize(src: Timestamp?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement =
                JsonPrimitive(src?.toDate()?.let { SimpleDateFormat(formats[0], Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(it) } ?: "")
            override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Timestamp {
                val text = json?.asString.orEmpty()
                val date = formats.firstNotNullOfOrNull { pattern -> runCatching { SimpleDateFormat(pattern, Locale.US).parse(text) }.getOrNull() }
                    ?: text.toLongOrNull()?.let(::Date) ?: Date()
                return Timestamp(date)
            }
        }).create()

    val api: FoodHubApi by lazy {
        val authInterceptor = FirebaseAuthInterceptor(::resolveFastIdToken)
        val client = OkHttpClient.Builder()
            .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
            .addInterceptor(authInterceptor)
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .build()
        Retrofit.Builder().baseUrl(baseUrl).client(client)
            .addConverterFactory(GsonConverterFactory.create(gson)).build()
            .create(FoodHubApi::class.java)
    }

    suspend fun <T> listTyped(resource: String, itemClass: Class<T>, filters: Map<String, String>): List<T> {
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        val ttl = computeCacheTtlMs(resource, filters)
        val cacheKey = if (ttl > 0L) "$resource:list:$uid:${filters.entries.sortedBy { it.key }.joinToString("&") { "${it.key}=${it.value}" }}" else ""
        if (ttl > 0L) {
            val hit = responseCache[cacheKey]
            if (hit != null && System.currentTimeMillis() < hit.expiresAtMs && hit.json.isJsonArray) {
                return hit.json.asJsonArray.map { gson.fromJson(it, itemClass) }
            }
        }
        val response = api.list(resource, filters)
        if (!response.isSuccessful) error(response.errorBody()?.string() ?: "API lỗi ${response.code()}")
        val array = response.body()?.getAsJsonArray("data") ?: JsonArray()
        if (ttl > 0L) {
            responseCache[cacheKey] = CacheEntry(array, System.currentTimeMillis() + ttl)
        }
        return array.map { gson.fromJson(it, itemClass) }
    }

    suspend fun <T> getTyped(resource: String, id: String, itemClass: Class<T>): T {
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        val ttl = computeCacheTtlMs(resource, emptyMap())
        val cacheKey = if (ttl > 0L) "$resource:get:$uid:$id" else ""
        if (ttl > 0L) {
            val hit = responseCache[cacheKey]
            if (hit != null && System.currentTimeMillis() < hit.expiresAtMs) {
                return gson.fromJson(hit.json, itemClass)
            }
        }
        val response = api.get(resource, id)
        if (!response.isSuccessful) error(response.errorBody()?.string() ?: "API lỗi ${response.code()}")
        val value = response.body()?.get("data") ?: error("API không trả dữ liệu")
        if (ttl > 0L) {
            responseCache[cacheKey] = CacheEntry(value, System.currentTimeMillis() + ttl)
        }
        return gson.fromJson(value, itemClass)
    }

    suspend fun createObject(resource: String, value: Any?): String {
        val response = api.create(resource, gson.toJsonTree(value).asJsonObject)
        if (!response.isSuccessful) error(response.errorBody()?.string() ?: "API lỗi ${response.code()}")
        invalidateCacheForResource(resource)
        return response.body()?.get("id")?.asString ?: ""
    }

    suspend fun updateObject(resource: String, id: String, value: Any?) {
        val response = api.update(resource, id, gson.toJsonTree(value).asJsonObject)
        if (!response.isSuccessful) error(response.errorBody()?.string() ?: "API lỗi ${response.code()}")
        invalidateCacheForResource(resource)
    }

    suspend inline fun <reified T> list(resource: String, filters: Map<String, String> = emptyMap()): List<T> =
        listTyped(resource, T::class.java, filters)

    suspend inline fun <reified T> get(resource: String, id: String): T =
        getTyped(resource, id, T::class.java)

    suspend inline fun <reified T> create(resource: String, value: T): String =
        createObject(resource, value)

    suspend inline fun <reified T> update(resource: String, id: String, value: T) =
        updateObject(resource, id, value)

    suspend fun delete(resource: String, id: String) {
        val response = api.delete(resource, id)
        if (!response.isSuccessful) error(response.errorBody()?.string() ?: "API lỗi ${response.code()}")
        invalidateCacheForResource(resource)
    }
}
