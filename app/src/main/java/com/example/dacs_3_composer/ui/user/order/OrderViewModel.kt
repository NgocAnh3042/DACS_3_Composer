package com.example.dacs_3_composer.ui.user.order

import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import com.example.dacs_3_composer.data.model.Order
import com.example.dacs_3_composer.data.model.OrderStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class OrderViewModel : ViewModel() {
    private val firestore = FirebaseFirestore.getInstance()
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

        firestore.collection("orders")
            .whereEqualTo("userId", uid)
            .addSnapshotListener { snapshot, error ->
                _isLoading.value = false
                if (error != null) {
                    Log.e("OrderViewModel", "Lỗi lắng nghe đơn hàng: ", error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val allOrders = snapshot.toObjects(Order::class.java)

                    val ongoing = allOrders.filter {
                        it.status == OrderStatus.PENDING_PAYMENT.name ||
                        it.status == OrderStatus.PENDING.name ||
                        it.status == "PENDING" ||
                        it.status == "PROCESSING" ||
                        it.status == "ACCEPTED" ||
                        it.status == "SHIPPING"
                    }.sortedByDescending { it.time }

                    val history = allOrders.filter {
                        it.status == "COMPLETED" || it.status == "CANCELLED"
                    }.sortedByDescending { it.time }

                    allOrders.forEach { order ->
                        if (order.restaurantId.isNotBlank() && !restaurantImages.containsKey(order.restaurantId)) {
                            fetchRestaurantImage(order.restaurantId)
                        }
                    }

                    _ongoingOrders.value = ongoing
                    _historyOrders.value = history
                }
            }
    }

    // ✅ CẬP NHẬT: Đánh dấu đã thanh toán và chuyển trạng thái cho nhà hàng
    fun updateOrderToPaid(orderId: String) {
        if (orderId.isBlank()) return
        firestore.collection("orders").document(orderId)
            .update(
                "status", OrderStatus.PENDING.name,
                "isPaid", true // Shipper sẽ nhìn thấy đơn đã trả tiền
            )
            .addOnSuccessListener {
                Log.d("OrderViewModel", "✅ Đơn hàng $orderId đã cập nhật PAID & WAITING_RESTAURANT")
            }
            .addOnFailureListener { e ->
                Log.e("OrderViewModel", "❌ Lỗi cập nhật đơn hàng $orderId: ${e.message}")
            }
    }

    fun cancelOrder(orderId: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        if (orderId.isBlank()) return

        firestore.collection("orders").document(orderId)
            .update("status", "CANCELLED")
            .addOnSuccessListener {
                Log.d("OrderViewModel", "Hủy đơn hàng thành công ID: $orderId")
                onSuccess()
            }
            .addOnFailureListener { e ->
                Log.e("OrderViewModel", "Hủy đơn hàng thất bại: ${e.message}")
                onFailure(e.message ?: "Lỗi hệ thống khi hủy đơn")
            }
    }

    fun observeOrderDetails(orderId: String) {
        if (orderId.isBlank()) return

        firestore.collection("orders").document(orderId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("OrderViewModel", "Lỗi lắng nghe chi tiết đơn hàng $orderId: ", error)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val order = snapshot.toObject(Order::class.java)
                    _currentTrackingOrder.value = order
                }
            }
    }

    private fun fetchRestaurantImage(restaurantId: String) {
        firestore.collection("restaurants").document(restaurantId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val imageUrl = document.getString("coverImage") ?: ""
                    if (imageUrl.isNotBlank()) {
                        restaurantImages[restaurantId] = imageUrl
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("OrderViewModel", "Lỗi lấy ảnh quán $restaurantId", e)
            }
    }
}
