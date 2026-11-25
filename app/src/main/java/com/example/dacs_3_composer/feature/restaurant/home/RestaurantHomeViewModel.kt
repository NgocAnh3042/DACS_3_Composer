package com.example.dacs_3_composer.feature.restaurant.home

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dacs_3_composer.core.model.Dish
import com.example.dacs_3_composer.core.model.Order
import com.example.dacs_3_composer.core.model.TopDish
import com.example.dacs_3_composer.core.model.User
import com.example.dacs_3_composer.data.remote.FoodHubClient
import com.example.dacs_3_composer.data.repository.RestaurantStoreRepository
import com.example.dacs_3_composer.feature.admin.analytics.AdminPeriod
import com.example.dacs_3_composer.feature.admin.analytics.adminRevenueSummary
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class RestaurantHomeViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()

    var totalRevenue by mutableStateOf("0")
        private set
    var totalOrdersCount by mutableStateOf("0")
        private set

    var restaurantName by mutableStateOf("")
        private set
    var restaurantAvatarUrl by mutableStateOf("")
        private set

    private val _orders = MutableStateFlow<List<Order>>(emptyList())
    val orders: StateFlow<List<Order>> = _orders.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    var lastUpdated by mutableStateOf("")
        private set

    private val _topDishes = MutableStateFlow<List<TopDish>>(emptyList())
    val topDishes: StateFlow<List<TopDish>> = _topDishes.asStateFlow()

    // Mảng lưu tỷ lệ chiều cao biểu đồ cho 7 ngày trong tuần hiện tại (T2 -> CN) từ 0.0f đến 1.0f
    var weeklyChartData by mutableStateOf(listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f))
        private set

    private val currentRestaurantId: String
        get() = auth.currentUser?.uid ?: ""

    init {
        fetchDashboardData()
        fetchRestaurantInfo()
    }

    private fun fetchRestaurantInfo() {
        if (currentRestaurantId.isBlank()) return
        viewModelScope.launch {
            runCatching { FoodHubClient.get<User>("users", "me") }
                .onSuccess {
                    restaurantName = it.getDisplayName()
                    restaurantAvatarUrl = it.avatarUrl
                }
                .onFailure { Log.e("RestaurantHomeVM", "Lỗi tải hồ sơ MySQL: ${it.message}") }
        }
    }

    fun refreshNow() {
        if (currentRestaurantId.isBlank()) return
        viewModelScope.launch { loadOnce(showSpinner = true) }
    }

    fun fetchDashboardData() {
        if (currentRestaurantId.isBlank()) return
        viewModelScope.launch {
            var first = true
            while (true) {
                loadOnce(showSpinner = first)
                first = false
                delay(10_000)
            }
        }
    }

    private suspend fun loadOnce(showSpinner: Boolean = false) {
        if (showSpinner) _isLoading.value = true
        runCatching {
            coroutineScope {
                val ordersDeferred = async { FoodHubClient.list<Order>("orders") }
                val dishesDeferred = async {
                    runCatching {
                        val store = RestaurantStoreRepository.ensureStore()
                        if (restaurantName.isBlank() && store.name.isNotBlank()) restaurantName = store.name
                        FoodHubClient.list<Dish>("dishes", mapOf("restaurantId" to store.id, "includeUnavailable" to "1"))
                    }.getOrElse {
                        runCatching { FoodHubClient.list<Dish>("dishes") }.getOrDefault(emptyList())
                    }
                }
                ordersDeferred.await() to dishesDeferred.await()
            }
        }.onSuccess { (fetchedOrders, storeDishes) ->
            _orders.value = fetchedOrders
            val completedOrders = fetchedOrders.filter { it.status == "COMPLETED" }
            totalRevenue = String.format(Locale("vi", "VN"), "%,.0f", completedOrders.sumOf { it.totalPrice })
            totalOrdersCount = String.format(Locale("vi", "VN"), "%,d", completedOrders.size)
            calculateWeeklyChart(fetchedOrders)
            calculateTopDishes(completedOrders, storeDishes)
            lastUpdated = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        }.onFailure {
            Log.e("RestaurantHomeVM", "Lỗi tải thống kê MySQL: ${it.message}")
        }
        _isLoading.value = false
    }

    private fun calculateWeeklyChart(allOrders: List<Order>) {
        val summary = adminRevenueSummary(allOrders, LocalDate.now(), AdminPeriod.WEEK)
        val maxRevenue = summary.days.maxOfOrNull { it.revenue }?.takeIf { it > 0.0 } ?: 1.0
        weeklyChartData = summary.days.map { (it.revenue / maxRevenue).toFloat() }
    }

    private fun calculateTopDishes(completedOrders: List<Order>, storeDishes: List<Dish>) {
        val dishById = storeDishes.associateBy { it.id }
        val dishByName = storeDishes.associateBy { it.name.trim().lowercase(Locale.ROOT) }

        data class DishAgg(var count: Int = 0, var displayName: String = "", var imageUrl: String = "")
        val aggMap = LinkedHashMap<String, DishAgg>()

        completedOrders.flatMap { it.items }.forEach { item ->
            val rawName = item.name.trim()
            if (rawName.isBlank()) return@forEach
            val key = if (item.dishId.isNotBlank()) "id:${item.dishId}" else "name:${rawName.lowercase(Locale.ROOT)}"
            val resolvedDish = dishById[item.dishId] ?: dishByName[rawName.lowercase(Locale.ROOT)]
            val resolvedImage = item.imageUrl.trim()
                .ifBlank { resolvedDish?.imageUrl?.trim().orEmpty() }
            val resolvedName = resolvedDish?.name?.takeIf { it.isNotBlank() } ?: rawName

            val current = aggMap.getOrPut(key) { DishAgg(0, resolvedName, resolvedImage) }
            current.count += item.quantity
            if (current.imageUrl.isBlank() && resolvedImage.isNotBlank()) {
                current.imageUrl = resolvedImage
            }
        }

        _topDishes.value = aggMap.values
            .sortedByDescending { it.count }
            .take(5)
            .mapIndexed { index, entry ->
                TopDish(
                    rank = index + 1,
                    dishName = entry.displayName,
                    ordersCount = entry.count,
                    imageUrl = entry.imageUrl
                )
            }
    }
}
