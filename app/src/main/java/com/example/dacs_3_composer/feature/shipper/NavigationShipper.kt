package com.example.dacs_3_composer.feature.shipper

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.ui.graphics.vector.ImageVector

sealed class NavigationShipper(val route: String, val title: String, val icon: ImageVector?) {
    object Dashboard : NavigationShipper("shipper_dashboard", "Tổng quan", Icons.Default.GridView)
    object Orders : NavigationShipper("shipper_orders", "Đơn hàng", Icons.Default.Assignment)
    object Account : NavigationShipper("shipper_account", "Cá nhân", Icons.Default.Person)

    // Tuyến đường phụ (Không xuất hiện trên BottomBar)
    object OrderDetail : NavigationShipper("shipper_order_detail", "Chi tiết đơn hàng", null)
}