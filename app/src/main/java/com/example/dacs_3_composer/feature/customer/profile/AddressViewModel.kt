package com.example.dacs_3_composer.feature.customer.profile

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dacs_3_composer.core.model.UserAddress
import com.example.dacs_3_composer.data.remote.FoodHubClient
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AddressViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()

    private val _addressList = MutableStateFlow<List<UserAddress>>(emptyList())
    val addressList: StateFlow<List<UserAddress>> = _addressList

    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun clearError() { error = null }

    private val userId: String
        get() = auth.currentUser?.uid ?: "guest_user"

    init {
        loadAddresses()
    }

    fun loadAddresses() {
        if (userId == "guest_user") return
        isLoading = true
        viewModelScope.launch {
            try { _addressList.value = FoodHubClient.list("addresses") }
            catch (e: Exception) {
                Log.e("AddressViewModel", "Lỗi tải địa chỉ MySQL: ${e.message}")
                error = "Không tải được địa chỉ: ${e.message}"
            }
            finally { isLoading = false }
        }
    }

    fun addAddress(newAddress: UserAddress, onComplete: () -> Unit) {
        viewModelScope.launch {
            runCatching { FoodHubClient.create("addresses", newAddress) }
                .onSuccess { loadAddresses(); onComplete() }
                .onFailure { Log.e("AddressViewModel", "Lỗi thêm địa chỉ MySQL", it); error = "Không lưu được địa chỉ: ${it.message}" }
        }
    }

    fun deleteAddress(addressToDelete: UserAddress) {
        if (addressToDelete.id.isBlank()) return
        viewModelScope.launch { runCatching { FoodHubClient.delete("addresses", addressToDelete.id) }.onSuccess { loadAddresses() }.onFailure { Log.e("AddressViewModel", "Lỗi xóa địa chỉ MySQL", it); error = "Không xóa được địa chỉ: ${it.message}" } }
    }

    fun updateAddress(oldAddress: UserAddress, newAddress: UserAddress, onComplete: () -> Unit) {
        if (oldAddress.id.isBlank()) return
        viewModelScope.launch {
            runCatching { FoodHubClient.update("addresses", oldAddress.id, newAddress) }
                .onSuccess { loadAddresses(); onComplete() }
                .onFailure { Log.e("AddressViewModel", "Lỗi cập nhật địa chỉ MySQL", it); error = "Không cập nhật được địa chỉ: ${it.message}" }
        }
    }
}
