package com.example.dacs_3_composer.feature.customer.payment

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dacs_3_composer.core.model.Order
import com.example.dacs_3_composer.data.remote.FoodHubClient
import com.example.dacs_3_composer.data.repository.PaymentRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class PaymentViewModel(private val repository: PaymentRepository) : ViewModel() {
    var checkoutUrl by mutableStateOf<String?>(null)
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var isPaymentSuccess by mutableStateOf(false)

    private var statusJob: kotlinx.coroutines.Job? = null

    fun createPayment(orderId: String, amount: Double, description: String) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val response = repository.createPaymentLink(orderId, amount, description)
                checkoutUrl = response.checkoutUrl
                // Bắt đầu lắng nghe trạng thái đơn hàng ngay khi tạo link thành công
                observeOrderStatus(orderId)
            } catch (e: Exception) {
                errorMessage = "Lỗi kết nối thanh toán: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    private fun observeOrderStatus(orderId: String) {
        statusJob?.cancel()
        statusJob = viewModelScope.launch {
            while (true) {
                runCatching { FoodHubClient.get<Order>("orders", orderId) }.getOrNull()?.let { order ->
                    isPaymentSuccess = order.isPaid || order.status == "PENDING"
                }
                if (isPaymentSuccess) break
                kotlinx.coroutines.delay(3000)
            }
        }
    }
    
    fun resetState() {
        statusJob?.cancel()
        checkoutUrl = null
        errorMessage = null
        isLoading = false
        isPaymentSuccess = false
    }
}
