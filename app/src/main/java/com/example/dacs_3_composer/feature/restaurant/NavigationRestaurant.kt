package com.example.dacs_3_composer.feature.restaurant

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.ui.graphics.vector.ImageVector

sealed class NavigationRestaurant(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : NavigationRestaurant("admin_dashboard", "Tổng quan", Icons.Default.Dashboard)
    object Orders : NavigationRestaurant("admin_orders", "Đơn hàng", Icons.Default.RestaurantMenu)
    object Store : NavigationRestaurant("admin_store", "Cửa hàng", Icons.Default.Storefront)
    object Menu : NavigationRestaurant("admin_menu", "Thực đơn", Icons.Default.MenuBook)
    object Account : NavigationRestaurant("admin_account", "Tài khoản", Icons.Default.People)
}