package com.example.dacs_3_composer.feature.customer

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.dacs_3_composer.feature.customer.home.HomeViewModel
import com.example.dacs_3_composer.feature.customer.home.components.AllDishesScreen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.example.dacs_3_composer.R
import com.example.dacs_3_composer.core.model.Restaurant
import com.example.dacs_3_composer.feature.customer.home.HomeScreen
import com.example.dacs_3_composer.feature.customer.order.OrderScreen
import com.example.dacs_3_composer.feature.customer.order.OrderDetailScreen
import com.example.dacs_3_composer.feature.customer.order.OrderViewModel
import com.example.dacs_3_composer.feature.customer.profile.ProfileScreen
import com.example.dacs_3_composer.feature.customer.profileRestaurant.RestaurantDetailScreen
import com.example.dacs_3_composer.feature.customer.search.presentation.SearchScreen
import com.example.dacs_3_composer.feature.customer.cart.CartViewModel
import com.example.dacs_3_composer.feature.customer.cart.CartScreen
import com.example.dacs_3_composer.feature.customer.notification.NotificationScreen
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.dacs_3_composer.data.repository.PaymentRepository
import com.example.dacs_3_composer.feature.customer.payment.PaymentScreen
import com.example.dacs_3_composer.feature.customer.payment.PaymentViewModel
import com.example.dacs_3_composer.feature.customer.payment.PaymentHistoryScreen
import com.example.dacs_3_composer.feature.chat.MessageCenterScreen
import com.example.dacs_3_composer.feature.chat.ChatDetailScreen

