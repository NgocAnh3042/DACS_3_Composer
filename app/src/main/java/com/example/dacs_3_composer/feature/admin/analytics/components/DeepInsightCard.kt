package com.example.dacs_3_composer.feature.admin.analytics.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DeepInsightCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Thông tin chuyên sâu", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0052CC))
            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier.size(36.dp).background(Color(0xFFE8EFFF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.TrendingUp, contentDescription = null, tint = Color(0xFF0052CC), modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Gợi ý: Doanh thu có xu hướng tăng mạnh vào cuối tuần từ 18:00 đến 21:00. Bạn nên tăng cường đội ngũ Shipper thêm 15% vào khung giờ này để đảm bảo thời gian giao hàng dưới 30 phút.",
                        fontSize = 13.sp,
                        color = Color.DarkGray,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}