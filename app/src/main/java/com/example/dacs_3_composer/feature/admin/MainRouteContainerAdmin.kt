package com.example.dacs_3_composer.feature.admin

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.example.dacs_3_composer.feature.chat.MessageCenterScreen
import com.example.dacs_3_composer.feature.chat.ChatDetailScreen
import com.example.dacs_3_composer.feature.admin.analytics.AdminAnalyticsScreen
import com.example.dacs_3_composer.feature.admin.complaint.AdminComplaintScreen
import com.example.dacs_3_composer.feature.admin.customer.AdminCustomerScreen
import com.example.dacs_3_composer.feature.admin.profile.AdminProfileScreen
import com.example.dacs_3_composer.feature.admin.components.AdminTheme
import com.example.dacs_3_composer.feature.admin.settings.AdminPromotionScreen
import com.example.dacs_3_composer.feature.admin.settings.AdminPromotionViewModel

@Composable
fun MainRouteContainerAdmin(
    onLogoutCallback: () -> Unit,
    modifier: Modifier = Modifier
) {
    AdminTheme { AdminRouteContent(onLogoutCallback, modifier) }
}

@Composable
private fun AdminRouteContent(
    onLogoutCallback: () -> Unit,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val navEntry by navController.currentBackStackEntryAsState()
    val route = navEntry?.destination?.route
    fun openTab(destination: String) {
        if (route != destination) navController.navigate(destination) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Bốn mục điều hướng chính của quản trị viên.
    val navigationItems = listOf(
        NavigationAdmin.Overview,
//         NavigationAdmin.Orders,
        NavigationAdmin.Customers,
        NavigationAdmin.Promotions,
        NavigationAdmin.Profile
    )

    Scaffold(
        floatingActionButton = { if (route == NavigationAdmin.Overview.route) androidx.compose.material3.ExtendedFloatingActionButton(onClick = { navController.navigate("foodhub_support") }) { androidx.compose.material3.Text("Trợ lý FoodHub") } },
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            if (route != "foodhub_support" && route != "admin_messages" && route?.startsWith("admin_chat/") != true) {
            NavigationBar(
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                containerColor = Color.White,
                tonalElevation = 8.dp
            ) {
                navigationItems.forEach { item ->
                    val isSelected = route == item.route

                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { openTab(item.route) },
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.title,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = Color(0xFF2159BC), // Màu xanh chủ đạo của app
                            indicatorColor = Color(0xFF2159BC),
                            unselectedIconColor = Color(0xFFC4C7C5),
                            unselectedTextColor = Color(0xFF727785)
                        )
                    )
                }
            }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = NavigationAdmin.Overview.route, // Màn hình mặc định ban đầu là Báo cáo & Thống kê
            modifier = Modifier.padding(paddingValues)
        ) {
            // Tab 1: Báo cáo & Thống kê
            composable("foodhub_support") {
                com.example.dacs_3_composer.feature.customer.ai.MealAdvisorScreen(
                    onBack = { navController.popBackStack() }, onOpenRestaurant = {}, staff = true)
            }
            composable(NavigationAdmin.Overview.route) {
                AdminAnalyticsScreen(
                    onNavigateToChat = { navController.navigate("admin_messages") },
                    onNavigateToUsers = { openTab(NavigationAdmin.Customers.route) },
                    onNavigateToPromotions = { openTab(NavigationAdmin.Promotions.route) },
                    onNavigateToProfile = { openTab(NavigationAdmin.Profile.route) }
                )
            }

            // Tab 2: Đơn hàng & Xử lý Khiếu nại
            composable(NavigationAdmin.Orders.route) {
                AdminComplaintScreen()
            }

//            composable(NavigationAdmin.Categories.route) {
//                AdminCategoryScreen()
//            }

            // Tab 5: Quản lý Khách hàng (Nhãn hiển thị UI: Cá nhân)
            composable(NavigationAdmin.Customers.route) {
                AdminCustomerScreen()
            }

            // Tab 3: Quản lý Hồ sơ & Cài đặt hệ thống
            composable(NavigationAdmin.Profile.route) {
                // 🎯 Đã loại bỏ 'onNavigateToAccountInfo' để khớp hoàn toàn với cấu trúc Dialog sửa tại chỗ của Screen
                AdminProfileScreen(
                    onNavigateToOverview = { openTab(NavigationAdmin.Overview.route) },
                    onNavigateToUsers = { openTab(NavigationAdmin.Customers.route) },
                    onNavigateToPromotions = { openTab(NavigationAdmin.Promotions.route) },
                    onNavigateToSupport = { navController.navigate("admin_messages") },
                    onLogoutCallbackk = (onLogoutCallback)
                )
            }
            composable(NavigationAdmin.Promotions.route) {
                // Khởi tạo ViewModel cho màn hình Voucher Admin
                val promotionViewModel: AdminPromotionViewModel = androidx.lifecycle.viewmodel.compose.viewModel()

                AdminPromotionScreen(
                    viewModel = promotionViewModel,
                    onBackClick = {
                        // Khi nhấn nút Back tại top bar, quay về màn hình Overview mặc định của Admin
                        navController.navigate(NavigationAdmin.Overview.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            composable("admin_messages") {
                MessageCenterScreen(onConversationClick = { navController.navigate("admin_chat/$it") }, onBackClick = { navController.popBackStack() })
            }
            composable("admin_chat/{conversationId}", arguments = listOf(navArgument("conversationId") { type = NavType.StringType })) { entry ->
                ChatDetailScreen(conversationId = entry.arguments?.getString("conversationId").orEmpty(), onBackClick = { navController.popBackStack() })
            }
        }
    }
}
