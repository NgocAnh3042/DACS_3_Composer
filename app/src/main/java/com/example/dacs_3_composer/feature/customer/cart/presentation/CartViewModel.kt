package com.example.dacs_3_composer.feature.customer.cart.presentation

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
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
import kotlinx.coroutines.launch

data class CartItem(
    val dish: DishItem,
    val quantity: Int
)

class CartViewModel : ViewModel() {

    var cartItems = mutableStateListOf<CartItem>()
        private set

    var currentRestaurantId: String = ""
    var currentRestaurantName: String = ""

    // Danh sách khuyến mãi được tải từ MySQL API.
    var availablePromotions = mutableStateListOf<Promotion>()
        private set

    /**
     * Tải voucher đang hiệu lực từ MySQL API.
     */
    fun fetchPromotionsFromFirebase() {
        viewModelScope.launch {
            runCatching { FoodHubClient.list<Promotion>("promotions") }
                .onSuccess { availablePromotions.clear(); availablePromotions.addAll(it) }
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

        if (cartItems.isEmpty()) {
            onFailure("Giỏ hàng đang trống!")
            return
        }

        val finalRestaurantId = if (restaurantId.isNotBlank() && restaurantId != "RES_DEFAULT") {
            restaurantId
        } else if (this.currentRestaurantId.isNotBlank()) {
            this.currentRestaurantId
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
            restaurantName = restaurantName.ifBlank { this.currentRestaurantName }.ifBlank { "Nhà hàng đối tác" },
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
        if (dish.restaurantId.isNotBlank()) {
            this.currentRestaurantId = dish.restaurantId
        }
        if (dish.restaurantName.isNotBlank()) {
            this.currentRestaurantName = dish.restaurantName
        }
        val existingItem = cartItems.find { it.dish.id == dish.id }
        if (existingItem != null) {
            val index = cartItems.indexOf(existingItem)
            cartItems[index] = existingItem.copy(quantity = existingItem.quantity + safeQty)
        } else {
            cartItems.add(CartItem(dish = dish, quantity = safeQty))
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
        val existingItem = cartItems.find { it.dish.id == dish.id } ?: return
        val index = cartItems.indexOf(existingItem)

        if (existingItem.quantity > 1) {
            cartItems[index] = existingItem.copy(quantity = existingItem.quantity - 1)
        } else {
            cartItems.removeAt(index)
            if (cartItems.isEmpty()) {
                currentRestaurantId = ""
                currentRestaurantName = ""
            }
        }
    }

    fun getTotalPrice(): Double = cartItems.sumOf { it.dish.price * it.quantity }

    fun clearCart() {
        cartItems.clear()
        currentRestaurantId = ""
        currentRestaurantName = ""
    }
}
