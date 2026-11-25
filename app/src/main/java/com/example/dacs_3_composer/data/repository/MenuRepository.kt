package com.example.dacs_3_composer.data.repository

import android.net.Uri
import android.util.Log
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.dacs_3_composer.core.model.Dish
import com.example.dacs_3_composer.data.remote.FoodHubClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class MenuRepository {
    private val TAG = "MenuRepository"

//    Gọi viewmodel để lấy dữ liệu (callbackFlow lắng nghe nếu tác động thảy đổi để load lại)
fun observeDishes(restaurantId: String, includeUnavailable: Boolean = false): Flow<CachedList<Dish>> = flow {
    val filters = mapOf("restaurantId" to restaurantId, "includeUnavailable" to if (includeUnavailable) "1" else "0")
    while (true) {
        try {
            StaleListRepository.observe("dishes", Dish::class.java, filters).collect { emit(it) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Không tải được thực đơn API: ${e.message}")
        }
        delay(10_000)
    }
}

fun getDishes(restaurantId: String, includeUnavailable: Boolean = false): Flow<List<Dish>> =
    observeDishes(restaurantId, includeUnavailable).map { it.items }
//  Xử lý tải ảnh lên
    suspend fun uploadImage(imageUri: Uri, uploadPreset: String): String = suspendCoroutine { continuation ->
        Log.d(TAG, "Bắt đầu upload ảnh: $imageUri với preset: $uploadPreset")
        try {
            MediaManager.get().upload(imageUri)
                .unsigned(uploadPreset)
                .callback(object : UploadCallback {
                    override fun onStart(requestId: String?) {}
                    override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                    override fun onSuccess(requestId: String?, resultData: Map<*, *>?) {
                        val url = resultData?.get("secure_url") as? String ?: ""
                        Log.d(TAG, "Upload ảnh thành công: $url")
                        continuation.resume(url)
                    }
                    override fun onError(requestId: String?, error: ErrorInfo?) {
                        val errorMsg = error?.description ?: "Unknown Cloudinary error"
                        Log.e(TAG, "Upload ảnh thất bại: $errorMsg")
                        continuation.resumeWithException(Exception(errorMsg))
                    }
                    override fun onReschedule(requestId: String?, error: ErrorInfo?) {}
                }).dispatch()
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi gọi MediaManager: ${e.message}")
            continuation.resumeWithException(e)
        }
    }

    suspend fun addDish(dish: Dish): String {
        try {
            val id = FoodHubClient.create("dishes", dish)
            if (id.isBlank()) error("API đã nhận món nhưng không trả ID MySQL")
            Log.d(TAG, "Thêm món thành công trên MySQL")
            return id
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi MySQL addDish: ${e.message}")
            throw e
        }
    }

    suspend fun updateDish(dish: Dish) {
        if (dish.id.isNotEmpty()) {
            FoodHubClient.update("dishes", dish.id, dish)
        }
    }

    suspend fun deleteDish(dishId: String) {
        FoodHubClient.delete("dishes", dishId)
    }
}
