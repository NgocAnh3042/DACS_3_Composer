package com.example.dacs_3_composer.feature.shipper.dashboard.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CustomerNoteBox(noteText: String) { // 🌟 Đã sửa: Nhận đúng biến noteText
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFDF2F2), RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = Color(0xFFEF4444),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(text = "GHI CHÚ TỪ KHÁCH HÀNG", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = noteText, // 🌟 Hiển thị nội dung chuỗi động truyền xuống từ Firestore
                fontSize = 12.sp,
                color = Color(0xFF4B5563),
                lineHeight = 16.sp
            )
        }
    }
}