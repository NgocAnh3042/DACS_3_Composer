package com.example.dacs_3_composer.feature.restaurant

import androidx.compose.foundation.layout.Box
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
import com.example.dacs_3_composer.data.repository.ChatRepository
import com.example.dacs_3_composer.feature.restaurant.home.RestaurantHomeScreen
import com.example.dacs_3_composer.feature.restaurant.menu.RestaurantMenuScreen
import com.example.dacs_3_composer.feature.restaurant.order.RestaurantOrderScreen
import com.example.dacs_3_composer.feature.restaurant.profile.RestauranProfileScreen
import com.example.dacs_3_composer.feature.restaurant.profile.settings.*
import com.example.dacs_3_composer.feature.restaurant.store.RestaurantManageScreen
import com.example.dacs_3_composer.feature.chat.MessageCenterScreen
import com.example.dacs_3_composer.feature.chat.ChatDetailScreen
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@Composable
fun MainRouteContainerRestaurant(
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val auth = FirebaseAuth.getInstance()
    val currentUserId = auth.currentUser?.uid ?: ""
    val coroutineScope = rememberCoroutineScope()
    val chatRepository = remember { ChatRepository() }

    val navigationItems = listOf(
        NavigationRestaurant.Dashboard,
        NavigationRestaurant.Orders,
        NavigationRestaurant.Store,
        NavigationRestaurant.Menu,
        NavigationRestaurant.Account
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

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
                        val isSettingsRoute = currentRoute in listOf(
                            "restaurant_info_settings", "notification_settings", 
                            "activity_history", "security_settings", "help_support"
                        )
                        val isSelected = currentRoute == item.route || (item == NavigationRestaurant.Account && isSettingsRoute)

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
            startDestination = NavigationRestaurant.Dashboard.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(NavigationRestaurant.Dashboard.route) {
                RestaurantHomeScreen(onMessageClick = { navController.navigate("message_center") })
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

            composable(NavigationRestaurant.Orders.route) { RestaurantOrderScreen() }

            composable(NavigationRestaurant.Store.route) {
                RestaurantManageScreen(
                    restaurantId = currentUserId,
                    onBackClick = { navController.popBackStack() },
                    onEditCoverClick = { },
                    onEditDishClick = { }
                )
            }

            composable(NavigationRestaurant.Menu.route) { RestaurantMenuScreen() }

            composable(NavigationRestaurant.Account.route) {
                RestauranProfileScreen(
                    onStoreInfoClick = { navController.navigate("restaurant_info_settings") },
                    onNotificationSettingClick = { navController.navigate("notification_settings") },
                    onActivityHistoryClick = { navController.navigate("activity_history") },
                    onSecurityClick = { navController.navigate("security_settings") },
                    onHelpClick = { navController.navigate("help_support") },
                    onLogoutClick = onLogout
                )
            }

            composable("restaurant_info_settings") { RestaurantInfoScreen(onBackClick = { navController.popBackStack() }) }
            composable("notification_settings") { NotificationSettingsScreen(onBackClick = { navController.popBackStack() }) }
            composable("activity_history") { ActivityHistoryScreen(onBackClick = { navController.popBackStack() }) }
            composable("security_settings") { SecurityScreen(onBackClick = { navController.popBackStack() }) }

            composable("help_support") {
                HelpSupportScreen(
                    onBackClick = { navController.popBackStack() },
                    onChatSupportClick = {
                        coroutineScope.launch {
                            try {
                                // Gọi repository để tạo/lấy hội thoại support thật
                                val convId = chatRepository.contactSupport(myRole = "RESTAURANT")
                                navController.navigate("chat_detail/$convId")
                            } catch (e: Exception) {
                                // Xử lý lỗi nếu cần
                            }
                        }
                    }
                )
            }
        }
    }
}
