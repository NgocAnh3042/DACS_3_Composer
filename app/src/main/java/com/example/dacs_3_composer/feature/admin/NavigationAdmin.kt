package com.example.dacs_3_composer.feature.admin

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.InsertChart
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector

sealed class NavigationAdmin(val route: String, val title: String, val icon: ImageVector) {
    object Overview : NavigationAdmin("super_admin_overview", "Tổng quan", Icons.Default.InsertChart)
    object Orders : NavigationAdmin("super_admin_orders", "Đơn hàng", Icons.Default.Assignment)
    object Customers : NavigationAdmin("super_admin_customers", "Người dùng", Icons.Default.People)
    object Profile : NavigationAdmin("super_admin_profile", "Cá nhân", Icons.Default.Person)
    object Promotions : NavigationAdmin("super_admin_promotions", "Khuyến mãi", Icons.Default.ConfirmationNumber)
}
