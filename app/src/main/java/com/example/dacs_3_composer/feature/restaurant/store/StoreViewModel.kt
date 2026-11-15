package com.example.dacs_3_composer.feature.restaurant.store

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dacs_3_composer.core.model.DishItem
import com.example.dacs_3_composer.core.model.RestaurantDetail
import com.example.dacs_3_composer.data.repository.MenuRepository
import com.example.dacs_3_composer.data.repository.RestaurantStoreRepository
import com.example.dacs_3_composer.data.remote.FoodHubClient
import kotlinx.coroutines.launch

class StoreViewModel : ViewModel() {
    private val repository = MenuRepository()
    private val TAG = "StoreViewModel"

    var restaurantDetail = mutableStateOf<RestaurantDetail?>(null)
    var dishesList = mutableStateOf<List<DishItem>>(emptyList())
    var isLoading = mutableStateOf(false)
    var isSaving = mutableStateOf(false)
    var error = mutableStateOf<String?>(null)

    fun fetchStoreData(restaurantId: String) {
        if (restaurantId.isEmpty()) return

        isLoading.value = true

        viewModelScope.launch {
            try {
                val store = RestaurantStoreRepository.ensureStore()
                restaurantDetail.value = store
                isLoading.value = false
                repository.getDishes(store.id).collect { dishList ->
                    val availableDishes = dishList.filter { it.available == true }
                    dishesList.value = availableDishes.map { dish ->
                        DishItem(
                            id = dish.id,
                            name = dish.name,
                            price = dish.price,
                            description = dish.description,
                            imageUrl = dish.imageUrl,
                            category = dish.category
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi tải cửa hàng/thực đơn MySQL: ${e.message}")
                error.value = "Không tải được cửa hàng: ${e.message}"
            } finally { isLoading.value = false }
        }
    }

    fun updateRestaurantInfo(details: RestaurantDetail, logoUri: Uri?, coverUri: Uri?, onSuccess: () -> Unit) {
        if (details.name.isBlank()) { error.value = "Vui lòng nhập tên quán"; return }
        val validTime = Regex("^(?:[01][0-9]|2[0-3]):[0-5][0-9](?::[0-5][0-9])?$")
        if (!validTime.matches(details.openTime) || !validTime.matches(details.closeTime)) {
            error.value = "Giờ mở và đóng cửa phải theo dạng HH:mm"
            return
        }
        isSaving.value = true
        viewModelScope.launch {
            try {
                val store = RestaurantStoreRepository.ensureStore()
                var payload = details.copy(id = store.id)
                if (logoUri != null) {
                    val url = repository.uploadImage(logoUri, "ml_default")
                    if (url.isBlank()) throw IllegalStateException("Cloudinary không trả về ảnh đại diện")
                    payload = payload.copy(avatarUrl = url)
                }
                if (coverUri != null) {
                    val url = repository.uploadImage(coverUri, "ml_default")
                    if (url.isBlank()) throw IllegalStateException("Cloudinary không trả về ảnh bìa")
                    payload = payload.copy(coverImage = url)
                }
                FoodHubClient.update("stores", "mine", payload)
                restaurantDetail.value = FoodHubClient.get("stores", "mine")
                onSuccess()
            } catch (e: Exception) {
                error.value = "Không lưu được cửa hàng: ${e.message}"
                Log.e(TAG, "Lỗi cập nhật cửa hàng", e)
            } finally { isSaving.value = false }
        }
    }

    // Fix lỗi Unresolved reference 'updateCoverImage'
    fun updateCoverImage(
        restaurantId: String,
        newCoverUrl: String,
        onSuccess: () -> Unit
    ) {
        if (newCoverUrl.isEmpty()) return
        viewModelScope.launch { runCatching { RestaurantStoreRepository.ensureStore(); FoodHubClient.update("stores", "mine", mapOf("coverImage" to newCoverUrl)); restaurantDetail.value=FoodHubClient.get("stores", "mine") }.onSuccess { onSuccess(); Log.d(TAG, "Cover image updated successfully") }.onFailure { error.value="Không lưu được ảnh bìa: ${it.message}"; Log.e(TAG, "Lỗi lưu ảnh cửa hàng: ${it.message}") } }
    }

    fun uploadCoverToCloudinary(
        imageUri: Uri,
        restaurantId: String,
        uploadPreset: String = "ml_default",
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                val uploadedUrl = repository.uploadImage(imageUri, uploadPreset)
                if (uploadedUrl.isNotBlank()) {
                    RestaurantStoreRepository.ensureStore()
                    FoodHubClient.update("stores", "mine", mapOf("coverImage" to uploadedUrl))
                    restaurantDetail.value = FoodHubClient.get("stores", "mine")
                    onSuccess()
                } else {
                    error.value = "Cloudinary không trả về URL ảnh bìa"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Upload failed: ${e.message}")
                error.value = "Không tải hoặc lưu được ảnh bìa: ${e.message}"
            }
        }
    }
}
