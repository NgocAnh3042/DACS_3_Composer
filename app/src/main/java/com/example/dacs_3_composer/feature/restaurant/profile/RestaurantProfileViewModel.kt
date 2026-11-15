package com.example.dacs_3_composer.feature.restaurant.profile

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dacs_3_composer.data.repository.MenuRepository
import com.example.dacs_3_composer.data.repository.RestaurantStoreRepository
import com.example.dacs_3_composer.data.remote.FoodHubClient
import com.example.dacs_3_composer.core.model.User
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class RestaurantProfileViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val menuRepository = MenuRepository()
    private val TAG = "RestaurantProfileVM"

    var name by mutableStateOf("")
        private set
    var email by mutableStateOf("")
        private set
    var avatarUrl by mutableStateOf("")
        private set

    // 🎯 THÊM BIẾN LƯU LINK ẢNH BÌA
    var coverUrl by mutableStateOf("")
        private set

    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private val currentUserId: String
        get() = auth.currentUser?.uid ?: ""

    init {
        loadUserProfileAndStoreData()
    }

    private fun loadUserProfileAndStoreData() {
        if (currentUserId.isBlank()) return

        viewModelScope.launch { runCatching { FoodHubClient.get<User>("users", "me") }.onSuccess { name=it.name;email=it.email;avatarUrl=it.avatarUrl }
            runCatching { RestaurantStoreRepository.ensureStore() }.onSuccess { coverUrl=it.coverImage }.onFailure { error="Không tải được cửa hàng: ${it.message}" } }
    }

    // Hàm upload Avatar (Giữ nguyên)
    fun uploadAndSaveAvatar(imageUri: Uri, uploadPreset: String = "ml_default") {
        if (currentUserId.isBlank()) return
        viewModelScope.launch {
            isLoading = true
            try {
                val uploadedUrl = menuRepository.uploadImage(imageUri, uploadPreset)
                if (uploadedUrl.isNotBlank()) {
                    val user = FoodHubClient.get<User>("users", "me")
                    FoodHubClient.update("users", "me", user.copy(avatarUrl = uploadedUrl)); avatarUrl = uploadedUrl
                }
                isLoading = false
            } catch (e: Exception) {
                error = "Không thể tải avatar: ${e.message}"
                isLoading = false
            }
        }
    }

    // 🎯 THÊM HÀM UPLOAD ẢNH BÌA LÊN CLOUDINARY VÀ LƯU VÀO COLLECTION RESTAURANTS
    fun uploadAndSaveCover(imageUri: Uri, uploadPreset: String = "ml_default") {
        if (currentUserId.isBlank()) return
        viewModelScope.launch {
            isLoading = true
            try {
                Log.d(TAG, "Đang tải ảnh bìa lên Cloudinary...")
                // 1. Đẩy file lên Cloudinary
                val uploadedUrl = menuRepository.uploadImage(imageUri, uploadPreset)
                if (uploadedUrl.isBlank()) throw IllegalStateException("Cloudinary không trả về URL ảnh bìa")
                val store = RestaurantStoreRepository.ensureStore()
                FoodHubClient.update("stores", "mine", store.copy(coverImage = uploadedUrl)); coverUrl = uploadedUrl
                isLoading = false
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi uploadAndSaveCover: ${e.message}")
                error = "Không thể cập nhật ảnh bìa: ${e.message}"
                isLoading = false
            }
        }
    }

    fun updateName(newName: String) {
        if (currentUserId.isBlank() || newName.isBlank()) return
        viewModelScope.launch {
            try {
                val user = FoodHubClient.get<User>("users", "me")
                FoodHubClient.update("users", "me", user.copy(name = newName)); name = newName
            } catch (e: Exception) {
                error = "Không thể cập nhật tên: ${e.message}"
            }
        }
    }
    fun clearError() { error = null }
}
