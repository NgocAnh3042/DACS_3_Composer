package com.example.dacs_3_composer.feature.customer.orderDaital.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TimelineNode(
    icon: ImageVector,
    title: String,
    description: String,
    time: String?,
    isCompleted: Boolean,
    isLast: Boolean,
    extraContent: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val tintColor = if (isCompleted) Color(0xFF2159BC) else Color(0xFFC4C7C5)
    val bgColor = if (isCompleted) Color(0xFFE8F0FE) else Color(0xFFF1F3F4)

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // Cột hiển thị Icon và Đường nối tuyến tính dọc (Timeline line)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(48.dp)
        ) {
            // Khung tròn chứa Icon
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(bgColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tintColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Đường gạch nối dọc xuống dưới (Trừ Node cuối cùng)
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(55.dp) // Chiều cao đường nối dọc
                        .background(if (isCompleted) Color(0xFF2159BC) else Color(0xFFE7E8E9))
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Cột hiển thị chữ trạng thái kế bên
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 4.dp)
        ) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (isCompleted) Color(0xFF2159BC) else Color(0xFF727785)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 13.sp,
                color = Color(0xFF727785),
                lineHeight = 18.sp
            )

            // Nếu có nhãn nội dung phụ (ví dụ: "Dự kiến: 10 phút nữa")
            extraContent?.let {
                Spacer(modifier = Modifier.height(6.dp))
                it()
            }

            time?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = it,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2159BC)
                )
            }
        }
    }
}