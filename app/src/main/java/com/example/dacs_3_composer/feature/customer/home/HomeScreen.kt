package com.example.dacs_3_composer.feature.customer.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.dacs_3_composer.feature.customer.cart.CartViewModel
import com.example.dacs_3_composer.feature.customer.home.components.*
import com.example.dacs_3_composer.feature.customer.search.components.SectionTitle

@Composable
fun HomeScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    onNavigateToSearch: (String) -> Unit,
    onNavigateToChat: () -> Unit, // 🎯 THÊM: Điều hướng tới MessageCenter
    homeViewModel: HomeViewModel = viewModel(),
    cartViewModel: CartViewModel = viewModel()
) {
    val dishes by homeViewModel.deliciousDishes.collectAsState()
    val restaurants by homeViewModel.restaurantsList.collectAsState()
    val isLoading by homeViewModel.isLoading.collectAsState()

    LaunchedEffect(Unit) {
        homeViewModel.loadHome()
        cartViewModel.fetchPromotionsFromFirebase()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Header(
                userName = homeViewModel.userName,
                userAddress = homeViewModel.userAddress,
                userImageUrl = homeViewModel.userImageUrl,
                onSearchAction = { query ->
                    onNavigateToSearch(query)
                },
                onChatClick = onNavigateToChat // 🎯 TRUYỀN: Sự kiện từ Header lên
            )
        }

        item { Banner(cartViewModel.availablePromotions.filter { it.status == "active" && (it.usageLimit == 0L || it.usageCount < it.usageLimit) }) }
        item {
            Surface(
                onClick = { navController.navigate("meal_advisor") },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                color = Color(0xFFEAF2FA), contentColor = Color(0xFF173D66)
            ) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(44.dp).background(Color(0xFF2159BC), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Forum, null, Modifier.size(22.dp), tint = Color.White)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Hôm nay muốn ăn gì?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Hỏi FoodHub AI theo khẩu vị và ngân sách", style = MaterialTheme.typography.bodySmall)
                    }
                    Box(Modifier.size(36.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(20.dp), tint = Color(0xFF2159BC))
                    }
                }
            }
        }
        item { CategorySectionn() }

        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Món ngon cho bạn", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = { navController.navigate("all_dishes") { launchSingleTop = true } }) { Text("Tất cả") }
            }
        }
        items(dishes.take(20).chunked(2), key = { "dishes-" + it.first().id }) { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { dish ->
                    DeliciousDishItem(dish, Modifier.weight(1f)) {
                        cartViewModel.currentRestaurantId = dish.restaurantId
                        cartViewModel.currentRestaurantName = restaurants.find { it.id == dish.restaurantId }?.name.orEmpty()
                        navController.navigate("restaurant_detail/${dish.restaurantId}")
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item {
            SectionTitle(
                title = "Quán ngon gợi ý",
                onSeeAllClick = { /* xử lý nếu cần */ }
            )
        }

        if (isLoading && restaurants.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF1E56A0))
                }
            }
        }
        items(restaurants) { restaurant ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        cartViewModel.currentRestaurantId = restaurant.id
                        cartViewModel.currentRestaurantName = restaurant.name
                        navController.navigate("restaurant_detail/${restaurant.id}")
                    }
            ) {
                RestaurantItem(restaurant = restaurant)
            }
        }
    }
}
