package com.example.dacs_3_composer.ui.shipper.dashboard.components

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.dacs_3_composer.data.model.User

@Composable
fun ShipperDashboardTopBar(
    user: User,
    onNotificationClick: () -> Unit = {},
    onChatClick: () -> Unit = {} // 🎯 THÊM: Sự kiện khi bấm icon Chat
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFAFAFA))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0xFF1D4ED8)),
            contentAlignment = Alignment.Center
        ) {
            if (user.avatarUrl.isNotBlank()) {
                AsyncImage(
                    model = user.avatarUrl,
                    contentDescription = "Shipper Avatar",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                val firstChar = if (user.name.isNotBlank()) user.name.take(1).uppercase() else "S"
                Text(
                    text = firstChar,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Chào buổi sáng,",
                fontSize = 13.sp,
                color = Color(0xFF6B7280)
            )
            Text(
                text = if (user.name.isNotBlank()) user.name else "Tài xế mới",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1D4ED8)
            )
        }

        // 🎯 THÊM: Icon Chat phong cách đồng nhất
        IconButton(onClick = onChatClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Chat,
                contentDescription = "Chat",
                tint = Color(0xFF1D4ED8),
                modifier = Modifier.size(24.dp)
            )
        }

        IconButton(onClick = onNotificationClick) {
            BadgedBox(
                badge = { Badge(containerColor = Color(0xFFEF4444), modifier = Modifier.size(8.dp)) }
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsNone,
                    contentDescription = "Notifications",
                    tint = Color(0xFF1D4ED8),
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}
