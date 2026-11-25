package com.example.dacs_3_composer.feature.customer.order.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dacs_3_composer.core.model.Order
import com.example.dacs_3_composer.core.model.OrderStatus
import com.example.dacs_3_composer.data.remote.FoodHubClient
import com.example.dacs_3_composer.data.repository.StaleListRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class OrderViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()

    private val _ongoingOrders = MutableStateFlow<List<Order>>(emptyList())
    val ongoingOrders: StateFlow<List<Order>> = _ongoingOrders.asStateFlow()

    private val _historyOrders = MutableStateFlow<List<Order>>(emptyList())
    val historyOrders: StateFlow<List<Order>> = _historyOrders.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    private val _refreshFailed = MutableStateFlow(false)
    val refreshFailed: StateFlow<Boolean> = _refreshFailed.asStateFlow()
    private val refreshMutex = Mutex()


    private val _currentTrackingOrder = MutableStateFlow<Order?>(null)
    val currentTrackingOrder: StateFlow<Order?> = _currentTrackingOrder.asStateFlow()

    suspend fun observeUserOrders() {
        if (auth.currentUser == null) return
        _isLoading.value = _ongoingOrders.value.isEmpty() && _historyOrders.value.isEmpty()
        var previousFingerprint: Int? = null
        while (true) {
            try {
                refreshOrderSnapshot()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { Log.e("OrderViewModel", "Lỗi tải đơn MySQL: ${e.message}", e) }
            _isLoading.value = false
            val fingerprint = 31 * _ongoingOrders.value.hashCode() + _historyOrders.value.hashCode()
            val changed = previousFingerprint != null && previousFingerprint != fingerprint
            previousFingerprint = fingerprint
            delay(if (changed) 5_000 else 10_000)
        }
    }

    fun refreshOrders() {
        viewModelScope.launch { refreshOrderSnapshot() }
    }

    private suspend fun refreshOrderSnapshot() = refreshMutex.withLock {
        _isRefreshing.value = true
        try {
            StaleListRepository.observe("orders", Order::class.java).collect { cached ->
                _isLoading.value = cached.refreshing && cached.updatedAt == null && cached.items.isEmpty()
                _refreshFailed.value = cached.refreshError
                if (cached.items.isNotEmpty() || cached.updatedAt != null) {
                    _ongoingOrders.value = cached.items.filter { it.status in setOf("PENDING_PAYMENT","PENDING","PROCESSING","ACCEPTED","SHIPPING") }.sortedByDescending { it.time }
                    _historyOrders.value = cached.items.filter { it.status in setOf("COMPLETED","CANCELLED") }.sortedByDescending { it.time }
                }
            }
        } finally { _isRefreshing.value = false }
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

    suspend fun observeOrderDetails(orderId: String) {
        if (orderId.isBlank()) return
        var previousFingerprint: String? = null
        while (true) {
            val refreshed = runCatching { FoodHubClient.get<Order>("orders", orderId) }.getOrNull()
            if (refreshed != null) {
                _currentTrackingOrder.value = refreshed
                val fingerprint = "${refreshed.status}:${refreshed.isPaid}:${refreshed.time}:${refreshed.shipperId}"
                val unchanged = fingerprint == previousFingerprint
                previousFingerprint = fingerprint
                delay(if (unchanged) 8_000 else 3_000)
            } else {
                Log.e("OrderViewModel", "Lỗi tải đơn $orderId")
                delay(if (_currentTrackingOrder.value == null) 3_000 else 8_000)
            }
        }
    }

}
