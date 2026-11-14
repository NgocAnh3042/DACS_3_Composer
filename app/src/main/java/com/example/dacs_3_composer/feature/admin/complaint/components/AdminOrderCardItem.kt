package com.example.dacs_3_composer.feature.admin.complaint.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dacs_3_composer.R

@Composable
fun AdminOrderCardItem(
    orderId: String,
    restaurantName: String,
    details: String,
    time: String,
    status: String // "PENDING", "SHIPPING", "DELIVERED"
) {
    val (statusText, statusBg, statusColor) = when (status) {
        "PENDING" -> Triple("CHỜ XÁC NHẬN", Color(0xFFFFF3E0), Color(0xFFE67E22))
        "SHIPPING" -> Triple("ĐANG GIAO", Color(0xFFE8F0FE), Color(0xFF2159BC))
        else -> Triple("ĐÃ GIAO", Color(0xFFE8F8F0), Color(0xFF2ECC71))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // Ảnh món ăn thu nhỏ
                Box(modifier = Modifier.size(45.dp).clip(RoundedCornerShape(8.dp)).background(Color.LightGray))

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = orderId, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Box(modifier = Modifier.background(statusBg, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text(text = statusText, fontSize = 9.sp, color = statusColor, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(text = restaurantName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.Black)
                    Text(text = details, fontSize = 12.sp, color = Color.Gray)
                }
            }

            HorizontalDivider(color = Color(0xFFF1F3F4))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = time, fontSize = 12.sp, color = Color.Gray)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Các nút chức năng tùy biến theo hình mẫu mẫu
                    IconButton(onClick = {}, modifier = Modifier.background(Color(0xFFE8F0FE), RoundedCornerShape(8.dp)).size(36.dp)) {
                        Icon(Icons.Default.RemoveRedEye, contentDescription = null, tint = Color(0xFF2159BC), modifier = Modifier.size(18.dp))
                    }
                    if (status == "PENDING") {
                        IconButton(onClick = {}, modifier = Modifier.background(Color(0xFF2159BC), RoundedCornerShape(8.dp)).size(36.dp)) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    } else if (status == "DELIVERED") {
                        IconButton(onClick = {}, modifier = Modifier.background(Color(0xFFF1F3F4), RoundedCornerShape(8.dp)).size(36.dp)) {
                            Icon(Icons.Default.Print, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}