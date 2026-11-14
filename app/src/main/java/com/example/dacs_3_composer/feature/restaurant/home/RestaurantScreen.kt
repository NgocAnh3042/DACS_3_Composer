package com.example.dacs_3_composer.feature.restaurant.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dacs_3_composer.feature.restaurant.home.components.*
import com.example.dacs_3_composer.feature.restaurant.home.components.StatsMiniCardRow
import com.example.dacs_3_composer.feature.restaurant.home.components.WeeklyRevenueCard

@Composable
fun RestaurantHomeScreen(
    onMessageClick: () -> Unit,
    modifier: Modifier = Modifier,
    homeViewModel: RestaurantHomeViewModel = viewModel()
) {
    val topDishesList by homeViewModel.topDishes.collectAsState()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { 
            RestaurantTopBar(
                name = homeViewModel.restaurantName,
                avatarUrl = homeViewModel.restaurantAvatarUrl,
                onMessageClick = onMessageClick
            ) 
        }
        item { GreetingSection() }

        item { RevenueCard(revenue = homeViewModel.totalRevenue) }

        item { StatsMiniCardRow(totalOrders = homeViewModel.totalOrdersCount) }

        item { WeeklyRevenueCard(chartWeights = homeViewModel.weeklyChartData) }

        item { TopDishesSectionTitle() }

        items(topDishesList) { dish ->
            TopSellingDishItem(
                rank = dish.rank,
                dishName = dish.dishName,
                ordersCount = dish.ordersCount,
                imageUrl = dish.imageUrl
            )
        }
        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}
