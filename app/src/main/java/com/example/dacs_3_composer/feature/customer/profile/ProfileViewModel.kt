package com.example.dacs_3_composer.feature.customer.profile

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dacs_3_composer.data.repository.MenuRepository // 🌟 Dùng chung repository với nhà hàng
import com.example.dacs_3_composer.data.remote.FoodHubClient
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class ProfileViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val repository = MenuRepository() // 🌟 Khai báo repository giống StoreViewModel

    var userName by mutableStateOf("Đang tải...")
        private set

    var userEmail by mutableStateOf("Đang tải...")
        private set

    // Trạng thái ảnh: Lưu Uri tệp điện thoại hoặc String link URL mạng
    var avatarState by mutableStateOf<Any?>(null)
        private set

    var isUploading by mutableStateOf(false)
        private set

    fun loadUserData() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            runCatching { FoodHubClient.get<com.example.dacs_3_composer.core.model.User>("users", "me") }
                .onSuccess { user ->
                    userName = user.getDisplayName()
                    userEmail = user.email.ifBlank { auth.currentUser?.email ?: "Chưa cập nhật" }
                    avatarState = user.avatarUrl
                }
                .onFailure { Log.e("ProfileViewModel", "Lỗi tải hồ sơ MySQL: ${it.message}", it) }
        }
    }

    // 🌟 Upload ảnh lên Cloudinary giống hệt bên StoreViewModel của nhà hàng
    fun uploadAvatar(imageUri: Uri, uploadPreset: String = "ml_default") {
        val uid = auth.currentUser?.uid ?: return

        // 🚀 HIỂN THỊ LẬP TỨC: Đổi sang tệp ảnh trong điện thoại ngay trên giao diện
        avatarState = imageUri
        isUploading = true

        viewModelScope.launch {
            try {
                // 1. Đẩy ảnh lên Cloudinary lấy link về
                val uploadedUrl = repository.uploadImage(imageUri, uploadPreset)

                FoodHubClient.update("users", "me", mapOf("avatarUrl" to uploadedUrl))
                isUploading = false
                avatarState = uploadedUrl
                Log.d("ProfileViewModel", "Cập nhật avatar Cloudinary/MySQL thành công")
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Lỗi upload ảnh đại diện: ${e.message}")
                isUploading = false
            }
        }
    }
}
