package com.example.dacs_3_composer.feature.restaurant.menu

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dacs_3_composer.core.model.Dish
import com.example.dacs_3_composer.data.repository.MenuRepository
import com.example.dacs_3_composer.data.repository.RestaurantStoreRepository
import com.example.dacs_3_composer.data.repository.ActivityLogRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class RestaurantViewModel : ViewModel() {
    private val repository = MenuRepository()
    private val activityLogRepository = ActivityLogRepository()
    private val auth = FirebaseAuth.getInstance()
    private val TAG = "RestaurantViewModel"

    private val _dishes = MutableStateFlow<List<Dish>>(emptyList())
    val dishes: StateFlow<List<Dish>> = _dishes.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val localDishes = mutableMapOf<String, Dish>()

    private val restaurantId: String
        get() = auth.currentUser?.uid ?: ""

    init {
        fetchDishes()
    }

    fun fetchDishes() {
        val uid = restaurantId
        if (uid.isEmpty()) return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val store = RestaurantStoreRepository.ensureStore()
                repository.getDishes(store.id, includeUnavailable = true).collect { dishList ->
                    val idsOnServer = dishList.mapTo(mutableSetOf()) { it.id }
                    localDishes.keys.removeAll(idsOnServer)
                    _dishes.value = (localDishes.values + dishList).distinctBy { it.id }
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _error.value = "Lỗi khi tải thực đơn: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    fun addDish(name: String, price: Double, category: String, description: String, imageUri: Uri?, uploadPreset: String = "ml_default") {
        val uid = restaurantId
        if (uid.isEmpty()) return
        val pendingId = "pending-${UUID.randomUUID()}"
        val pendingDish = Dish(id = pendingId, name = name, price = price, category = category,
            description = description, imageUrl = imageUri?.toString().orEmpty(), available = true)
        localDishes[pendingId] = pendingDish
        _dishes.value = listOf(pendingDish) + _dishes.value
        viewModelScope.launch {
            _isLoading.value = true
            try {
                var imageUrl = ""
                if (imageUri != null) {
                    imageUrl = repository.uploadImage(imageUri, uploadPreset)
                }
                val store = RestaurantStoreRepository.ensureStore()
                val newDish = Dish(
                    name = name,
                    price = price,
                    category = category,
                    description = description,
                    imageUrl = imageUrl,
                    restaurantId = store.id,
                    available = true
                )
                val savedId = repository.addDish(newDish)
                localDishes.remove(pendingId)
                val savedDish = newDish.copy(id = savedId)
                localDishes[savedId] = savedDish
                _dishes.value = listOf(savedDish) + _dishes.value.filterNot { it.id == pendingId || it.id == savedId }
                
                // 🎯 LOG ACTIVITY
                activityLogRepository.logActivity(
                    restaurantId = uid,
                    type = "menu",
                    title = "Thêm món mới",
                    description = "Đã thêm món \"$name\" vào danh mục $category",
                    imageUrl = imageUrl
                )
                
                _isLoading.value = false
            } catch (e: Exception) {
                localDishes.remove(pendingId)
                _dishes.value = _dishes.value.filterNot { it.id == pendingId }
                _error.value = "Lỗi khi thêm món: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    fun updateDish(dish: Dish, newImageUri: Uri?, uploadPreset: String = "ml_default") {
        val uid = restaurantId
        viewModelScope.launch {
            _isLoading.value = true
            try {
                var imageUrl = dish.imageUrl
                if (newImageUri != null) {
                    imageUrl = repository.uploadImage(newImageUri, uploadPreset)
                }
                val store = RestaurantStoreRepository.ensureStore()
                val updatedDish = dish.copy(imageUrl = imageUrl, restaurantId = store.id)
                repository.updateDish(updatedDish)

                // 🎯 LOG ACTIVITY
                activityLogRepository.logActivity(
                    restaurantId = uid,
                    type = "menu",
                    title = "Cập nhật thực đơn",
                    description = "Đã thay đổi thông tin món \"${dish.name}\"",
                    imageUrl = imageUrl
                )

                _isLoading.value = false
            } catch (e: Exception) {
                _error.value = "Lỗi khi cập nhật: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    fun deleteDish(dishId: String) {
        val uid = restaurantId
        val dishName = _dishes.value.find { it.id == dishId }?.name ?: "Món ăn"
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.deleteDish(dishId)
                
                // 🎯 LOG ACTIVITY
                activityLogRepository.logActivity(
                    restaurantId = uid,
                    type = "menu",
                    title = "Xóa món ăn",
                    description = "Đã xóa món \"$dishName\" khỏi thực đơn"
                )

                _isLoading.value = false
            } catch (e: Exception) {
                _error.value = "Lỗi khi xóa món: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    fun toggleDishAvailability(dish: Dish) {
        val uid = restaurantId
        viewModelScope.launch {
            try {
                val newState = !dish.available
                val store = RestaurantStoreRepository.ensureStore()
                val updatedDish = dish.copy(available = newState, restaurantId = store.id)
                repository.updateDish(updatedDish)

                // 🎯 LOG ACTIVITY
                activityLogRepository.logActivity(
                    restaurantId = uid,
                    type = "menu",
                    title = if (newState) "Bật món ăn" else "Tắt món ăn",
                    description = "Món \"${dish.name}\" hiện đang ${if (newState) "còn hàng" else "hết hàng"}"
                )
            } catch (e: Exception) {
                _error.value = "Không thể cập nhật trạng thái: ${e.message}"
            }
        }
    }
    fun clearError() {
        _error.value = null
    }
}
