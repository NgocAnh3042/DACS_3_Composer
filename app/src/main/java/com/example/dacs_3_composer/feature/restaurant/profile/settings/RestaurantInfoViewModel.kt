package com.example.dacs_3_composer.feature.restaurant.profile.settings

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dacs_3_composer.core.model.RestaurantDetail
import com.example.dacs_3_composer.data.repository.MenuRepository
import com.example.dacs_3_composer.data.repository.RestaurantStoreRepository
import com.example.dacs_3_composer.data.repository.ActivityLogRepository
import com.example.dacs_3_composer.data.remote.FoodHubClient
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class RestaurantInfoViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val repository = MenuRepository()
    private val activityLogRepository = ActivityLogRepository()
    private val TAG = "RestaurantInfoVM"

    private val restaurantId: String
        get() = auth.currentUser?.uid ?: ""

    var restaurantDetail by mutableStateOf<RestaurantDetail?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isSaving by mutableStateOf(false)
        private set
    var saveSuccess by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        fetchRestaurantData()
    }

    fun fetchRestaurantData() {
        if (restaurantId.isBlank()) return
        viewModelScope.launch { runCatching { RestaurantStoreRepository.ensureStore() }
            .onSuccess { restaurantDetail = it }
            .onFailure { error = "Không tải được cửa hàng: ${it.message}" } }
    }

    fun updateRestaurantInfo(
        name: String,
        email: String,
        phone: String,       // 🌟 Đã khớp với biến 'phone' của bạn
        address: String,
        latitude: Double,    // 🌟 Nhận tọa độ số từ Map kéo thả
        longitude: Double,   // 🌟 Nhận tọa độ số từ Map kéo thả
        openTime: String,
        closeTime: String,
        description: String,
        avatarUri: Uri? = null,
        coverUri: Uri? = null
    ) {
        if (restaurantId.isBlank()) return
        viewModelScope.launch {
            isSaving = true
            saveSuccess = false
            error = null
            try {
                val store = RestaurantStoreRepository.ensureStore()
                var finalAvatarUrl = restaurantDetail?.avatarUrl ?: ""
                var finalCoverUrl = restaurantDetail?.coverImage ?: ""

                if (avatarUri != null) {
                    val uploadedAvatar = repository.uploadImage(avatarUri, "ml_default")
                    if (uploadedAvatar.isBlank()) throw IllegalStateException("Không tải được ảnh đại diện lên Cloudinary")
                    finalAvatarUrl = uploadedAvatar
                }

                if (coverUri != null) {
                    val uploadedCover = repository.uploadImage(coverUri, "ml_default")
                    if (uploadedCover.isBlank()) throw IllegalStateException("Không tải được ảnh bìa lên Cloudinary")
                    finalCoverUrl = uploadedCover
                }

                // 🌟 Các key trong Map này phải trùng khớp 100% với thuộc tính trong Model của bạn
                val payload = store.copy(name=name,email=email,phone=phone,address=address,latitude=latitude,longitude=longitude,openTime=openTime.ifBlank { store.openTime },closeTime=closeTime.ifBlank { store.closeTime },description=description,avatarUrl=finalAvatarUrl,coverImage=finalCoverUrl)
                FoodHubClient.update("stores", "mine", payload)
                restaurantDetail = FoodHubClient.get("stores", "mine")

                activityLogRepository.logActivity(
                    restaurantId = restaurantId,
                    type = "profile",
                    title = "Chỉnh sửa hồ sơ",
                    description = "Cập nhật vị trí định vị và thông tin liên hệ của quán"
                )

                saveSuccess = true
            } catch (e: Exception) {
                Log.e(TAG, "Error updating restaurant info: ${e.message}")
                error = "Không lưu được thông tin quán: ${e.message}"
            } finally {
                isSaving = false
            }
        }
    }

    fun resetSaveSuccess() {
        saveSuccess = false
    }
    fun clearError() { error = null }
}