@Composable
fun MainRouteContainerUser(
    onLogout: () -> Unit = {}
) {
    val navController = rememberNavController()
    val cartViewModel: CartViewModel = viewModel()
    val orderViewModel: OrderViewModel = viewModel()

    val paymentRepository = remember { PaymentRepository() }

    val paymentViewModel: PaymentViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PaymentViewModel(paymentRepository) as T
            }
        }
    )

    val sampleRestaurant = listOf(Restaurant(
        name = "The Coffee House",
        description = "Cà phê, trà, bánh ngọt...",
        rating = 4.8f,
        time = "25-30 phút",
        distance = "1.2 km",
        promo = "Free Ship",
        imageRes = R.drawable.banner1
    ))

    val route = navController.currentBackStackEntryAsState().value?.destination?.route
    var selectedAiDish by remember { mutableStateOf<com.example.dacs_3_composer.core.model.Dish?>(null) }
    Scaffold(
        bottomBar = {
            if (route != "meal_advisor" && route != "all_dishes" && route?.startsWith("dish_detail/") != true) {
                BottomBarUser(navController = navController)
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
            NavHost(navController = navController, startDestination = "home") {
                composable("home") {
                    HomeScreen(
                        navController = navController,
                        cartViewModel = cartViewModel,
                        onNavigateToSearch = { query -> navController.navigate("search/$query") },
                        onNavigateToChat = { navController.navigate("message_center") }
                    )
                }
                composable("all_dishes") { entry ->
                    val home = remember(entry) { navController.getBackStackEntry("home") }
                    val model: HomeViewModel = viewModel(viewModelStoreOwner = home)
                    val dishes by model.deliciousDishes.collectAsState()
                    val categories by model.categories.collectAsState()
                    val selectedCategory by model.selectedCategory.collectAsState()
                    LaunchedEffect(model) { model.ensureDishes() }
                    AllDishesScreen(
                        dishes = dishes,
                        loading = model.dishesLoading,
                        failed = model.dishesFailed,
                        onRetry = model::ensureDishes,
                        onBack = { navController.popBackStack() },
                        categories = categories,
                        selectedCategory = selectedCategory,
                        onSelectCategory = model::setCategoryFilter
                    ) { dish ->
                        navController.navigate("dish_detail/${dish.id}")
                    }
                }
                composable(
                    route = "dish_detail/{dishId}",
                    arguments = listOf(navArgument("dishId") { type = NavType.StringType })
                ) { entry ->
                    val dishId = entry.arguments?.getString("dishId") ?: ""
                    val homeEntry = remember(entry) { runCatching { navController.getBackStackEntry("home") }.getOrNull() }
                    val homeModel: HomeViewModel? = homeEntry?.let { viewModel(viewModelStoreOwner = it) }
                    val initialDish = remember(dishId, homeModel, selectedAiDish) {
                        homeModel?.findDishById(dishId) ?: selectedAiDish?.takeIf { it.id == dishId }
                    }
                    val initialRestaurant = remember(initialDish, homeModel) {
                        initialDish?.let { d -> homeModel?.restaurantsList?.value?.firstOrNull { it.id == d.restaurantId } }
                    }
                    val relatedDishes = remember(initialDish, homeModel) {
                        initialDish?.let { d -> homeModel?.dishesOfRestaurant(d.restaurantId, d.id) }.orEmpty()
                    }
                    com.example.dacs_3_composer.feature.customer.dish.DishDetailScreen(
                        dishId = dishId,
                        initialDish = initialDish,
                        initialRestaurant = initialRestaurant,
                        relatedStoreDishes = relatedDishes,
                        cartViewModel = cartViewModel,
                        onBackClick = { navController.popBackStack() },
                        onOpenRestaurant = { restaurantId -> navController.navigate("restaurant_detail/$restaurantId") },
                        onOpenDish = { nextDishId -> navController.navigate("dish_detail/$nextDishId") },
                        onViewCartClick = { navController.navigate("cart") }
                    )
                }
                composable("notification") { NotificationScreen() }
                composable("meal_advisor") {
                    com.example.dacs_3_composer.feature.customer.ai.MealAdvisorScreen(
                        onBack = { navController.popBackStack() },
                        onOpenDish = { dishId ->
                            if (dishId.isNotBlank()) {
                                navController.navigate("dish_detail/$dishId")
                            }
                        },
                        onSelectDish = { dish -> selectedAiDish = dish },
                        onTrackOrder = { orderId ->
                            if (orderId.isNotBlank()) {
                                navController.navigate("order_tracking/$orderId")
                            }
                        },
                        onPayOrder = { orderId, amount ->
                            if (orderId.isNotBlank()) {
                                navController.navigate("payment/$orderId/$amount")
                            }
                        },
                        onManageAddresses = {
                            navController.navigate("manage_address")
                        }
                    )
                }
                
                composable("message_center") {
                    MessageCenterScreen(
                        onConversationClick = { convId -> navController.navigate("chat_detail/$convId") },
                        onBackClick = { navController.popBackStack() }
                    )
                }
                composable(
                    route = "chat_detail/{conversationId}",
                    arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val convId = backStackEntry.arguments?.getString("conversationId") ?: ""
                    ChatDetailScreen(conversationId = convId, onBackClick = { navController.popBackStack() })
                }

                composable("search/{query}") { backStackEntry ->
                    val query = backStackEntry.arguments?.getString("query") ?: ""
                    SearchScreen(searchQuery = query, navController = navController, onBackClick = { navController.popBackStack() })
                }
                composable("order") {
                    OrderScreen(
                        suggestedRestaurants = sampleRestaurant,
                        orderViewModel = orderViewModel,
                        chuyenHienThiTrangChiTiet = { orderId -> navController.navigate("order_tracking/$orderId") },
                        onNavigateToPayment = { orderId, amount -> navController.navigate("payment/$orderId/$amount") }
                    )
                }
                composable(
                    route = "order_tracking/{orderId}?status={status}",
                    arguments = listOf(
                        navArgument("orderId") { type = NavType.StringType },
                        navArgument("status") { type = NavType.StringType; nullable = true }
                    ),
                    deepLinks = listOf(navDeepLink { uriPattern = "dacs3://payment_callback?orderId={orderId}&status={status}" })
                ) { backStackEntry ->
                    val orderId = backStackEntry.arguments?.getString("orderId") ?: ""
                    val status = backStackEntry.arguments?.getString("status")

                    LaunchedEffect(status) {
                        if (status == "PAID") {
                            orderViewModel.updateOrderToPaid(orderId)
                        }
                    }

                    OrderDetailScreen(
                        orderId = orderId,
                        orderViewModel = orderViewModel,
                        onBackClick = { navController.popBackStack() },
                        onNavigateToPayment = { id, amount -> navController.navigate("payment/$id/$amount") },
                        onNavigateToChat = { convId -> navController.navigate("chat_detail/$convId") } // ✅ KẾT NỐI CHAT
                    )
                }
                composable("profile") {
                    ProfileScreen(
                        onLogoutClick = onLogout,
                        onNavigateToOrderHistory = { navController.navigate("order") { launchSingleTop = true } },
                        onNavigateToManageAddress = { navController.navigate("manage_address") },
                        onNavigateToPaymentHistory = { navController.navigate("payment_history") },
                        onNavigateToChatDetail = { convId -> navController.navigate("chat_detail/$convId") } // ✅ KẾT NỐI HỖ TRỢ
                    )
                }
                composable("manage_address") {
                    com.example.dacs_3_composer.feature.customer.profile.AddressScreen(onBackClick = { navController.popBackStack() })
                }
                composable("payment_history") {
                    PaymentHistoryScreen(onBackClick = { navController.popBackStack() })
                }
                composable("restaurant_detail/{restaurantId}") { backStackEntry ->
                    val restaurantId = backStackEntry.arguments?.getString("restaurantId") ?: ""
                    RestaurantDetailScreen(
                        restaurantId = restaurantId, cartViewModel = cartViewModel,
                        onBackClick = { navController.popBackStack() },
                        onAddToCart = { dish -> cartViewModel.addToCart(dish) },
                        onViewCartClick = { navController.navigate("cart") },
                        onNavigateToChat = { convId -> navController.navigate("chat_detail/$convId") } // ✅ KẾT NỐI CHAT QUÁN
                    )
                }
                composable("cart") {
                    CartScreen(
                        cartViewModel = cartViewModel,
                        onBackClick = { navController.popBackStack() },
                        onNavigateToPayment = { orderId, amount -> navController.navigate("payment/$orderId/$amount") }
                    )
                }
                composable("payment/{orderId}/{amount}", arguments = listOf(navArgument("orderId") { type = NavType.StringType }, navArgument("amount") { type = NavType.StringType })) { backStackEntry ->
                    val orderId = backStackEntry.arguments?.getString("orderId") ?: ""
                    val amountStr = backStackEntry.arguments?.getString("amount") ?: "0.0"
                    val amount = amountStr.toDoubleOrNull() ?: 0.0
                    PaymentScreen(orderId = orderId, amount = amount, viewModel = paymentViewModel, onPaymentFinished = {
                        cartViewModel.clearCart()
                        navController.navigate("order") { popUpTo("home") { inclusive = false } }
                    })
                }
            }
        }
    }
}

