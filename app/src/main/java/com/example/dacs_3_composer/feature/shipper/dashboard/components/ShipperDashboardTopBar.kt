package com.example.dacs_3_composer.feature.shipper.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.dacs_3_composer.core.model.User

@Composable
fun ShipperDashboardTopBar(
    user: User,
    onNotificationClick: () -> Unit = {},
    onChatClick: () -> Unit = {} // 🎯 THÊM: Sự kiện khi bấm icon Chat
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .background(Color.White)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0xFF1D4ED8)),
            contentAlignment = Alignment.Center
        ) {
            if (user.avatarUrl.isNotBlank()) {
                AsyncImage(
                    model = user.avatarUrl,
                    contentDescription = "Ảnh đại diện tài xế",
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
                text = "FoodHub Tài xế",
                fontSize = 13.sp,
                color = Color(0xFF6B7280)
            )
            Text(
                text = user.getDisplayName(),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF172333),
                maxLines = 1
            )
        }

        // 🎯 THÊM: Icon Chat phong cách đồng nhất
        IconButton(onClick = onChatClick, modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFFEAF1FF))) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Chat,
                contentDescription = "Tin nhắn",
                tint = Color(0xFF1D4ED8),
                modifier = Modifier.size(24.dp)
            )
        }

    }
}
