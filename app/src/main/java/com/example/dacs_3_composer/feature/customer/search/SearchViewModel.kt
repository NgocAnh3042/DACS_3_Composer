package com.example.dacs_3_composer.feature.customer.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dacs_3_composer.core.model.RestaurantDetail
import com.example.dacs_3_composer.core.model.Dish
import com.example.dacs_3_composer.data.remote.FoodHubClient
import com.example.dacs_3_composer.data.repository.RecommendationRepository
import com.example.dacs_3_composer.domain.usecase.RecordSearchQueryUseCase
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

class SearchViewModel(
    private val recordSearchQuery: RecordSearchQueryUseCase =
        RecordSearchQueryUseCase(RecommendationRepository())
) : ViewModel() {

    private val _restaurantResults = MutableStateFlow<List<RestaurantDetail>>(emptyList())
    val restaurantResults: StateFlow<List<RestaurantDetail>> = _restaurantResults.asStateFlow()

    private val _dishResults = MutableStateFlow<List<SearchDishWrapper>>(emptyList())
    val dishResults: StateFlow<List<SearchDishWrapper>> = _dishResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var lastRecordedQuery: String? = null

    fun searchRestaurantsAndDishes(query: String) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) {
            _restaurantResults.value = emptyList()
            _dishResults.value = emptyList()
            return
        }

        _isLoading.value = true
        val lowercaseQuery = trimmedQuery.lowercase(Locale.getDefault())

        // Ghi nhận lịch sử tìm kiếm đã xác nhận bất đồng bộ (không chặn kết quả, không lưu trùng do recomposition)
        if (lastRecordedQuery != lowercaseQuery && trimmedQuery.length >= 2) {
            lastRecordedQuery = lowercaseQuery
            viewModelScope.launch {
                recordSearchQuery(trimmedQuery)
            }
        }

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
