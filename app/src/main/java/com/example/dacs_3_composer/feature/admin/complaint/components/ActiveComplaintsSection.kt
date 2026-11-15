package com.example.dacs_3_composer.feature.admin.complaint.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentLate
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ActiveComplaintsSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEAEA)) // Màu nền đỏ hồng đặc trưng
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Tiêu đề khối
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AssignmentLate, contentDescription = null, tint = Color(0xFFA94442))
                Text(text = "Xử lý khiếu nại", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA94442))
            }

            Text(
                text = "Có 3 khiếu nại mới yêu cầu Super Admin can thiệp giải quyết giữa khách hàng và nhà hàng.",
                fontSize = 13.sp,
                color = Color(0xFFA94442)
            )

            // Thẻ khiếu nại 1 - Mức độ cao
            ComplaintItemMini(
                id = "#CMP-990 - Sakura Sushi",
                desc = "Món ăn không giống mô tả...",
                levelText = "MỨC ĐỘ CAO",
                levelColor = Color(0xFFE74C3C),
                time = "2p trước",
                isWarning = true
            )

            // Thẻ khiếu nại 2 - Trung bình
            ComplaintItemMini(
                id = "#CMP-988 - The Steakhouse",
                desc = "Giao hàng chậm trễ > 60p...",
                levelText = "TRUNG BÌNH",
                levelColor = Color(0xFF2159BC),
                time = "1h trước",
                isWarning = false
            )

            // Nút mở rộng hành động toàn màn hình
            Button(
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF962D2D)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(text = "Mở Trung tâm Khiếu nại", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun ComplaintItemMini(
    id: String,
    desc: String,
    levelText: String,
    levelColor: Color,
    time: String,
    isWarning: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(if (isWarning) Color(0xFFFFEBEE) else Color(0xFFE8F0FE), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isWarning) Icons.Default.Warning else Icons.Default.ChatBubbleOutline,
                    contentDescription = null,
                    tint = levelColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = id, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(text = desc, fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = levelText, fontSize = 10.sp, color = levelColor, fontWeight = FontWeight.Bold)
                    Text(text = time, fontSize = 10.sp, color = Color.Gray)
                }
            }
        }
    }
}