package com.example.dacs_3_composer.feature.admin.complaint.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ComplaintStatsRow() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Thẻ Doanh thu
        StatRowCard(
            title = "TỔNG DOANH THU HÔM NAY",
            value = "42.850.000đ",
            subtitle = "↗ +12.5% so với hôm qua",
            icon = Icons.Outlined.CreditCard,
            iconBg = Color(0xFFE8F0FE),
            iconColor = Color(0xFF2159BC)
        )

        // Thẻ Đơn hàng mới
        StatRowCard(
            title = "Đơn hàng mới",
            value = "156",
            subtitle = "Chờ xác nhận: 12",
            icon = Icons.Outlined.ShoppingCart,
            iconBg = Color(0xFFF1F3F4),
            iconColor = Color(0xFF5F6368)
        )

        // Thẻ Khiếu nại mở (Có viền đỏ nổi bật)
        StatRowCard(
            title = "Khiếu nại mở",
            value = "08",
            subtitle = "Cần xử lý ngay",
            icon = Icons.Outlined.ErrorOutline,
            iconBg = Color(0xFFFFEAEA),
            iconColor = Color(0xFFE74C3C),
            borderColor = Color(0xFFFFD6D6),
            valueColor = Color(0xFFE74C3C)
        )
    }
}

@Composable
private fun StatRowCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    iconBg: Color,
    iconColor: Color,
    borderColor: Color? = null,
    valueColor: Color = Color.Black
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (borderColor != null) Modifier.border(1.dp, borderColor, RoundedCornerShape(16.dp)) else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = title, fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = valueColor)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = subtitle, fontSize = 12.sp, color = if (borderColor != null) Color(0xFFE74C3C) else Color.Gray)
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(iconBg, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
            }
        }
    }
}