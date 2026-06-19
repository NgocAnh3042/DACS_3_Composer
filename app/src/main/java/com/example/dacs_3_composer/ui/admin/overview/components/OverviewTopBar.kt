package com.example.dacs_3_composer.ui.admin.overview.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewTopBar(
    adminName: String = "Gourmet Admin",
    avatarUrl: String = "",
    onNotificationClick: () -> Unit = {},
    onChatClick: () -> Unit = {}, // 🎯 THÊM: Callback nhắn tin
    onAvatarClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(avatarUrl.ifEmpty { R.drawable.banner1 })
                .crossfade(true)
                .build(),
            contentDescription = "Admin Avatar",
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable { onAvatarClick() },
            contentScale = ContentScale.Crop,
            placeholder = painterResource(id = R.drawable.banner1),
            error = painterResource(id = R.drawable.banner1)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable { onAvatarClick() }
        ) {
            Text(
                text = "Chào buổi sáng,",
                fontSize = 12.sp,
                color = Color(0xFF727785)
            )
            Text(
                text = adminName.ifEmpty { "Gourmet Admin" },
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2159BC)
            )
        }

        // 🎯 THÊM: Icon Chat cho Admin
        IconButton(onClick = onChatClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Chat,
                contentDescription = "Tin nhắn",
                tint = Color(0xFF2159BC),
                modifier = Modifier.size(24.dp)
            )
        }

        IconButton(onClick = onNotificationClick) {
            BadgedBox(
                badge = {
                    Badge(
                        containerColor = Color.Red,
                        modifier = Modifier.size(8.dp)
                    )
                }
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsNone,
                    contentDescription = "Thông báo",
                    tint = Color(0xFF2159BC),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
