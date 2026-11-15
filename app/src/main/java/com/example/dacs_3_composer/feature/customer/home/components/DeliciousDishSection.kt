package com.example.dacs_3_composer.feature.customer.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.dacs_3_composer.feature.customer.cart.CartViewModel
import com.example.dacs_3_composer.feature.customer.home.HomeViewModel

@Composable
fun DeliciousDishSection(
    navController: NavController,
    viewModel: HomeViewModel = viewModel(),
    cartViewModel: CartViewModel = viewModel() // 🌟 TÍCH HỢP: Nhận CartViewModel từ HomeScreen truyền xuống
) {
    val allListDishes by viewModel.deliciousDishes.collectAsState()
    val row1Dishes = allListDishes

    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Món ngon cho bạn",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Xem tất cả",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            items(row1Dishes) { dish ->
                DeliciousDishItem(dish = dish, onClick = {
                    // 🎯 ĐỒNG BỘ DỮ LIỆU: Lưu vết ID nhà hàng của món ăn này vào hệ thống Giỏ hàng
                    cartViewModel.currentRestaurantId = dish.restaurantId
                    cartViewModel.currentRestaurantName = "Nhà hàng đối tác" // Tạm gán mặc định nếu cấu trúc Dish chưa lưu kèm chuỗi tên Quán

                    // Chuyển màn hình và đưa id nhà hàng
                    navController.navigate("restaurant_detail/${dish.restaurantId}")
                })
            }
        }
    }
}