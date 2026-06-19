package com.example.dacs_3_composer.ui.user.home

import androidx.compose.foundation.clickable
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
import com.example.dacs_3_composer.ui.user.cart.CartViewModel
import com.example.dacs_3_composer.ui.user.home.components.*
import com.example.dacs_3_composer.ui.user.search.components.SectionTitle

@Composable
fun HomeScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    onNavigateToSearch: (String) -> Unit,
    onNavigateToChat: () -> Unit, // 🎯 THÊM: Điều hướng tới MessageCenter
    homeViewModel: HomeViewModel = viewModel(),
    cartViewModel: CartViewModel = viewModel()
) {
    val restaurants by homeViewModel.restaurantsList.collectAsState()
    val isLoading by homeViewModel.isLoading.collectAsState()

    LaunchedEffect(Unit) {
        homeViewModel.loadUserData()
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF1E56A0))
        }
    } else {
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

            item { Banner() }
            item { CategorySectionn() }

            item {
                DeliciousDishSection(navController = navController, cartViewModel = cartViewModel)
            }

            item {
                SectionTitle(
                    title = "Quán ngon gợi ý",
                    onSeeAllClick = { /* xử lý nếu cần */ }
                )
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
}
