package com.example.dacs_3_composer.feature.customer.order

import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dacs_3_composer.core.model.Order
import com.example.dacs_3_composer.core.model.OrderStatus
import com.example.dacs_3_composer.core.model.RestaurantDetail
import com.example.dacs_3_composer.data.remote.FoodHubClient
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class OrderViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()

    private val _ongoingOrders = MutableStateFlow<List<Order>>(emptyList())
    val ongoingOrders: StateFlow<List<Order>> = _ongoingOrders.asStateFlow()

    private val _historyOrders = MutableStateFlow<List<Order>>(emptyList())
    val historyOrders: StateFlow<List<Order>> = _historyOrders.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val restaurantImages = mutableStateMapOf<String, String>()

    private val _currentTrackingOrder = MutableStateFlow<Order?>(null)
    val currentTrackingOrder: StateFlow<Order?> = _currentTrackingOrder.asStateFlow()

    init {
        observeUserOrders()
    }

    fun observeUserOrders() {
        val uid = auth.currentUser?.uid ?: return
        _isLoading.value = true

        viewModelScope.launch {
            while (true) {
                try {
                    val allOrders = FoodHubClient.list<Order>("orders")
                    _ongoingOrders.value = allOrders.filter { it.status in setOf("PENDING_PAYMENT","PENDING","PROCESSING","ACCEPTED","SHIPPING") }.sortedByDescending { it.time }
                    _historyOrders.value = allOrders.filter { it.status in setOf("COMPLETED","CANCELLED") }.sortedByDescending { it.time }
                    allOrders.forEach { order -> if (order.restaurantId.isNotBlank() && !restaurantImages.containsKey(order.restaurantId)) fetchRestaurantImage(order.restaurantId) }
                } catch (e: Exception) { Log.e("OrderViewModel", "Lỗi tải đơn MySQL: ${e.message}", e) }
                _isLoading.value = false
                delay(5_000)
            }
        }
    }

    // ✅ CẬP NHẬT: Đánh dấu đã thanh toán và chuyển trạng thái cho nhà hàng
    fun updateOrderToPaid(orderId: String) {
        if (orderId.isBlank()) return
        // Trạng thái đã thanh toán phải đến từ PayOS callback xác thực ở backend.
        Log.d("OrderViewModel", "Đợi backend xác nhận thanh toán cho đơn $orderId")
    }

    fun cancelOrder(orderId: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        if (orderId.isBlank()) return

        viewModelScope.launch {
            runCatching { FoodHubClient.update("orders", orderId, mapOf("status" to "CANCELLED")) }
                .onSuccess { onSuccess() }
                .onFailure { onFailure(it.message ?: "Lỗi hủy đơn MySQL") }
        }
    }

    fun observeOrderDetails(orderId: String) {
        if (orderId.isBlank()) return

        viewModelScope.launch {
            while (true) {
                runCatching { FoodHubClient.get<Order>("orders", orderId) }
                    .onSuccess { _currentTrackingOrder.value = it }
                    .onFailure { Log.e("OrderViewModel", "Lỗi tải đơn $orderId: ${it.message}") }
                delay(3_000)
            }
        }
    }

    private fun fetchRestaurantImage(restaurantId: String) {
        viewModelScope.launch {
            runCatching { FoodHubClient.get<RestaurantDetail>("stores", restaurantId) }
                .onSuccess { if (it.coverImage.isNotBlank()) restaurantImages[restaurantId] = it.coverImage }
                .onFailure { Log.e("OrderViewModel", "Lỗi lấy ảnh quán $restaurantId: ${it.message}") }
        }
    }
}
