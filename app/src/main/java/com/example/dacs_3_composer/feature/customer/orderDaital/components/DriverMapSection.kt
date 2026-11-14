package com.example.dacs_3_composer.feature.customer.orderDaital.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Phone
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
fun DriverMapSection(
    driverName: String,
    licensePlate: String,
    rating: String,
    driverAvatarRes: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
    ) {
        // Ảnh giả lập bản đồ nền (Bạn chuẩn bị ảnh map trong drawable nhé)
        Image(
            painter = painterResource(id = R.drawable.banner1), // Thay bằng ảnh map của bạn
            contentDescription = "Bản đồ",
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)),
            contentScale = ContentScale.Crop
        )

        // Card thông tin tài xế đè lên bản đồ ở phía dưới
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar tài xế kèm chấm xanh lá online
                Box(contentAlignment = Alignment.BottomEnd) {
                    Image(
                        painter = painterResource(id = driverAvatarRes),
                        contentDescription = "Tài xế",
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(Color(0xFF2ECC71), CircleShape)
                            .padding(2.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Tên & biển số xe tài xế
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = driverName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF191C1D)
                    )
                    Text(
                        text = licensePlate,
                        fontSize = 12.sp,
                        color = Color(0xFF727785)
                    )
                    Text(
                        text = "⭐ $rating",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2159BC)
                    )
                }

                // Cặp nút Nhắn tin và Gọi điện màu xanh dương
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { /* Nhắn tin */ },
                        modifier = Modifier.background(Color(0xFFE8F0FE), CircleShape).size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = "Nhắn tin",
                            tint = Color(0xFF2159BC),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { /* Gọi điện */ },
                        modifier = Modifier.background(Color(0xFF2159BC), CircleShape).size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = "Gọi điện",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}