package com.example.dacs_3_composer.data.remote

import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
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
import java.util.concurrent.TimeUnit
import java.lang.reflect.Type
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

interface FoodHubApi {
    @GET("{resource}") suspend fun list(@Path(value = "resource", encoded = true) resource: String, @QueryMap filters: Map<String, String> = emptyMap()): Response<JsonObject>
    @GET("{resource}/{id}") suspend fun get(@Path(value = "resource", encoded = true) resource: String, @Path(value = "id", encoded = true) id: String): Response<JsonObject>
    @POST("{resource}") suspend fun create(@Path(value = "resource", encoded = true) resource: String, @Body payload: JsonObject): Response<JsonObject>
    @PATCH("{resource}/{id}") suspend fun update(@Path(value = "resource", encoded = true) resource: String, @Path(value = "id", encoded = true) id: String, @Body payload: JsonObject): Response<JsonObject>
    @DELETE("{resource}/{id}") suspend fun delete(@Path(value = "resource", encoded = true) resource: String, @Path(value = "id", encoded = true) id: String): Response<JsonObject>
}

object FoodHubClient {
    // Hosted API: device requests no longer depend on the development computer.
    const val BASE_URL = "https://chinh.alwaysdata.net/food-delivery-api/api/"
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
        val authInterceptor = FirebaseAuthInterceptor {
            FirebaseAuth.getInstance().currentUser?.let { user ->
                val idToken = Tasks.await(user.getIdToken(false), 15, TimeUnit.SECONDS).token
                if (idToken.isNullOrBlank()) throw IOException("Firebase không trả về ID token cho tài khoản hiện tại")
                idToken
            }
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        Retrofit.Builder().baseUrl(BASE_URL).client(client)
            .addConverterFactory(GsonConverterFactory.create(gson)).build()
            .create(FoodHubApi::class.java)
    }

    suspend inline fun <reified T> list(resource: String, filters: Map<String, String> = emptyMap()): List<T> {
        val response = api.list(resource, filters)
        if (!response.isSuccessful) error(response.errorBody()?.string() ?: "API lỗi ${response.code()}")
        val array = response.body()?.getAsJsonArray("data") ?: JsonArray()
        return array.map { gson.fromJson(it, T::class.java) }
    }

    suspend inline fun <reified T> get(resource: String, id: String): T {
        val response = api.get(resource, id)
        if (!response.isSuccessful) error(response.errorBody()?.string() ?: "API lỗi ${response.code()}")
        val value = response.body()?.get("data") ?: error("API không trả dữ liệu")
        return gson.fromJson(value, T::class.java)
    }

    suspend inline fun <reified T> create(resource: String, value: T): String {
        val response = api.create(resource, gson.toJsonTree(value).asJsonObject)
        if (!response.isSuccessful) error(response.errorBody()?.string() ?: "API lỗi ${response.code()}")
        return response.body()?.get("id")?.asString ?: ""
    }

    suspend inline fun <reified T> update(resource: String, id: String, value: T) {
        val response = api.update(resource, id, gson.toJsonTree(value).asJsonObject)
        if (!response.isSuccessful) error(response.errorBody()?.string() ?: "API lỗi ${response.code()}")
    }

    suspend fun delete(resource: String, id: String) {
        val response = api.delete(resource, id)
        if (!response.isSuccessful) error(response.errorBody()?.string() ?: "API lỗi ${response.code()}")
    }
}
