package com.example.dacs_3_composer.feature.admin.profile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dacs_3_composer.core.model.User
import com.example.dacs_3_composer.data.remote.FoodHubClient
import com.example.dacs_3_composer.data.remote.ImageUploadService
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val uid: String = "",
    val adminName: String = "",
    val adminEmail: String = "",
    val adminPhone: String = "",
    val avatarUrl: String = "",
    val totalUsersCount: String = "0",
    val totalRestaurants: Int = 0,
    val totalShippers: Int = 0,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)

class AdminProfileViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState = _uiState.asStateFlow()
    private var loadJob: Job? = null

    init { loadProfile() }

    fun loadProfile() {
        if (loadJob?.isActive == true || _uiState.value.isSaving) return
        loadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                coroutineScope {
                    val profile = async { FoodHubClient.get<User>("users", "me") }
                    val accounts = async { FoodHubClient.list<User>("users") }
                    val user = profile.await()
                    val users = accounts.await()
                    _uiState.value = _uiState.value.copy(
                        uid = user.uid, adminName = user.getDisplayName(), adminEmail = user.email,
                        adminPhone = user.phoneNumber.ifBlank { user.phone }, avatarUrl = user.avatarUrl,
                        totalUsersCount = users.size.toString(),
                        totalRestaurants = users.count { it.role in setOf("restaurant", "seller") },
                        totalShippers = users.count { it.role in setOf("shipper", "driver") },
                        isLoading = false
                    )
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "Không tải được hồ sơ. Hãy kiểm tra kết nối và thử lại.")
            }
        }
    }

    fun uploadAvatarToCloudinary(context: Context, fileUri: Uri, onSuccess: (String) -> Unit, onFailure: (Exception) -> Unit) {
        if (_uiState.value.isSaving) return
        loadJob?.cancel()
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, isLoading = false, errorMessage = null)
            try {
                val url = ImageUploadService.uploadImage(context.applicationContext, fileUri)
                FoodHubClient.update("users", "me", mapOf("avatarUrl" to url))
                _uiState.value = _uiState.value.copy(avatarUrl = url)
                onSuccess(url)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Không lưu được ảnh đại diện. Hãy thử lại.")
                onFailure(e)
            } finally { _uiState.value = _uiState.value.copy(isSaving = false) }
        }
    }

    fun updateAccountInfo(newName: String, newPhone: String, onComplete: (Boolean) -> Unit) {
        if (_uiState.value.isSaving) return
        val name = newName.trim()
        val phone = newPhone.trim()
        if (name.isBlank() || name.length > 160 || phone.length > 32) {
            _uiState.value = _uiState.value.copy(errorMessage = "Hãy nhập họ tên hợp lệ và số điện thoại không quá 32 ký tự.")
            onComplete(false)
            return
        }
        loadJob?.cancel()
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, isLoading = false, errorMessage = null)
            try {
                // Partial fields avoid the legacy fullName/name and phoneNumber/phone alias conflict.
                FoodHubClient.update("users", "me", mapOf("name" to name, "phone" to phone))
                _uiState.value = _uiState.value.copy(adminName = name, adminPhone = phone)
                onComplete(true)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Không lưu được thông tin. Hãy thử lại.")
                onComplete(false)
            } finally { _uiState.value = _uiState.value.copy(isSaving = false) }
        }
    }

    fun logout(onSuccess: () -> Unit) {
        if (_uiState.value.isSaving) return
        loadJob?.cancel()
        auth.signOut()
        _uiState.value = ProfileUiState()
        onSuccess()
    }
}
