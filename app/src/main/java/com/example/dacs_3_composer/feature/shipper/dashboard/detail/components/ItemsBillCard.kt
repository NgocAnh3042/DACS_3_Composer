package com.example.dacs_3_composer.feature.shipper.dashboard.detail.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dacs_3_composer.core.model.OrderItem
import java.util.Locale

@Composable
fun ItemsBillCard(
    orderId: String,
    items: List<OrderItem>,
    totalPrice: Double,
    shippingFee: Double,
    paymentMethod: String,
    modifier: Modifier = Modifier
) {
    val localeVN = remember { Locale("vi", "VN") }
    val totalDishPrice = remember(totalPrice, shippingFee) { (totalPrice - shippingFee).coerceAtLeast(0.0) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Chi tiết đơn hàng",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827)
                )
                
                val methodLabel = if (paymentMethod == "ONLINE") "ĐÃ THANH TOÁN" else "THU TIỀN MẶT"
                val methodColor = if (paymentMethod == "ONLINE") Color(0xFF2ECC71) else Color(0xFFEF4444)
                
                Surface(
                    color = methodColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = methodLabel,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = methodColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            items.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = item.name, fontSize = 14.sp, color = Color(0xFF374151), fontWeight = FontWeight.Medium)
                        Text(text = "Số lượng: ${item.quantity}", fontSize = 12.sp, color = Color(0xFF6B7280))
                    }
                    Text(
                        text = "${String.format(localeVN, "%,.0f", item.price * item.quantity)}đ",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF111827)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color(0xFFF3F4F6))
            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Tổng tiền món", fontSize = 13.sp, color = Color(0xFF6B7280))
                Text(text = "${String.format(localeVN, "%,.0f", totalDishPrice)}đ", fontSize = 13.sp, color = Color(0xFF374151))
            }

            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Phí vận chuyển", fontSize = 13.sp, color = Color(0xFF6B7280))
                Text(text = "${String.format(localeVN, "%,.0f", shippingFee)}đ", fontSize = 13.sp, color = Color(0xFF2563EB), fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = Color(0xFFE5E7EB))
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val bottomLabel = if (paymentMethod == "ONLINE") "Đã trả trực tuyến" else "Tổng cộng cần thu"
                Text(text = bottomLabel, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                Text(
                    text = "${String.format(localeVN, "%,.0f", totalPrice)}đ",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (paymentMethod == "ONLINE") Color(0xFF2ECC71) else Color(0xFFEF4444)
                )
            }
        }
    }
}
