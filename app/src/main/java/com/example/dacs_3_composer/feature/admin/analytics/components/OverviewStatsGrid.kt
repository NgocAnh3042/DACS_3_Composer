package com.example.dacs_3_composer.feature.admin.analytics.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocalMall
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dacs_3_composer.feature.admin.analytics.AnalyticsState

@Composable
fun OverviewStatsGrid(data: AnalyticsState) { // 🎯 KHAI BÁO THAM SỐ 'data' Ở ĐÂY
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- PHẦN 1: THÀNH VIÊN ---
        Text(text = "Thành viên hệ thống", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF727785))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AnalyticsStatCard(
                title = "Tổng người dùng", value = "${data.totalUsers}", trend = "Đang hoạt động", isPositive = true,
                icon = Icons.Default.Group, iconBg = Color(0xFFE8F0FE), iconColor = Color(0xFF0052CC),
                modifier = Modifier.weight(1f)
            )
            AnalyticsStatCard(
                title = "Tổng nhà hàng", value = "${data.totalRestaurants}", trend = "Đối tác", isPositive = true,
                icon = Icons.Default.Storefront, iconBg = Color(0xFFFEF7E0), iconColor = Color(0xFFF1C40F),
                modifier = Modifier.weight(1f)
            )
            AnalyticsStatCard(
                title = "Tổng shipper", value = "${data.totalShippers}", trend = "Giao vận", isPositive = true,
                icon = Icons.Default.LocalShipping, iconBg = Color(0xFFE8F8F0), iconColor = Color(0xFF2ECC71),
                modifier = Modifier.weight(1f)
            )
        }

        // --- PHẦN 2: ĐƠN HÀNG ---
        Text(text = "Tình trạng đơn hàng", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF727785))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AnalyticsStatCard(
                title = "Tổng đơn hàng", value = "${data.totalOrders}", trend = "Tích lũy", isPositive = true,
                icon = Icons.Default.Assignment, iconBg = Color(0xFFF1F3F4), iconColor = Color(0xFF5F6368),
                modifier = Modifier.weight(1f)
            )
            AnalyticsStatCard(
                title = "Đơn đang giao", value = "${data.shippingOrders} đơn", trend = "Realtime", isPositive = true,
                icon = Icons.Default.LocalMall, iconBg = Color(0xFFE8EFFF), iconColor = Color(0xFF3B82F6),
                modifier = Modifier.weight(1f)
            )
            AnalyticsStatCard(
                title = "Đơn hoàn thành", value = "${data.completedOrders}", trend = "Thành công", isPositive = true,
                icon = Icons.Default.CheckCircle, iconBg = Color(0xFFEAFAF1), iconColor = Color(0xFF27AE60),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AnalyticsStatCard(
    title: String, value: String, trend: String, isPositive: Boolean,
    icon: ImageVector, iconBg: Color, iconColor: Color, modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(modifier = Modifier.size(32.dp).background(iconBg, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = title, fontSize = 11.sp, color = Color(0xFF727785), maxLines = 1)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = trend, fontSize = 10.sp, color = if (isPositive) Color(0xFF2ECC71) else Color(0xFFE74C3C))
        }
    }
}