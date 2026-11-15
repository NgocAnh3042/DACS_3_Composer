package com.example.dacs_3_composer.feature.shipper.profile.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DriverStatsCard(
    totalOrders: Int,
    successRate: Int,
    joinDate: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Ô Lớn: Tổng đơn hàng
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = "TỔNG ĐƠN HÀNG", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6B7280))
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = String.format("%,d", totalOrders), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1D4ED8))
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFEFF6FF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.LocalShipping, null, tint = Color(0xFF2563EB), modifier = Modifier.size(22.dp))
            }
        }

        // 2. Hàng ngang chứa: Tỉ lệ thành công & Ngày tham gia
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Khối Tỉ lệ thành công
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "TỈ LỆ THÀNH CÔNG", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6B7280))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "$successRate%", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1D4ED8))
                }
                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF10B981), modifier = Modifier.size(24.dp))
            }

            // Khối Ngày tham gia
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "NGÀY THAM GIA", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6B7280))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = joinDate, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1F2937))
                }
                Icon(Icons.Default.CalendarMonth, null, tint = Color(0xFF9CA3AF), modifier = Modifier.size(24.dp))
            }
        }
    }
}