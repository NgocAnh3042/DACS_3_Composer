package com.example.dacs_3_composer.feature.shipper.dashboard.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailTopBar(onBackClick: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                text = "Chi tiết đơn hàng",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1D4ED8)
            )
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color(0xFF1D4ED8)
                )
            }
        },
        actions = {
            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Default.NotificationsNone,
                    contentDescription = "Notifications",
                    tint = Color(0xFF1D4ED8)
                )
            }
            Box(
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF3B82F6))
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
    )
}