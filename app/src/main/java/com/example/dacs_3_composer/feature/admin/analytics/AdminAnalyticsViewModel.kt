package com.example.dacs_3_composer.feature.admin.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dacs_3_composer.core.model.Order
import com.example.dacs_3_composer.core.model.User
import com.example.dacs_3_composer.core.model.RestaurantDetail
import com.example.dacs_3_composer.data.remote.FoodHubClient
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class AnalyticsState(
    val totalUsers: Int = 0,
    val totalRestaurants: Int = 0,
    val totalShippers: Int = 0,
    val totalOrders: Int = 0,
    val shippingOrders: Int = 0,
    val completedOrders: Int = 0,
    val weeklyRevenue: List<Double> = List(7) { 0.0 },
    val orders: List<Order> = emptyList(),
    val users: List<User> = emptyList(),
    val activeStores: Int = 0,
    val adminName: String = "",
    val isLoading: Boolean = true,
    val error: String? = null,
    val lastUpdated: String = ""
)

class AdminAnalyticsViewModel : ViewModel() {
    private val _analyticsState = MutableStateFlow(AnalyticsState())
    val analyticsState = _analyticsState.asStateFlow()
    private val loadMutex = Mutex()
    private var loaded = false

    init {
        viewModelScope.launch { while (true) { load(); delay(10_000) } }
    }

    fun refresh() { viewModelScope.launch { load(showLoading = true) } }

    private suspend fun load(showLoading: Boolean = false) = loadMutex.withLock {
        _analyticsState.value = _analyticsState.value.copy(isLoading = showLoading || !loaded)
        try {
            coroutineScope {
                val usersRequest = async { FoodHubClient.list<User>("users") }
                val ordersRequest = async { FoodHubClient.list<Order>("orders") }
                val storesRequest = async { FoodHubClient.list<RestaurantDetail>("stores") }
                val users = usersRequest.await()
                val orders = ordersRequest.await()
                val stores = storesRequest.await()
                val uid = FirebaseAuth.getInstance().currentUser?.uid
                _analyticsState.value = AnalyticsState(
                    totalUsers = users.size,
                    totalRestaurants = users.count { it.role in setOf("restaurant", "seller") },
                    totalShippers = users.count { it.role in setOf("shipper", "driver") },
                    totalOrders = orders.size,
                    shippingOrders = orders.count { it.status in setOf("PENDING", "PROCESSING", "ACCEPTED", "SHIPPING") },
                    completedOrders = orders.count { it.status == "COMPLETED" },
                    weeklyRevenue = adminRevenueSummary(orders, LocalDate.now(), AdminPeriod.WEEK).days.map { it.revenue },
                    orders = orders, users = users, activeStores = stores.size,
                    adminName = users.firstOrNull { it.uid == uid }?.getDisplayName().orEmpty(),
                    isLoading = false,
                    lastUpdated = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                )
                loaded = true
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            _analyticsState.value = _analyticsState.value.copy(isLoading = false, error = "Không tải được dữ liệu tổng quan. Hãy kiểm tra kết nối và thử lại.")
        }
    }
}
