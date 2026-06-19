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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.dacs_3_composer.ui.admin.analytics.AdminAnalyticsScreen
import com.example.dacs_3_composer.ui.admin.complaint.AdminComplaintScreen
import com.example.dacs_3_composer.ui.admin.customer.AdminCustomerScreen
import com.example.dacs_3_composer.ui.admin.profile.AdminProfileScreen
import com.example.dacs_3_composer.ui.admin.payments.AdminPaymentScreen
import com.example.dacs_3_composer.ui.admin.settings.AdminPromotionScreen
import com.example.dacs_3_composer.ui.admin.settings.AdminPromotionViewModel
import com.example.dacs_3_composer.ui.chat.MessageCenterScreen
import com.example.dacs_3_composer.ui.chat.ChatDetailScreen

@Composable
fun MainRouteContainerAdmin(
    onLogoutCallback: () -> Unit,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()

    val navigationItems = listOf(
        NavigationAdmin.Overview,
        NavigationAdmin.Customers,
        NavigationAdmin.Promotions,
        NavigationAdmin.Profile
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // 🎯 CẬP NHẬT: Ẩn BottomBar khi vào mục Chat
    val showBottomBar = currentRoute !in listOf("message_center", "chat_detail/{conversationId}")

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    modifier = Modifier.clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                    containerColor = Color.White,
                    tonalElevation = 8.dp
                ) {
                    navigationItems.forEach { item ->
                        val isSelected = currentRoute == item.route
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                if (currentRoute != item.route) {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(imageVector = item.icon, contentDescription = item.title, modifier = Modifier.size(24.dp)) },
                            label = { Text(text = item.title, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = Color(0xFF2159BC),
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
            startDestination = NavigationAdmin.Overview.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(NavigationAdmin.Overview.route) {
                AdminAnalyticsScreen(
                    onNavigateToChat = { navController.navigate("message_center") } // ✅ ĐIỀU HƯỚNG CHAT
                )
            }

            composable(NavigationAdmin.Orders.route) { AdminComplaintScreen() }

            composable(NavigationAdmin.Customers.route) { AdminCustomerScreen() }

            composable(NavigationAdmin.Profile.route) {
                AdminProfileScreen(
                    onNavigateToVehicleManagement = { },
                    onNavigateToNotification = { },
                    onNavigateToSupport = { navController.navigate("message_center") }, // ✅ Tận dụng message center cho admin
                    onLogoutCallbackk = (onLogoutCallback)
                )
            }

            composable(NavigationAdmin.Promotions.route) {
                val promotionViewModel: AdminPromotionViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                AdminPromotionScreen(
                    viewModel = promotionViewModel,
                    onBackClick = { navController.popBackStack() },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 🎯 THÊM ROUTE CHAT CHO ADMIN
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
        }
    }
}
