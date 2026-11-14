package com.example.dacs_3_composer.feature.shipper.profile

import android.util.Log
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dacs_3_composer.core.model.User
import com.example.dacs_3_composer.data.repository.UserRepository
import com.example.dacs_3_composer.data.repository.MenuRepository
import com.example.dacs_3_composer.data.remote.FoodHubClient
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private data class DriverProfile(val vehicleName: String = "")

class ShipperProfileViewModel : ViewModel() {

    private val repository = UserRepository()
    private val imageRepository = MenuRepository()
    private val auth = FirebaseAuth.getInstance()

    private val _user = MutableStateFlow(User())
    val user: StateFlow<User> = _user

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _totalOrders = MutableStateFlow(0)
    val totalOrders: StateFlow<Int> = _totalOrders

    private val _totalEarnings = MutableStateFlow(0.0)
    val totalEarnings: StateFlow<Double> = _totalEarnings

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _avatarPreview = MutableStateFlow<Uri?>(null)
    val avatarPreview: StateFlow<Uri?> = _avatarPreview

    init {
        loadProfile()
        observeShipperStats()
    }

    fun loadProfile() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val profile = repository.getCurrentUser()
                val driver = runCatching { FoodHubClient.get<DriverProfile>("drivers", "me") }.getOrNull()
                _user.value = profile.copy(vehicleName = driver?.vehicleName.orEmpty())
            } catch (e: Exception) {
                Log.e("Profile", "Lỗi tải thông tin: ${e.message}")
                _errorMessage.value = "Không tải được hồ sơ tài xế. Vui lòng thử lại."
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun observeShipperStats() {
        val uid = auth.currentUser?.uid ?: return

        viewModelScope.launch {
            while (true) {
                runCatching { FoodHubClient.list<com.example.dacs_3_composer.core.model.Order>("orders") }
                    .onSuccess { orders -> val completed=orders.filter { it.status=="COMPLETED" };_totalOrders.value=completed.size;_totalEarnings.value=completed.sumOf { it.shippingFee } }
                    .onFailure { Log.e("Stats", "Lỗi tải thống kê MySQL: ${it.message}") }
                kotlinx.coroutines.delay(10_000)
            }
        }
    }

    fun updateProfile(
        name: String,
        phone: String,
        address: String,
        vehicleName: String,
        imageUri: Uri?,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                var avatarUrl = _user.value.avatarUrl

                if (imageUri != null) {
                    avatarUrl = imageRepository.uploadImage(imageUri, "ml_default")
                }

                val updatedUser = _user.value.copy(
                    fullName = name.trim(),
                    name = name,
                    phone = phone,
                    address = address,
                    vehicleName = vehicleName,
                    avatarUrl = avatarUrl
                )

                FoodHubClient.update("users", "me", mapOf(
                    "name" to name.trim(), "phone" to phone.trim(),
                    "address" to address.trim(), "avatarUrl" to avatarUrl
                ))
                if (vehicleName != _user.value.vehicleName) {
                    FoodHubClient.update("drivers", "me", mapOf("vehicleName" to vehicleName.trim()))
                }
                _user.value = updatedUser
                _avatarPreview.value = null
                onDone()
            } catch (e: Exception) {
                Log.e("UpdateProfile", "Lỗi lưu dữ liệu: ${e.message}")
                _errorMessage.value = e.localizedMessage ?: "Cập nhật thất bại, vui lòng kiểm tra lại!"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun changeAvatar(uri: Uri) {
        if (_isLoading.value) return
        _avatarPreview.value = uri
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val uploadedUrl = imageRepository.uploadImage(uri, "ml_default")
                FoodHubClient.update("users", "me", mapOf("avatarUrl" to uploadedUrl))
                _user.value = _user.value.copy(avatarUrl = uploadedUrl)
            } catch (e: Exception) {
                Log.e("ShipperAvatar", "Không lưu được ảnh đại diện", e)
                _errorMessage.value = e.localizedMessage ?: "Không cập nhật được ảnh đại diện"
            } finally {
                _avatarPreview.value = null
                _isLoading.value = false
            }
        }
    }
    fun logout(onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                // 1. Gọi lệnh đăng xuất của Firebase Auth
                auth.signOut()

                // 2. Xóa sạch dữ liệu User trong StateFlow về mặc định
                _user.value = User()
                _totalOrders.value = 0
                _totalEarnings.value = 0.0

                // 3. Kích hoạt callback để chuyển màn hình trên UI
                onSuccess()
            } catch (e: Exception) {
                Log.e("Logout", "Lỗi khi đăng xuất: ${e.message}")
                _errorMessage.value = "Không thể đăng xuất, vui lòng thử lại!"
            }
        }
    }
}
