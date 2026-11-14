package com.example.dacs_3_composer.feature.shipper.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.example.dacs_3_composer.core.model.Order
import java.util.Locale

@Composable
fun DashboardAvailableOrderCard(
    order: Order,
    onCardClick: () -> Unit,
    onAcceptClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCardClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "#ĐƠN: ${order.id.takeLast(6).uppercase(Locale.getDefault())}", fontWeight = FontWeight.Bold, color = Color(0xFF2159BC))

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // ✅ HIỂN THỊ PHƯƠNG THỨC THANH TOÁN ĐỂ SHIPPER LỰA CHỌN
                    val methodLabel = if (order.paymentMethod == "ONLINE") "ONLINE" else "COD"
                    val methodColor = if (order.paymentMethod == "ONLINE") Color(0xFF2ECC71) else Color(0xFFEF4444)
                    
                    Surface(
                        color = methodColor.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = methodLabel,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = methodColor
                        )
                    }

                    Text(
                        text = "${String.format(Locale.getDefault(), "%,.0f", order.shippingFee)}đ",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2159BC),
                        fontSize = 16.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "📍 Nhà hàng: ${order.restaurantName}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(text = "🏁 Giao đến: ${order.customerAddress}", fontSize = 13.sp, color = Color.Gray, maxLines = 1)
            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onAcceptClick,
                modifier = Modifier.fillMaxWidth().height(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2159BC)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(text = "Nhận đơn hàng này", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
