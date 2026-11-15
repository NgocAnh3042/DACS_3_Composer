package com.example.dacs_3_composer.feature.customer.profileRestaurant

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dacs_3_composer.core.model.DishItem
import com.example.dacs_3_composer.core.model.RestaurantDetail
import com.example.dacs_3_composer.data.repository.MenuRepository
import com.example.dacs_3_composer.data.remote.FoodHubClient
import kotlinx.coroutines.launch

class UserStoreViewModel : ViewModel() {
    private val repository = MenuRepository()
    private val TAG = "UserStoreViewModel"

    // 1. Tạo 2 cái túi thần kỳ (State) ban đầu trống rỗng để chứa dữ liệu thật
    var restaurantDetail = mutableStateOf<RestaurantDetail?>(null)
    var dishesList = mutableStateOf<List<DishItem>>(emptyList())
    var isLoading = mutableStateOf(false)

    fun fetchStoreDataForUser(restaurantId: String) {
        if (restaurantId.isEmpty()) return

        isLoading.value = true

        viewModelScope.launch {
            try {
                restaurantDetail.value = FoodHubClient.get("stores", restaurantId)
                isLoading.value = false
                repository.getDishes(restaurantId).collect { dishList ->
                    // 🌟 Lọc: Chỉ lấy những món có available == true
                    val availableDishes = dishList.filter { dish -> dish.available == true }

                    // Đổ dữ liệu đã lọc sạch vào túi chứa dishesList để UI lấy ra vẽ
                    dishesList.value = availableDishes.map { dish ->
                        DishItem(
                            id = dish.id,
                            name = dish.name,
                            price = dish.price,
                            description = dish.description,
                            imageUrl = dish.imageUrl,
                            category = dish.category
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi lấy món phía user: ${e.message}")
            } finally { isLoading.value = false }
        }
    }
}
