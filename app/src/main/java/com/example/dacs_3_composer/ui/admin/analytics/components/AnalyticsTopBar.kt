package com.example.dacs_3_composer.ui.admin.analytics.components

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight

@Composable
fun AnalyticsTopBar(
    onChatClick: () -> Unit = {}, // 🎯 THÊM: Callback nhắn tin
    onNotificationClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Báo cáo & Thống kê", 
                fontSize = 24.sp, 
                fontWeight = FontWeight.Bold, 
                color = Color(0xFF191C1D),
                modifier = Modifier.weight(1f)
            )

            // 🎯 THÊM: Icon Chat phong cách Admin
            IconButton(onClick = onChatClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = "Tin nhắn",
                    tint = Color(0xFF2159BC)
                )
            }

            IconButton(onClick = onNotificationClick) {
                BadgedBox(badge = { Badge(containerColor = Color.Red, modifier = Modifier.size(8.dp)) }) {
                    Icon(
                        imageVector = Icons.Default.NotificationsNone,
                        contentDescription = "Thông báo",
                        tint = Color(0xFF2159BC),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Dữ liệu kinh doanh thời gian thực",
            fontSize = 13.sp,
            color = Color(0xFF727785)
        )
    }
}
