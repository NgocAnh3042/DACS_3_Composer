package com.example.dacs_3_composer.feature.customer.search.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dacs_3_composer.core.model.RestaurantDetail
import com.example.dacs_3_composer.core.model.Dish
import com.example.dacs_3_composer.data.remote.FoodHubClient
import com.example.dacs_3_composer.domain.usecase.RecordSearchQueryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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

data class SearchUiState(
    val restaurantResults: List<RestaurantDetail> = emptyList(),
    val dishResults: List<SearchDishWrapper> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val recordSearchQuery: RecordSearchQueryUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var lastRecordedQuery: String? = null

    fun searchRestaurantsAndDishes(query: String) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) {
            _uiState.value = SearchUiState()
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true)
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
                val restaurantResults = allRestaurants.filter { restaurant ->
                    restaurant.name.contains(lowercaseQuery, ignoreCase = true) ||
                        restaurant.description.contains(lowercaseQuery, ignoreCase = true)
                }
                val dishResults = allDishes.filter { dish ->
                    dish.name.contains(lowercaseQuery, ignoreCase = true) ||
                        dish.description.contains(lowercaseQuery, ignoreCase = true)
                }.map { dish ->
                    SearchDishWrapper(dish, allRestaurants.firstOrNull { it.id == dish.restaurantId }?.name ?: "Cửa hàng đối tác")
                }
                _uiState.value = SearchUiState(
                    restaurantResults = restaurantResults,
                    dishResults = dishResults,
                    isLoading = false
                )
            } catch (e: Exception) {
                Log.e("SearchViewModel", "Lỗi truy vấn MySQL: ${e.message}", e)
                _uiState.value = SearchUiState()
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
}
