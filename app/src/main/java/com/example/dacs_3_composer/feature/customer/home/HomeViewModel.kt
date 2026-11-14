package com.example.dacs_3_composer.feature.customer.home

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dacs_3_composer.core.model.Dish
import com.example.dacs_3_composer.core.model.RestaurantDetail
import com.example.dacs_3_composer.data.remote.FoodHubClient
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    private val _restaurantsList = MutableStateFlow<List<RestaurantDetail>>(emptyList())
    val restaurantsList: StateFlow<List<RestaurantDetail>> = _restaurantsList.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _deliciousDishes = MutableStateFlow<List<Dish>>(emptyList())
    val deliciousDishes: StateFlow<List<Dish>> = _deliciousDishes.asStateFlow()

    init {
        loadUserData() // 🌟 Cho chạy ngay từ đầu để thông tin cá nhân nạp nhanh hơn
        fetchDeliciousDishes()
        fetchAllRestaurants()
    }

    // --- QUẢN LÝ THÔNG TIN NGƯỜI DÙNG ---
    var userName by mutableStateOf("Khách")
        private set

    var userAddress by mutableStateOf("")
        private set

    // 🌟 THÊM BIẾN NÀY ĐỂ LƯU LINK AVATAR NGƯỜI DÙNG
    var userImageUrl by mutableStateOf("")
        private set

    fun loadUserData() {
        if (FirebaseAuth.getInstance().currentUser == null) return
        viewModelScope.launch {
            try {
                val user = FoodHubClient.get<com.example.dacs_3_composer.core.model.User>("users", "me")
                userName = user.getDisplayName()
                userImageUrl = user.avatarUrl
                val addresses = FoodHubClient.list<com.example.dacs_3_composer.core.model.UserAddress>("addresses")
                userAddress = addresses.firstOrNull { it.isDefault }?.addressDetail
                    ?: addresses.firstOrNull()?.addressDetail.orEmpty()
            } catch (exception: Exception) {
                Log.e("HomeViewModel", "Lỗi lấy hồ sơ từ MySQL: ${exception.message}", exception)
            }
        }
    }

    private fun fetchDeliciousDishes() {
        viewModelScope.launch {
            try { _deliciousDishes.value = FoodHubClient.list("dishes") }
            catch (e: Exception) { Log.e("HomeViewModel", "Lỗi tải món MySQL: ${e.message}", e) }
        }
    }

    private fun fetchAllRestaurants() {
        _isLoading.value = true

        viewModelScope.launch {
            try { _restaurantsList.value = FoodHubClient.list("stores") }
            catch (exception: Exception) { Log.e("HomeViewModel", "Lỗi tải cửa hàng từ MySQL: ${exception.message}", exception) }
            _isLoading.value = false
        }
    }
}
