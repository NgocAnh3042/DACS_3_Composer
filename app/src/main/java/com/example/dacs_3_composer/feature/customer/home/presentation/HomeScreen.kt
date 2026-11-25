package com.example.dacs_3_composer.feature.customer.home.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.dacs_3_composer.feature.customer.cart.presentation.CartViewModel
import com.example.dacs_3_composer.core.ui.FoodHubPullToRefresh
import com.example.dacs_3_composer.feature.customer.home.components.*
import com.example.dacs_3_composer.feature.customer.search.components.SectionTitle
import com.example.dacs_3_composer.feature.shared.location.DeliveryEstimate
import com.example.dacs_3_composer.feature.shared.location.rememberUserCoordinates

@Composable
fun HomeScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    onNavigateToSearch: (String) -> Unit,
    onNavigateToChat: () -> Unit,
    homeViewModel: HomeViewModel = hiltViewModel(),
    cartViewModel: CartViewModel = viewModel()
) {
    val homeUiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val cartUiState by cartViewModel.uiState.collectAsStateWithLifecycle()
    val dishes = homeUiState.deliciousDishes
    val restaurants = homeUiState.restaurantsList
    val isLoading = homeUiState.isLoading
    val personalizedIds = homeUiState.personalizedDishIds
    val isPersonalizedFeed = homeUiState.isPersonalizedFeed
    val categories = homeUiState.categories
    val selectedCategory = homeUiState.selectedCategory
    val userCoords = rememberUserCoordinates(requestPermissionIfNeeded = true)

    var showAllCategoriesSheet by remember { mutableStateOf(false) }

    val enrichedRestaurants = remember(restaurants, userCoords) {
        restaurants
            .map { DeliveryEstimate.enrichRestaurant(it, userCoords?.first, userCoords?.second) }
            .sortedBy { it.distanceKm ?: Double.MAX_VALUE }
    }

    val displayedDishes = remember(dishes, selectedCategory) {
        HomeViewModel.filterDishesByCategory(dishes, selectedCategory)
    }

    val displayedRestaurants = remember(enrichedRestaurants, dishes, selectedCategory) {
        HomeViewModel.filterRestaurantsByCategory(enrichedRestaurants, dishes, selectedCategory)
    }
    var visibleRestaurantLimit by remember(selectedCategory) { mutableIntStateOf(20) }
    val visibleRestaurants = remember(displayedRestaurants, visibleRestaurantLimit) { displayedRestaurants.take(visibleRestaurantLimit) }

    LaunchedEffect(Unit) {
        homeViewModel.loadHome()
        cartViewModel.fetchPromotionsFromFirebase()
    }

    if (showAllCategoriesSheet) {
        AllCategoriesBottomSheet(
            categories = categories,
            totalDishCount = dishes.size,
            selectedCategory = selectedCategory,
            onSelectCategory = { cat -> homeViewModel.setCategoryFilter(cat) },
            onOpenFullMenu = {
                showAllCategoriesSheet = false
                navController.navigate("all_dishes") { launchSingleTop = true }
            },
            onDismiss = { showAllCategoriesSheet = false }
        )
    }

    FoodHubPullToRefresh(
        refreshing = homeUiState.isRefreshing,
        onRefresh = { homeViewModel.refreshAll() },
        modifier = modifier.fillMaxSize()
    ) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Header(
                userName = homeUiState.userName,
                userAddress = homeUiState.userAddress,
                userImageUrl = homeUiState.userImageUrl,
                onSearchAction = { query ->
                    onNavigateToSearch(query)
                },
                onChatClick = onNavigateToChat
            )
        }

        item { Banner(cartUiState.availablePromotions.filter { it.status == "active" && (it.usageLimit == 0L || it.usageCount < it.usageLimit) }) }
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
        item {
            CategorySection(
                categories = categories,
                totalDishCount = dishes.size,
                selectedCategory = selectedCategory,
                isLoading = homeUiState.dishesLoading && categories.isEmpty(),
                onCategoryClick = { categoryName ->
                    homeViewModel.toggleCategory(categoryName)
                },
                onSeeAllClick = {
                    showAllCategoriesSheet = true
                }
            )
        }

        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = selectedCategory?.let { "Danh mục: $it" } ?: "Món ngon cho bạn",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (selectedCategory != null) {
                        Text(
                            text = "Có ${displayedDishes.size} món ăn thuộc nhóm $selectedCategory",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF2159BC)
                        )
                    } else if (isPersonalizedFeed) {
                        Text(
                            text = "Ưu tiên món liên quan đến tìm kiếm gần đây của bạn",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF727785)
                        )
                    }
                }
                if (selectedCategory != null) {
                    AssistChip(
                        onClick = { homeViewModel.setCategoryFilter(null) },
                        label = { Text("Bỏ lọc", style = MaterialTheme.typography.labelMedium) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Bỏ lọc danh mục",
                                modifier = Modifier.size(15.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color(0xFFEAF2FA),
                            labelColor = Color(0xFF2159BC),
                            trailingIconContentColor = Color(0xFF2159BC)
                        ),
                        border = null
                    )
                } else {
                    IconButton(
                        onClick = { homeViewModel.refreshDeliciousDishes() },
                        enabled = !homeUiState.dishesLoading
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Làm mới gợi ý món",
                            tint = Color(0xFF2159BC)
                        )
                    }
                }
                TextButton(onClick = { navController.navigate("all_dishes") { launchSingleTop = true } }) {
                    Text("Tất cả", color = Color(0xFF2159BC))
                }
            }
        }

        if (homeUiState.dishesLoading && dishes.isEmpty()) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(2) {
                        Surface(Modifier.weight(1f).height(170.dp), shape = RoundedCornerShape(18.dp), color = Color(0xFFE9EDF3)) { }
                    }
                }
            }
        } else if (displayedDishes.isEmpty() && selectedCategory != null) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Hiện chưa có món nào thuộc danh mục \"$selectedCategory\".", color = Color(0xFF6B7280))
                        OutlinedButton(onClick = { homeViewModel.setCategoryFilter(null) }) {
                            Text("Xem tất cả món ăn", color = Color(0xFF2159BC))
                        }
                    }
                }
            }
        }

        val maxHomeDishes = if (selectedCategory != null) 40 else 20
        items(displayedDishes.take(maxHomeDishes).chunked(2), key = { "dishes-" + (selectedCategory ?: "all") + "-" + it.first().id }) { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { dish ->
                    DeliciousDishItem(
                        dish = dish,
                        modifier = Modifier.weight(1f),
                        isRecommended = dish.id in personalizedIds
                    ) {
                        navController.navigate("dish_detail/${dish.id}")
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        item {
            SectionTitle(
                title = selectedCategory?.let { "Quán có món $it (${displayedRestaurants.size})" } ?: "Quán ngon gợi ý",
                onSeeAllClick = { navController.navigate("all_dishes") { launchSingleTop = true } }
            )
        }

        if (isLoading && restaurants.isEmpty()) {
            item {
                Surface(Modifier.fillMaxWidth().height(150.dp), shape = RoundedCornerShape(16.dp), color = Color(0xFFE9EDF3)) { }
            }
        }
        if (homeUiState.dishesFailed || homeUiState.restaurantsFailed) {
            item { Text("Đang hiển thị dữ liệu đã lưu; chưa thể làm mới.", color = Color.Gray, style = MaterialTheme.typography.bodySmall) }
        }
        items(visibleRestaurants, key = { it.id }) { restaurant ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        cartViewModel.setCurrentRestaurant(restaurant.id, restaurant.name)
                        navController.navigate("restaurant_detail/${restaurant.id}")
                    }
            ) {
                RestaurantItem(restaurant = restaurant)
            }
        }
        if (visibleRestaurants.size < displayedRestaurants.size) {
            item(key = "load-more-restaurants") {
                TextButton(onClick = { visibleRestaurantLimit += 20 }, modifier = Modifier.fillMaxWidth()) { Text("Tải thêm quán") }
            }
        }
    }
    }
}
