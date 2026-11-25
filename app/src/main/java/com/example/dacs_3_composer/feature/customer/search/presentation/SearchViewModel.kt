package com.example.dacs_3_composer.feature.customer.search.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dacs_3_composer.core.model.RestaurantDetail
import com.example.dacs_3_composer.core.model.Dish
import com.example.dacs_3_composer.data.repository.StaleListRepository
import com.example.dacs_3_composer.domain.usecase.RecordSearchQueryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import java.util.Locale

// Class bọc món ăn kết hợp tên nhà hàng để hiển thị lên UI
data class SearchDishWrapper(
    val dish: Dish,
    val restaurantName: String
)

data class SearchUiState(
    val restaurantResults: List<RestaurantDetail> = emptyList(),
    val dishResults: List<SearchDishWrapper> = emptyList(),
    val isLoading: Boolean = false,
    val refreshFailed: Boolean = false
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val recordSearchQuery: RecordSearchQueryUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var lastRecordedQuery: String? = null
    private var searchJob: Job? = null

    fun searchRestaurantsAndDishes(query: String) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) {
            searchJob?.cancel()
            _uiState.value = SearchUiState()
            return
        }

        searchJob?.cancel()
        _uiState.value = _uiState.value.copy(isLoading = true)
        val lowercaseQuery = trimmedQuery.lowercase(Locale.getDefault())

        // Ghi nhận lịch sử tìm kiếm đã xác nhận bất đồng bộ (không chặn kết quả, không lưu trùng do recomposition)
        if (lastRecordedQuery != lowercaseQuery && trimmedQuery.length >= 2) {
            lastRecordedQuery = lowercaseQuery
            viewModelScope.launch {
                recordSearchQuery(trimmedQuery)
            }
        }

        searchJob = viewModelScope.launch {
            try {
                delay(250)
                combine(
                    StaleListRepository.observe("stores", RestaurantDetail::class.java),
                    StaleListRepository.observe("dishes", Dish::class.java)
                ) { stores, dishes -> stores to dishes }.collect { (storeSnapshot, dishSnapshot) ->
                    val (restaurantResults, dishResults) = withContext(Dispatchers.Default) {
                        val restaurants = storeSnapshot.items.filter { restaurant ->
                            restaurant.name.contains(lowercaseQuery, ignoreCase = true) || restaurant.description.contains(lowercaseQuery, ignoreCase = true)
                        }
                        val restaurantNames = storeSnapshot.items.associate { it.id to it.name }
                        val dishMatches = dishSnapshot.items.filter { dish ->
                            dish.name.contains(lowercaseQuery, ignoreCase = true) || dish.description.contains(lowercaseQuery, ignoreCase = true)
                        }.map { dish -> SearchDishWrapper(dish, restaurantNames[dish.restaurantId] ?: "Cửa hàng đối tác") }
                        restaurants to dishMatches
                    }
                    _uiState.value = SearchUiState(
                        restaurantResults = restaurantResults,
                        dishResults = dishResults,
                        isLoading = (storeSnapshot.refreshing || dishSnapshot.refreshing) && storeSnapshot.updatedAt == null && dishSnapshot.updatedAt == null,
                        refreshFailed = storeSnapshot.refreshError || dishSnapshot.refreshError
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("SearchViewModel", "Lỗi truy vấn MySQL: ${e.message}", e)
                _uiState.value = _uiState.value.copy(isLoading = false, refreshFailed = true)
            }
        }
    }
}
