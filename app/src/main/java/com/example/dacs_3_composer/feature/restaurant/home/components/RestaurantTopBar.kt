package com.example.dacs_3_composer.feature.restaurant.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.dacs_3_composer.R

@Composable
fun RestaurantTopBar(
    name: String,
    avatarUrl: String,
    modifier: Modifier = Modifier,
    onMessageClick: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White) // Chỉnh lại nền trắng giống Admin
            .padding(16.dp)
    ) {
        // Hàng 1: Avatar, Tên và Các icon chức năng
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(avatarUrl.ifEmpty { R.drawable.banner1 })
                    .crossfade(true)
                    .build(),
                contentDescription = "Avatar",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(id = R.drawable.banner1),
                error = painterResource(id = R.drawable.banner1)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = name.ifEmpty { "Gourmet Partner" },
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF191C1D),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onMessageClick) {
                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Tin nhắn", tint = Color(0xFF191C1D))
            }
            IconButton(onClick = {}) {
                BadgedBox(badge = { Badge(containerColor = Color.Red) }) {
                    Icon(Icons.Default.NotificationsNone, contentDescription = "Thông báo", tint = Color(0xFF191C1D))
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Hàng 2: Tiêu đề màn hình
        Text(
            text = "Trang chủ nhà hàng", 
            fontSize = 24.sp, 
            fontWeight = FontWeight.Bold, 
            color = Color(0xFF191C1D)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Hệ thống đang vận hành ổn định", 
            fontSize = 13.sp, 
            color = Color(0xFF727785)
        )
    }
}
