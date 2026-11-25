package com.example.dacs_3_composer.data.remote

import com.google.gson.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.QueryMap

/** Typed API groups introduced alongside FoodHubApi; legacy callers remain compatible for now. */
interface AuthApi {
    @GET("users/me")
    suspend fun getMyProfile(): Response<JsonObject>

    // The current client registration flow posts to /users; retain that contract here.
    @POST("users")
    suspend fun createProfile(@Body payload: JsonObject): Response<JsonObject>
}

interface OrderApi {
    @GET("orders")
    suspend fun listOrders(@QueryMap filters: Map<String, String> = emptyMap()): Response<JsonObject>

    @GET("orders/{id}")
    suspend fun getOrder(@Path("id") id: String): Response<JsonObject>

    @POST("orders")
    suspend fun createOrder(@Body payload: JsonObject): Response<JsonObject>

    @PATCH("orders/{id}")
    suspend fun updateOrder(@Path("id") id: String, @Body payload: JsonObject): Response<JsonObject>

    @POST("deliveries/{orderId}/accept")
    suspend fun acceptDelivery(@Path("orderId") orderId: String): Response<JsonObject>
}

interface RestaurantApi {
    @GET("stores")
    suspend fun listStores(@QueryMap filters: Map<String, String> = emptyMap()): Response<JsonObject>

    @GET("stores/{id}")
    suspend fun getStore(@Path("id") id: String): Response<JsonObject>

    @GET("stores/mine")
    suspend fun getMyStore(): Response<JsonObject>

    @GET("dishes")
    suspend fun listDishes(@QueryMap filters: Map<String, String> = emptyMap()): Response<JsonObject>

    @POST("dishes")
    suspend fun createDish(@Body payload: JsonObject): Response<JsonObject>

    @PATCH("dishes/{id}")
    suspend fun updateDish(@Path("id") id: String, @Body payload: JsonObject): Response<JsonObject>
}
