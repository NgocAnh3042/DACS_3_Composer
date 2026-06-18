package com.example.dacs_3_composer.ui.shipper.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dacs_3_composer.data.model.Order

@Composable
fun ActiveDeliveryDetailCard(
    order: Order,
    onUpdateStatusClick: (String) -> Unit, // ✅ Nhận lambda truyền String trạng thái mục tiêu lên Firebase
    onCardClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "#MÃ: ${order.id.takeLast(6).uppercase()}", fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))

                // Badge hiển thị tương ứng trạng thái thực tế
                val statusText = if (order.status == "ACCEPTED") "ĐANG ĐẾN QUÁN" else "ĐANG GIAO HÀNG"
                val badgeColor = if (order.status == "ACCEPTED") Color(0xFFEFF6FF) else Color(0xFFFEE2E2)
                val textColor = if (order.status == "ACCEPTED") Color(0xFF2563EB) else Color(0xFFEF4444)

                Box(modifier = Modifier.background(badgeColor, RoundedCornerShape(4.dp)).padding(6.dp)) {
                    Text(text = statusText, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textColor)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "🏢 Cửa hàng: ${order.restaurantName}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(text = "👤 Khách hàng: ${order.customerName} (${order.customerPhone})", fontSize = 13.sp)
            Text(text = "📍 Địa chỉ nhận: ${order.customerAddress}", fontSize = 13.sp, color = Color.Gray)

            // 🌟 CHÈN BẢN ĐỒ VÀO ĐÂY: Hiển thị ngay bên trong thông tin đơn hàng
            Spacer(modifier = Modifier.height(12.dp))
            ShipperMapView(
                order = order,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp) // Chiều cao vừa vặn cho UI của thẻ
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Tách biệt nút bấm phân tầng dựa vào trạng thái thực tế của đơn hàng
            if (order.status == "ACCEPTED") {
                // Bước 1: Tài xế đã nhận đơn, đang đi đến quán lấy món
                Button(
                    onClick = { onUpdateStatusClick("SHIPPING") }, // Kích hoạt nấc Đang giao hàng
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)), // Màu xanh dương lấy hàng
                    shape = RoundedCornerShape(23.dp)
                ) {
                    Text(text = "Xác nhận đã lấy hàng tại quán", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            } else {
                // Bước 2: Tài xế đã lấy đồ ăn xong và đang trên đường đi ship cho khách
                Button(
                    onClick = { onUpdateStatusClick("COMPLETED") }, // Kích hoạt nấc Giao xong
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2ECC71)), // Màu xanh lá giao xong
                    shape = RoundedCornerShape(23.dp)
                ) {
                    Text(text = "Xác nhận đã giao xong cho khách", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}
