package com.example.dacs_3_composer.ui.admin

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
import com.example.dacs_3_composer.ui.admin.analytics.AdminAnalyticsScreen
import com.example.dacs_3_composer.ui.admin.complaint.AdminComplaintScreen
import com.example.dacs_3_composer.ui.admin.customer.AdminCustomerScreen
import com.example.dacs_3_composer.ui.admin.profile.AdminProfileScreen
import com.example.dacs_3_composer.ui.admin.payments.AdminPaymentScreen
import com.example.dacs_3_composer.ui.admin.settings.AdminPromotionScreen
import com.example.dacs_3_composer.ui.admin.settings.AdminPromotionViewModel

@Composable
fun MainRouteContainerAdmin(
    onLogoutCallback: () -> Unit,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()

    // Danh sách 5 mục hiển thị dưới thanh BottomBar điều hướng của Super Admin
    val navigationItems = listOf(
        NavigationAdmin.Overview,
//         NavigationAdmin.Orders,
        NavigationAdmin.Customers,
        NavigationAdmin.Promotions,
        NavigationAdmin.Profile
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                containerColor = Color.White,
                tonalElevation = 8.dp
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                navigationItems.forEach { item ->
                    val isSelected = currentRoute == item.route

                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            if (currentRoute != item.route) {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
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
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = NavigationAdmin.Overview.route, // Màn hình mặc định ban đầu là Báo cáo & Thống kê
            modifier = Modifier.padding(paddingValues)
        ) {
            // Tab 1: Báo cáo & Thống kê
            composable(NavigationAdmin.Overview.route) {
                AdminAnalyticsScreen()
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
                    onNavigateToVehicleManagement = { /* Điều hướng tới màn hình quản lý xe nếu có */ },
                    onNavigateToNotification = { /* Điều hướng tới màn hình Payout Settings hoặc thông báo */ },
                    onNavigateToSupport = { /* Điều hướng tới bộ phận hỗ trợ */ },
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
        }
    }
}