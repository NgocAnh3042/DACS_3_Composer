package com.example.dacs_3_composer.feature.restaurant.home

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dacs_3_composer.core.model.Order
import com.example.dacs_3_composer.core.model.TopDish
import com.example.dacs_3_composer.core.model.User
import com.example.dacs_3_composer.data.remote.FoodHubClient
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class RestaurantHomeViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()

    var totalRevenue by mutableStateOf("0")
        private set
    var totalOrdersCount by mutableStateOf("0")
        private set

    // Thêm các biến lưu thông tin nhà hàng
    var restaurantName by mutableStateOf("")
        private set
    var restaurantAvatarUrl by mutableStateOf("")
        private set

    private val _topDishes = MutableStateFlow<List<TopDish>>(emptyList())
    val topDishes: StateFlow<List<TopDish>> = _topDishes

    // Mảng lưu tỷ lệ chiều cao biểu đồ cho 7 ngày trong tuần (T2 -> CN) từ 0.0f đến 1.0f
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
        viewModelScope.launch { runCatching { FoodHubClient.get<User>("users", "me") }.onSuccess { restaurantName=it.getDisplayName();restaurantAvatarUrl=it.avatarUrl }.onFailure { Log.e("RestaurantHomeVM", "Lỗi tải hồ sơ MySQL: ${it.message}") } }
    }

    fun fetchDashboardData() {
        if (currentRestaurantId.isBlank()) return
        viewModelScope.launch {
            while(true) {
                runCatching { FoodHubClient.list<Order>("orders") }.onSuccess { orders ->
                    val completedOrders=orders.filter { it.status=="COMPLETED" }
                    totalRevenue=String.format(Locale("vi","VN"),"%,.0f",completedOrders.sumOf { it.totalPrice })
                    totalOrdersCount=String.format(Locale("vi","VN"),"%,d",completedOrders.size)
                    calculateWeeklyChart(completedOrders);calculateTopDishes(completedOrders)
                }.onFailure { Log.e("RestaurantHomeVM","Lỗi tải thống kê MySQL: ${it.message}") }
                delay(10_000)
            }
        }
    }

    private fun calculateWeeklyChart(orders: List<Order>) {
        val daysRevenue = mutableMapOf(
            Calendar.MONDAY to 0.0, Calendar.TUESDAY to 0.0, Calendar.WEDNESDAY to 0.0,
            Calendar.THURSDAY to 0.0, Calendar.FRIDAY to 0.0, Calendar.SATURDAY to 0.0, Calendar.SUNDAY to 0.0
        )

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        orders.forEach { order ->
            try {
                val date = sdf.parse(order.time) ?: java.util.Date()
                val cal = Calendar.getInstance().apply { time = date }
                val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                if (daysRevenue.containsKey(dayOfWeek)) {
                    daysRevenue[dayOfWeek] = daysRevenue[dayOfWeek]!! + order.totalPrice
                }
            } catch (e: Exception) {
                val todayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
                daysRevenue[todayOfWeek] = daysRevenue[todayOfWeek]!! + order.totalPrice
            }
        }

        val maxRevenue = daysRevenue.values.maxOrNull() ?: 1.0
        val safeMax = if (maxRevenue == 0.0) 1.0 else maxRevenue

        weeklyChartData = listOf(
            (daysRevenue[Calendar.MONDAY]!! / safeMax).toFloat(),
            (daysRevenue[Calendar.TUESDAY]!! / safeMax).toFloat(),
            (daysRevenue[Calendar.WEDNESDAY]!! / safeMax).toFloat(),
            (daysRevenue[Calendar.THURSDAY]!! / safeMax).toFloat(),
            (daysRevenue[Calendar.FRIDAY]!! / safeMax).toFloat(),
            (daysRevenue[Calendar.SATURDAY]!! / safeMax).toFloat(),
            (daysRevenue[Calendar.SUNDAY]!! / safeMax).toFloat()
        )
    }

    private fun calculateTopDishes(orders: List<Order>) {
        val dishCountMap = mutableMapOf<String, Int>()

        orders.flatMap { it.items }.forEach { item ->
            if (item.name.isNotBlank()) {
                dishCountMap[item.name] = (dishCountMap[item.name] ?: 0) + item.quantity
            }
        }

        val sortedDishes = dishCountMap.entries
            .sortedByDescending { it.value }
            .take(3)
            .mapIndexed { index, entry ->
                TopDish(
                    rank = index + 1,
                    dishName = entry.key,
                    ordersCount = entry.value,
                    imageUrl = ""
                )
            }

        _topDishes.value = sortedDishes
    }
}
