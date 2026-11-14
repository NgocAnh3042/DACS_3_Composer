package com.example.dacs_3_composer.feature.restaurant.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

//             BIỂU ĐỒ HOẠT ĐỘNG DOANH THU
@Composable
fun WeeklyRevenueCard(
    chartWeights: List<Float>, // 🌟 Nhận mảng tỷ lệ chiều cao [0.0f -> 1.0f]
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Doanh thu hàng tuần", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF191C1D))
                TextButton(onClick = {}) {
                    Text("Chi tiết", color = Color(0xFF2159BC), fontSize = 12.sp)
                }
            }

            // Khung chứa biểu đồ thanh cột
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(Color(0xFFF8F9FA), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                chartWeights.forEach { weight ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp)
                            .fillMaxHeight(fraction = weight.coerceIn(0.05f, 1f)) // Chiều cao cột động dựa trên doanh thu ngày đó
                            .background(
                                color = if (weight > 0.7f) Color(0xFF2159BC) else Color(0xFFADC6FF),
                                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                            )
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN").forEach { day ->
                    Text(day, fontSize = 11.sp, color = Color(0xFF727785))
                }
            }
        }
    }
}