package com.example.dacs_3_composer.feature.customer.profileRestaurant

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dacs_3_composer.core.model.DishItem
import com.example.dacs_3_composer.core.model.RestaurantDetail
import com.example.dacs_3_composer.data.repository.MenuRepository
import com.example.dacs_3_composer.data.repository.StaleListRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

class UserStoreViewModel : ViewModel() {
    private val repository = MenuRepository()
    private val TAG = "UserStoreViewModel"

    // 1. Tạo 2 cái túi thần kỳ (State) ban đầu trống rỗng để chứa dữ liệu thật
    var restaurantDetail = mutableStateOf<RestaurantDetail?>(null)
    var dishesList = mutableStateOf<List<DishItem>>(emptyList())
    var isLoading = mutableStateOf(false)
    private var storeRefreshFailed = mutableStateOf(false)
    private var menuRefreshFailed = mutableStateOf(false)
    val refreshFailed: Boolean get() = storeRefreshFailed.value || menuRefreshFailed.value
    private var storeDataJob: Job? = null
    private var currentRestaurantId: String? = null

    fun fetchStoreDataForUser(restaurantId: String) {
        if (restaurantId.isEmpty()) return

        if (currentRestaurantId != restaurantId) {
            currentRestaurantId = restaurantId
            restaurantDetail.value = null
            dishesList.value = emptyList()
        }

        isLoading.value = true

        storeDataJob?.cancel()
        storeDataJob = viewModelScope.launch {
            try {
                coroutineScope {
                    launch {
                        StaleListRepository.observeObject("stores", restaurantId, RestaurantDetail::class.java).collect { snapshot ->
                            storeRefreshFailed.value = snapshot.refreshError
                            if (snapshot.value != null) restaurantDetail.value = snapshot.value
                            isLoading.value = snapshot.refreshing && restaurantDetail.value == null && dishesList.value.isEmpty()
                        }
                    }
                    launch {
                        repository.observeDishes(restaurantId).collect { snapshot ->
                            menuRefreshFailed.value = snapshot.refreshError
                            dishesList.value = snapshot.items.filter { it.available }.map { dish ->
                                DishItem(id = dish.id, name = dish.name, price = dish.price, description = dish.description, imageUrl = dish.imageUrl, category = dish.category)
                            }
                            isLoading.value = snapshot.refreshing && snapshot.updatedAt == null && snapshot.items.isEmpty() && restaurantDetail.value == null
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi lấy món phía user: ${e.message}")
            } finally { isLoading.value = false }
        }
    }

    fun stopStoreDataObservation() { storeDataJob?.cancel(); storeDataJob = null }
}
