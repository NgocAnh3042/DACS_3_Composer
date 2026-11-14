package com.example.dacs_3_composer.feature.customer.order.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EmptyOrderState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Icon tờ đơn hàng vẽ bằng màu cam nay đổi sang màu xanh
        Icon(
            imageVector = Icons.Default.EditNote,
            contentDescription = null,
            tint = Color(0xFF2159BC), // Đổi sang màu xanh đặc trưng
            modifier = Modifier.size(100.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Quên chưa đặt món rồi nè bạn ơi?",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF191C1D)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Bạn sẽ nhìn thấy các món đang được chuẩn bị hoặc giao đi tại đây để kiểm tra đơn hàng nhanh hơn!",
            fontSize = 14.sp,
            color = Color(0xFF727785),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}