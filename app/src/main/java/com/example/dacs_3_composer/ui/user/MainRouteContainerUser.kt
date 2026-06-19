package com.example.dacs_3_composer.ui.user

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
import com.example.dacs_3_composer.data.model.Restaurant
import com.example.dacs_3_composer.ui.user.home.HomeScreen
import com.example.dacs_3_composer.ui.user.order.OrderScreen
import com.example.dacs_3_composer.ui.user.order.OrderDetailScreen
import com.example.dacs_3_composer.ui.user.order.OrderViewModel
import com.example.dacs_3_composer.ui.user.profile.ProfileScreen
import com.example.dacs_3_composer.ui.user.profileRestaurant.RestaurantDetailScreen
import com.example.dacs_3_composer.ui.user.search.SearchScreen
import com.example.dacs_3_composer.ui.user.cart.CartViewModel
import com.example.dacs_3_composer.ui.user.cart.CartScreen
import com.example.dacs_3_composer.ui.user.notification.NotificationScreen
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.dacs_3_composer.data.remote.PaymentApiService
import com.example.dacs_3_composer.data.repository.PaymentRepository
import com.example.dacs_3_composer.ui.user.payment.PaymentScreen
import com.example.dacs_3_composer.ui.user.payment.PaymentViewModel
import com.example.dacs_3_composer.ui.user.payment.PaymentHistoryScreen
import com.example.dacs_3_composer.ui.chat.MessageCenterScreen
import com.example.dacs_3_composer.ui.chat.ChatDetailScreen
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.Proxy
import java.util.concurrent.TimeUnit

@Composable
fun MainRouteContainerUser(
    onLogout: () -> Unit = {}
) {
    val navController = rememberNavController()
    val cartViewModel: CartViewModel = viewModel()
    val orderViewModel: OrderViewModel = viewModel()

    val okHttpClient = remember {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        OkHttpClient.Builder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .proxy(Proxy.NO_PROXY)
            .retryOnConnectionFailure(true)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    val retrofit = Retrofit.Builder()
        .baseUrl("http://127.0.0.1:8888/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val paymentApiService = retrofit.create(PaymentApiService::class.java)
    val paymentRepository = PaymentRepository(paymentApiService)

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

    Scaffold(
        bottomBar = { BottomBarUser(navController = navController) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            NavHost(navController = navController, startDestination = "home") {
                composable("home") {
                    HomeScreen(
                        navController = navController,
                        onNavigateToSearch = { query -> navController.navigate("search/$query") },
                        onNavigateToChat = { navController.navigate("message_center") }
                    )
                }
                composable("notification") { NotificationScreen() }
                
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
                    com.example.dacs_3_composer.ui.user.profile.AddressScreen(onBackClick = { navController.popBackStack() })
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

    NavigationBar {
        NavigationBarItem(
            selected = currentRoute == "home",
            onClick = { navController.navigate("home") { popUpTo(navController.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } },
            icon = { Icon(Icons.Default.Home, contentDescription = null) },
            label = { Text("Home") }
        )
        NavigationBarItem(
            selected = currentRoute == "notification",
            onClick = { navController.navigate("notification") { popUpTo(navController.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } },
            icon = { Icon(Icons.Default.Notifications, contentDescription = null) },
            label = { Text("Notification") }
        )
        NavigationBarItem(
            selected = currentRoute == "order",
            onClick = { navController.navigate("order") { popUpTo(navController.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } },
            icon = { Icon(Icons.Default.Receipt, contentDescription = null) },
            label = { Text("Product") }
        )
        NavigationBarItem(
            selected = currentRoute == "profile",
            onClick = { navController.navigate("profile") { popUpTo(navController.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } },
            icon = { Icon(Icons.Default.Person, contentDescription = null) },
            label = { Text("Profile") }
        )
    }
}