@Composable
fun BottomBarUser(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val mainDestinations = listOf("home", "notification", "order", "profile")
    if (currentRoute !in mainDestinations) return

    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = Color(0xFF2159BC),
        selectedTextColor = Color(0xFF2159BC),
        indicatorColor = Color(0xFFE8F0FE),
        unselectedIconColor = Color(0xFF7A8494),
        unselectedTextColor = Color(0xFF7A8494)
    )

    NavigationBar(
        containerColor = Color.White,
        tonalElevation = 4.dp
    ) {
        NavigationBarItem(
            selected = currentRoute == "home",
            onClick = { navController.navigate("home") { popUpTo(navController.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } },
            icon = { Icon(Icons.Default.Home, contentDescription = "Trang chủ") },
            label = { Text("Trang chủ") },
            colors = itemColors
        )
        NavigationBarItem(
            selected = currentRoute == "notification",
            onClick = { navController.navigate("notification") { popUpTo(navController.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } },
            icon = { Icon(Icons.Default.Notifications, contentDescription = "Thông báo") },
            label = { Text("Thông báo") },
            colors = itemColors
        )
        NavigationBarItem(
            selected = currentRoute == "order",
            onClick = { navController.navigate("order") { popUpTo(navController.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } },
            icon = { Icon(Icons.Default.Receipt, contentDescription = "Đơn hàng") },
            label = { Text("Đơn hàng") },
            colors = itemColors
        )
        NavigationBarItem(
            selected = currentRoute == "profile",
            onClick = { navController.navigate("profile") { popUpTo(navController.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } },
            icon = { Icon(Icons.Default.Person, contentDescription = "Tài khoản") },
            label = { Text("Tài khoản") },
            colors = itemColors
        )
    }
}
