package com.example.dacs_3_composer.feature.customer.orderDaital.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OrderSummarySection(
    items: List<Pair<String, Int>>, // Danh sách món và số lượng
    totalPrice: String,
    onDetailClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF3B82F6)) // Đổi sang màu xanh đặc trưng của bạn
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Tóm tắt đơn hàng",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            // Hiển thị danh sách món ăn đã đặt
            items.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = item.first, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                    Text(text = "x${item.second}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.3f), thickness = 1.dp)

            // Tổng thanh toán
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TỔNG THANH TOÁN",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = totalPrice,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Nút "Xem chi tiết hóa đơn" màu trắng bo góc
            Button(
                onClick = onDetailClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                Text(
                    text = "Xem chi tiết hóa đơn",
                    color = Color(0xFF2159BC), // Chữ màu xanh đậm
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}