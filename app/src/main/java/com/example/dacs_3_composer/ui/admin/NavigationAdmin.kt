package com.example.dacs_3_composer.ui.admin

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.InsertChart
import androidx.compose.material.icons.filled.Moped
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.ui.graphics.vector.ImageVector

sealed class NavigationAdmin(val route: String, val title: String, val icon: ImageVector) {
    object Overview : NavigationAdmin("super_admin_overview", "Tổng quan", Icons.Default.InsertChart)
    object Orders : NavigationAdmin("super_admin_orders", "Đơn hàng", Icons.Default.Assignment)
    object Payments : NavigationAdmin("super_admin_payments", "Giao dịch", Icons.Default.Payments)
    object Customers : NavigationAdmin("super_admin_customers", "All Role", Icons.Default.People)
    object Profile : NavigationAdmin("super_admin_profile", "Profile", Icons.Default.Moped)
    object Promotions : NavigationAdmin("super_admin_promotions", "Voucher", Icons.Default.ConfirmationNumber)
}
