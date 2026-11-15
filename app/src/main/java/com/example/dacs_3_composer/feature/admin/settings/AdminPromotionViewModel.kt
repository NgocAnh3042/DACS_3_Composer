package com.example.dacs_3_composer.feature.admin.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dacs_3_composer.core.model.Promotion
import com.example.dacs_3_composer.data.remote.FoodHubClient
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class AdminPromotionViewModel : ViewModel() {
    private val TAG = "AdminPromotionVM"

    private val _promotions = MutableStateFlow<List<Promotion>>(emptyList())
    val promotions: StateFlow<List<Promotion>> = _promotions.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    init {
        fetchPromotions()
    }

    private fun fetchPromotions() {
        _isLoading.value = true
        viewModelScope.launch {
            while (true) {
                runCatching { FoodHubClient.list<Promotion>("promotions") }
                    .onSuccess { _promotions.value = it.sortedByDescending { p -> p.startDate } }
                    .onFailure { Log.e(TAG, "Lỗi tải voucher MySQL: ${it.message}") }
                _isLoading.value = false
                delay(10_000)
            }
        }
    }

    fun addPromotion(promotion: Promotion) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                FoodHubClient.create("promotions", promotion)
                _toastMessage.emit("Thêm mã khuyến mãi thành công!")
                Log.d(TAG, "Promotion added successfully")
            } catch (e: Exception) {
                _toastMessage.emit("Lỗi: ${e.message}")
                Log.e(TAG, "Error adding promotion: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deletePromotion(id: String) {
        if (id.isBlank()) return
        viewModelScope.launch {
            try {
                FoodHubClient.delete("promotions", id)
                _toastMessage.emit("Đã xóa mã khuyến mãi")
            } catch (e: Exception) {
                _toastMessage.emit("Lỗi khi xóa: ${e.message}")
                Log.e(TAG, "Error deleting promotion", e)
            }
        }
    }

    fun toggleStatus(id: String, currentStatus: String) {
        if (id.isBlank()) return
        val newStatus = if (currentStatus == "active") "expired" else "active"
        viewModelScope.launch {
            try {
                FoodHubClient.update("promotions", id, mapOf("status" to newStatus))
                _toastMessage.emit("Đã cập nhật trạng thái")
            } catch (e: Exception) {
                _toastMessage.emit("Lỗi cập nhật: ${e.message}")
                Log.e(TAG, "Error toggling status", e)
            }
        }
    }
}
