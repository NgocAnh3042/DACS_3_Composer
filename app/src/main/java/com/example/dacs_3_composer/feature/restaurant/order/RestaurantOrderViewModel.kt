package com.example.dacs_3_composer.feature.restaurant.order

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dacs_3_composer.core.model.Order
import com.example.dacs_3_composer.core.model.OrderStatus
import com.example.dacs_3_composer.data.repository.ActivityLogRepository
import com.example.dacs_3_composer.data.remote.FoodHubClient
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class RestaurantOrderViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val activityLogRepository = ActivityLogRepository()

    private val _orders = MutableStateFlow<List<Order>>(emptyList())
    val orders: StateFlow<List<Order>> = _orders

    var isLoading by mutableStateOf(false)
        private set

    private val currentRestaurantId: String
        get() = auth.currentUser?.uid ?: ""

    init {
        listenToRestaurantOrders()
    }

    private fun listenToRestaurantOrders() {
        if (currentRestaurantId.isBlank()) return
        isLoading = true
        viewModelScope.launch {
            while (true) {
                runCatching { FoodHubClient.list<Order>("orders") }
                    .onSuccess { _orders.value = it.filter { order -> order.status != OrderStatus.PENDING_PAYMENT.name }.sortedByDescending { order -> order.id } }
                    .onFailure { Log.e("RestaurantOrderVM", "Lỗi tải đơn MySQL: ${it.message}") }
                isLoading = false
                delay(4_000)
            }
        }
    }

    fun updateOrderStatus(orderId: String, nextStatus: OrderStatus) {
        if (orderId.isBlank()) return

        viewModelScope.launch {
            try {
                FoodHubClient.update("orders", orderId, mapOf("status" to nextStatus.name))

                val statusMsg = when(nextStatus) {
                    OrderStatus.PROCESSING -> "Đã xác nhận đơn hàng"
                    OrderStatus.SHIPPING -> "Đã bàn giao cho Shipper"
                    OrderStatus.COMPLETED -> "Đã hoàn tất đơn hàng"
                    OrderStatus.CANCELLED -> "Đã hủy đơn hàng"
                    else -> "Cập nhật trạng thái đơn hàng"
                }

                activityLogRepository.logActivity(
                    restaurantId = currentRestaurantId,
                    type = "order",
                    title = statusMsg,
                    description = "Đơn hàng #$orderId",
                    extraInfo = mapOf("orderId" to orderId)
                )

            } catch (e: Exception) {
                Log.e("RestaurantOrderVM", "Lỗi cập nhật trạng thái đơn $orderId", e)
            }
        }
    }
}
