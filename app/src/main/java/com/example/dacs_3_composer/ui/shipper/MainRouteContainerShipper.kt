package com.example.dacs_3_composer.ui.shipper

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.dacs_3_composer.ui.shipper.dashboard.ShipperDashboardScreen
import com.example.dacs_3_composer.ui.shipper.dashboard.ShipperViewModel
import com.example.dacs_3_composer.ui.shipper.dashboard.detail.ShipperOrderDetailScreen
import com.example.dacs_3_composer.ui.shipper.orders.ShipperOrdersScreen
import com.example.dacs_3_composer.ui.shipper.profile.ShipperProfileScreen
import com.example.dacs_3_composer.ui.shipper.profile.ShipperEditProfileScreen
import com.example.dacs_3_composer.ui.shipper.profile.ShipperProfileViewModel
import com.example.dacs_3_composer.ui.chat.MessageCenterScreen
import com.example.dacs_3_composer.ui.chat.ChatDetailScreen
import com.example.dacs_3_composer.data.repository.ChatRepository
import kotlinx.coroutines.launch

@Composable
fun MainRouteContainerShipper(
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()
    val chatRepository = remember { ChatRepository() }

    val shipperViewModel: ShipperViewModel = viewModel()
    val profileViewModel: ShipperProfileViewModel = viewModel()

    val navigationItems = listOf(
        NavigationShipper.Dashboard,
        NavigationShipper.Orders,
        NavigationShipper.Account
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val shouldShowBottomBar = currentRoute?.startsWith("shipper_order_detail") != true
            && currentRoute != "shipper_edit_profile"
            && currentRoute != "message_center"
            && currentRoute?.startsWith("chat_detail") != true

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            if (shouldShowBottomBar) {
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
                            icon = {
                                item.icon?.let {
                                    Icon(imageVector = it, contentDescription = item.title, modifier = Modifier.size(24.dp))
                                }
                            },
                            label = { Text(text = item.title, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = Color(0xFF1A56DB),
                                indicatorColor = Color(0xFF1A56DB),
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
            startDestination = NavigationShipper.Dashboard.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(NavigationShipper.Dashboard.route) {
                ShipperDashboardScreen(
                    onOrderClick = { id -> navController.navigate("shipper_order_detail/$id") },
                    onNavigateToChat = { navController.navigate("message_center") }
                )
            }

            composable(NavigationShipper.Orders.route) {
                ShipperOrdersScreen(
                    viewModel = shipperViewModel,
                    onNavigateToDetail = { id -> navController.navigate("shipper_order_detail/$id") }
                )
            }

            composable(NavigationShipper.Account.route) {
                LaunchedEffect(Unit) { profileViewModel.loadProfile() }
                ShipperProfileScreen(
                    viewModel = profileViewModel,
                    onLogoutClick = { onLogout() },
                    onNavigateToSection = { sectionKey ->
                        when (sectionKey) {
                            "EDIT_PROFILE" -> navController.navigate("shipper_edit_profile")
                            "SUPPORT" -> {
                                // 🎯 ĐIỀU HƯỚNG CHAT VỚI ADMIN
                                coroutineScope.launch {
                                    try {
                                        val conversationId = chatRepository.contactSupport("shipper")
                                        navController.navigate("chat_detail/$conversationId")
                                    } catch (e: Exception) { }
                                }
                            }
                        }
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

            composable("shipper_edit_profile") {
                ShipperEditProfileScreen(viewModel = profileViewModel, onBackClick = { navController.popBackStack() })
            }

            composable(
                route = "shipper_order_detail/{orderId}",
                arguments = listOf(navArgument("orderId") { type = NavType.StringType })
            ) { backStackEntry ->
                val orderId = backStackEntry.arguments?.getString("orderId") ?: ""
                ShipperOrderDetailScreen(
                    orderId = orderId, 
                    onBackClick = { navController.popBackStack() },
                    onNavigateToChat = { convId -> navController.navigate("chat_detail/$convId") }
                )
            }
        }
    }
}
