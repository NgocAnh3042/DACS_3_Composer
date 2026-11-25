package com.example.dacs_3_composer.feature.customer.cart.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dacs_3_composer.core.model.DishItem
import com.example.dacs_3_composer.core.model.Order
import com.example.dacs_3_composer.core.model.OrderItem
import com.example.dacs_3_composer.core.model.OrderStatus
import com.example.dacs_3_composer.core.model.Promotion
import com.example.dacs_3_composer.core.model.UserAddress
import com.example.dacs_3_composer.data.remote.FoodHubClient
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CartItem(
    val dish: DishItem,
    val quantity: Int
)

data class CartUiState(
    val cartItems: List<CartItem> = emptyList(),
    val currentRestaurantId: String = "",
    val currentRestaurantName: String = "",
    val availablePromotions: List<Promotion> = emptyList()
)

class CartViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(CartUiState())
    val uiState = _uiState.asStateFlow()

    fun setCurrentRestaurant(id: String, name: String) {
        _uiState.update { it.copy(currentRestaurantId = id, currentRestaurantName = name) }
    }

    /**
     * Tải voucher đang hiệu lực từ MySQL API.
     */
    fun fetchPromotionsFromFirebase() {
        viewModelScope.launch {
            runCatching { FoodHubClient.list<Promotion>("promotions") }
                .onSuccess { promotions -> _uiState.update { it.copy(availablePromotions = promotions) } }
                .onFailure { Log.e("CartViewModel", "Lỗi tải voucher MySQL: ${it.message}") }
        }
    }

    /**
     * Xử lý đặt đơn hàng và tự động trừ lượt sử dụng mã khuyến mãi trên hệ thống
     */
    fun placeOrder(
        customerName: String,
        customerPhone: String,
        customerAddress: String,
        customerLat: Double,
        customerLng: Double,
        restaurantId: String,
        restaurantName: String,
        shippingFee: Double,
        totalDishPrice: Double,
        finalTotal: Double,
        appliedPromotionId: String?,
        appliedPromotionTitle: String,
        promotionDiscount: Double,
        paymentMethod: String, // ✅ Nhận thêm phương thức thanh toán
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            onFailure("Người dùng chưa đăng nhập!")
            return
        }

        val cartItems = _uiState.value.cartItems
        if (cartItems.isEmpty()) {
            onFailure("Giỏ hàng đang trống!")
            return
        }

        val finalRestaurantId = if (restaurantId.isNotBlank() && restaurantId != "RES_DEFAULT") {
            restaurantId
        } else if (_uiState.value.currentRestaurantId.isNotBlank()) {
            _uiState.value.currentRestaurantId
        } else {
            cartItems.firstOrNull()?.dish?.restaurantId ?: ""
        }

        val orderItemsList = cartItems.map { item ->
            OrderItem(
                dishId = item.dish.id,
                name = item.dish.name,
                quantity = item.quantity,
                price = item.dish.price
            )
        }

        // Xác định trạng thái ban đầu dựa trên phương thức thanh toán
        val initialStatus = if (paymentMethod == "ONLINE") {
            OrderStatus.PENDING_PAYMENT.name
        } else {
            OrderStatus.PENDING.name // ✅ CHỈ DÙNG PENDING, KHÔNG DÙNG WAITING_RESTAURANT
        }

        val newOrder = Order(
            id = "",
            time = "",
            status = initialStatus,
            paymentMethod = paymentMethod, // ✅ Lưu phương thức thanh toán
            totalDishPrice = totalDishPrice,
            totalPrice = finalTotal,
            shippingFee = shippingFee,

            // LƯU ĐẦY ĐỦ BỘ BA THÔNG TIN KHUYẾN MÃI VÀO ĐƠN HÀNG
            appliedPromotionId = appliedPromotionId,
            appliedPromotionTitle = appliedPromotionTitle,
            promotionDiscount = promotionDiscount,

            userId = uid,
            customerName = customerName,
            customerPhone = customerPhone,
            customerAddress = customerAddress,
            customerLat = customerLat,
            customerLng = customerLng,
            restaurantId = finalRestaurantId,
            restaurantName = restaurantName.ifBlank { _uiState.value.currentRestaurantName }.ifBlank { "Nhà hàng đối tác" },
            restaurantLat = null,
            restaurantLng = null,
            shipperId = "",
            items = orderItemsList
        )

        viewModelScope.launch {
            runCatching { FoodHubClient.create("orders", newOrder) }
                .onSuccess { orderId -> clearCart(); onSuccess(orderId) }
                .onFailure { onFailure(it.message ?: "Không thể tạo đơn trên MySQL") }
        }
    }

    fun addToCart(dish: DishItem, quantity: Int = 1) {
        val safeQty = quantity.coerceAtLeast(1)
        _uiState.update { state ->
            val existingItem = state.cartItems.find { it.dish.id == dish.id }
            val items = if (existingItem != null) {
                state.cartItems.map { item ->
                    if (item.dish.id == dish.id) item.copy(quantity = item.quantity + safeQty) else item
                }
            } else state.cartItems + CartItem(dish = dish, quantity = safeQty)
            state.copy(
                cartItems = items,
                currentRestaurantId = dish.restaurantId.ifBlank { state.currentRestaurantId },
                currentRestaurantName = dish.restaurantName.ifBlank { state.currentRestaurantName }
            )
        }
    }

    fun updateDeliveryInfo(
        newName: String,
        newPhone: String,
        newAddress: String,
        newAddressDetail: String,
        lat: Double,
        lng: Double
    ) {
        if (FirebaseAuth.getInstance().currentUser == null) return
        viewModelScope.launch {
            runCatching {
                FoodHubClient.update("users", "me", mapOf("fullName" to newName, "phoneNumber" to newPhone))
                FoodHubClient.create("addresses", UserAddress(name=newName,phone=newPhone,address=newAddress,addressDetail=newAddressDetail,latitude=lat,longitude=lng))
            }.onFailure { Log.e("CartViewModel", "Lỗi lưu địa chỉ MySQL: ${it.message}") }
        }
    }

    fun minusFromCart(dish: DishItem) {
        _uiState.update { state ->
            val existingItem = state.cartItems.find { it.dish.id == dish.id } ?: return@update state
            val items = if (existingItem.quantity > 1) {
                state.cartItems.map { item ->
                    if (item.dish.id == dish.id) item.copy(quantity = item.quantity - 1) else item
                }
            } else state.cartItems.filterNot { it.dish.id == dish.id }
            state.copy(
                cartItems = items,
                currentRestaurantId = if (items.isEmpty()) "" else state.currentRestaurantId,
                currentRestaurantName = if (items.isEmpty()) "" else state.currentRestaurantName
            )
        }
    }

    fun getTotalPrice(): Double = _uiState.value.cartItems.sumOf { it.dish.price * it.quantity }

    fun clearCart() {
        _uiState.update { it.copy(cartItems = emptyList(), currentRestaurantId = "", currentRestaurantName = "") }
    }
}
