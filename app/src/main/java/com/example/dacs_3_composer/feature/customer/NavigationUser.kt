package com.example.dacs_3_composer.feature.customer

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications // 🌟 Đổi import sang icon thông báo
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color

import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState

@Composable
fun BottomBar(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // 🌟 ĐỊNH NGHĨA DANH SÁCH MÀN HÌNH CHÍNH MUỐN HIỆN BOTTOMBAR
    // Nếu màn hình hiện tại không nằm trong danh sách này (ví dụ: "search/{query}"), BottomBar sẽ ẩn đi
    val mainDestinations = listOf("home", "notification", "order", "profile")
    if (currentRoute !in mainDestinations) return // 🚀 Tự động ẩn BottomBar ở màn hình chi tiết!

    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = Color(0xFF2159BC),
        selectedTextColor = Color(0xFF2159BC),
        indicatorColor = Color(0xFFE8F0FE),
        unselectedIconColor = Color(0xFF7A8494),
        unselectedTextColor = Color(0xFF7A8494)
    )

    NavigationBar(containerColor = Color.White, tonalElevation = 4.dp) {
        // 1. Home
        NavigationBarItem(
            selected = currentRoute == "home",
            onClick = {
                navController.navigate("home") {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            icon = { Icon(Icons.Default.Home, contentDescription = "Trang chủ") },
            label = { Text("Trang chủ") },
            colors = itemColors
        )

        // 2. 🌟 ĐÃ SỬA: Chuyển Search thành Thông báo
        NavigationBarItem(
            selected = currentRoute == "notification",
            onClick = {
                navController.navigate("notification") {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            icon = { Icon(Icons.Default.Notifications, contentDescription = "Thông báo") }, // Thêm icon chuông
            label = { Text("Thông báo") },
            colors = itemColors
        )

        // 3. Đơn hàng (Product)
        NavigationBarItem(
            selected = currentRoute == "order",
            onClick = {
                navController.navigate("order"){
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            icon = { Icon(Icons.Default.Receipt, contentDescription = "Đơn hàng") },
            label = { Text("Đơn hàng") },
            colors = itemColors
        )

        // 4. Cá nhân
        NavigationBarItem(
            selected = currentRoute == "profile",
            onClick = {
                navController.navigate("profile"){
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            icon = { Icon(Icons.Default.Person, contentDescription = "Cá nhân") },
            label = { Text("Tài khoản") },
            colors = itemColors
        )
    }
}
//             Khi nhấn vào home menu
//            NavController nhận lệnh điều hướng đến route tên "search".
//            Các tuỳ chọn đi kèm có tác dụng:
//            popUpTo(...): Xoá tất cả các màn hình khỏi stack cho đến khi gặp màn hình start destination (thường là "home").
//            Điều này đảm bảo khi bạn nhấn nút Back, bạn không quay lại hàng loạt màn hình cũ.
//            launchSingleTop = true: Nếu màn hình "search" đã ở đầu stack thì không tạo mới, chỉ tái sử dụng.
//            restoreState và saveState: Giúp lưu và khôi phục trạng thái của các màn hình (ví dụ danh sách cuộn, nội dung nhập liệu…).
