package com.example.dacs_3_composer.feature.restaurant.profile.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SystemSettingsSection(
    onStoreInfoClick: () -> Unit,
    onNotificationSettingClick: () -> Unit,
    onActivityHistoryClick: () -> Unit,
    onSecurityClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "CÀI ĐẶT HỆ THỐNG",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF727785),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column {
                RestauranSettingItem(
                    icon = Icons.Default.Storefront,
                    title = "Thông tin cửa hàng",
                    onClick = onStoreInfoClick
                )
                HorizontalDivider(color = Color(0xFFF1F3F4), thickness = 1.dp)
                RestauranSettingItem(
                    icon = Icons.Default.NotificationsActive,
                    title = "Cài đặt thông báo",
                    onClick = onNotificationSettingClick
                )
                HorizontalDivider(color = Color(0xFFF1F3F4), thickness = 1.dp)
                RestauranSettingItem(
                    icon = Icons.Default.History,
                    title = "Lịch sử hoạt động",
                    onClick = onActivityHistoryClick
                )
                HorizontalDivider(color = Color(0xFFF1F3F4), thickness = 1.dp)
                RestauranSettingItem(
                    icon = Icons.Default.Security,
                    title = "Bảo mật",
                    onClick = onSecurityClick
                )
            }
        }
    }
}
