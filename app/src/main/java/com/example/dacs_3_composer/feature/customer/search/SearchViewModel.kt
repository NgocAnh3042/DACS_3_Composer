package com.example.dacs_3_composer.feature.customer.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dacs_3_composer.core.model.RestaurantDetail
import com.example.dacs_3_composer.core.model.Dish
import com.example.dacs_3_composer.data.remote.FoodHubClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

// Class bọc món ăn kết hợp tên nhà hàng để hiển thị lên UI
data class SearchDishWrapper(
    val dish: Dish,
    val restaurantName: String
)

class SearchViewModel : ViewModel() {

    private val _restaurantResults = MutableStateFlow<List<RestaurantDetail>>(emptyList())
    val restaurantResults: StateFlow<List<RestaurantDetail>> = _restaurantResults.asStateFlow()

    private val _dishResults = MutableStateFlow<List<SearchDishWrapper>>(emptyList())
    val dishResults: StateFlow<List<SearchDishWrapper>> = _dishResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun searchRestaurantsAndDishes(query: String) {
        if (query.isBlank()) {
            _restaurantResults.value = emptyList()
            _dishResults.value = emptyList()
            return
        }

        _isLoading.value = true
        val lowercaseQuery = query.lowercase(Locale.getDefault()).trim()

        viewModelScope.launch {
            try {
                val allRestaurants = FoodHubClient.list<RestaurantDetail>("stores")
                val allDishes = FoodHubClient.list<Dish>("dishes")
                _restaurantResults.value = allRestaurants.filter { restaurant ->
                    restaurant.name.contains(lowercaseQuery, ignoreCase = true) ||
                        restaurant.description.contains(lowercaseQuery, ignoreCase = true)
                }
                _dishResults.value = allDishes.filter { dish ->
                    dish.name.contains(lowercaseQuery, ignoreCase = true) ||
                        dish.description.contains(lowercaseQuery, ignoreCase = true)
                }.map { dish ->
                    SearchDishWrapper(dish, allRestaurants.firstOrNull { it.id == dish.restaurantId }?.name ?: "Cửa hàng đối tác")
                }
            } catch (e: Exception) {
                Log.e("SearchViewModel", "Lỗi truy vấn MySQL: ${e.message}", e)
                _restaurantResults.value = emptyList()
                _dishResults.value = emptyList()
            } finally { _isLoading.value = false }
        }
    }
}
