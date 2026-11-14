package com.example.dacs_3_composer.feature.admin.settings

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dacs_3_composer.data.repository.MenuRepository
import com.example.dacs_3_composer.data.remote.FoodHubClient
import com.example.dacs_3_composer.core.model.User
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class AdminProfileViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val menuRepository = MenuRepository()
    private val TAG = "AdminProfileVM"

    var name by mutableStateOf("")
        private set
    var email by mutableStateOf("")
        private set
    var avatarUrl by mutableStateOf("")
        private set
    var coverUrl by mutableStateOf("")
        private set

    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private val currentUserId: String
        get() = auth.currentUser?.uid ?: ""

    init {
        loadAdminProfileData()
    }

    private fun loadAdminProfileData() {
        if (currentUserId.isBlank()) return

        viewModelScope.launch { runCatching { FoodHubClient.get<User>("users", "me") }.onSuccess { name=it.name.ifBlank { "Admin" }; email=it.email; avatarUrl=it.avatarUrl } }
    }

    fun uploadAndSaveAvatar(imageUri: Uri, uploadPreset: String = "ml_default") {
        if (currentUserId.isBlank()) return
        viewModelScope.launch {
            isLoading = true
            try {
                val uploadedUrl = menuRepository.uploadImage(imageUri, uploadPreset)
                if (uploadedUrl.isNotBlank()) {
                    val user=FoodHubClient.get<User>("users","me"); FoodHubClient.update("users","me",user.copy(avatarUrl=uploadedUrl));avatarUrl=uploadedUrl
                }
                isLoading = false
            } catch (e: Exception) {
                error = "Không thể tải avatar: ${e.message}"
                isLoading = false
            }
        }
    }

    fun uploadAndSaveCover(imageUri: Uri, uploadPreset: String = "ml_default") {
        if (currentUserId.isBlank()) return
        viewModelScope.launch {
            isLoading = true
            try {
                val uploadedUrl = menuRepository.uploadImage(imageUri, uploadPreset)
                if (uploadedUrl.isNotBlank()) {
                    coverUrl = uploadedUrl
                }
                isLoading = false
            } catch (e: Exception) {
                error = "Không thể cập nhật ảnh bìa: ${e.message}"
                isLoading = false
            }
        }
    }

    fun updateName(newName: String) {
        if (currentUserId.isBlank() || newName.isBlank()) return
        viewModelScope.launch {
            try {
                val user=FoodHubClient.get<User>("users","me");FoodHubClient.update("users","me",user.copy(name=newName));name=newName
            } catch (e: Exception) {
                error = "Không thể cập nhật tên: ${e.message}"
            }
        }
    }

    fun logout() {
        auth.signOut()
    }
}
