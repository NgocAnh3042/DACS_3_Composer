package com.example.dacs_3_composer.feature.admin.analytics.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DecimalFormat

@Composable
fun RevenueChartCard(revenueDays: List<Double>) { // 🎯 ĐẢM BẢO CÓ THAM SỐ NÀY
    var isWeekSelected by remember { mutableStateOf(true) }
    val formatter = DecimalFormat("#,###đ")
    val totalWeekRevenue = revenueDays.sum()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(text = "Biểu đồ doanh thu tuần này", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    Text(text = "Tổng doanh số: ${formatter.format(totalWeekRevenue)}", fontSize = 13.sp, color = Color(0xFF0052CC), fontWeight = FontWeight.Medium)
                }

                Row(
                    modifier = Modifier
                        .background(Color(0xFFE8EFFF), RoundedCornerShape(20.dp))
                        .padding(2.dp)
                ) {
                    val activeButtonColors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0052CC), contentColor = Color.White)
                    val inactiveButtonColors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color(0xFF0052CC))

                    Button(
                        onClick = { isWeekSelected = true },
                        colors = if (isWeekSelected) activeButtonColors else inactiveButtonColors,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.height(32.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Tuần", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { isWeekSelected = false },
                        colors = if (!isWeekSelected) activeButtonColors else inactiveButtonColors,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.height(32.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Tháng", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            val maxRevenue = revenueDays.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                revenueDays.forEach { amount ->
                    val barHeightFactor = (amount / maxRevenue).toFloat().coerceIn(0.05f, 1f)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight(barHeightFactor)
                                .width(14.dp)
                                .background(
                                    color = if (amount > 0) Color(0xFF0052CC) else Color(0xFFE2E8F0),
                                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                                )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val days = listOf("Th 2", "Th 3", "Th 4", "Th 5", "Th 6", "Th 7", "CN")
                days.forEach { day ->
                    Text(
                        text = day,
                        fontSize = 11.sp,
                        color = Color.Gray,
                        modifier = Modifier.width(32.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}