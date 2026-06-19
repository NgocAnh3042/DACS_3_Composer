package com.example.dacs_3_composer.ui.shipper.orders.components

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.OutlinedFlag
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dacs_3_composer.data.model.Order
import java.util.Locale

@SuppressLint("NonObservableLocale")
@Composable
fun ActiveDeliveryCard(
    order: Order,
    onDetailClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Hàng 1: Mã đơn & Badge trạng thái màu xanh dương
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "#ORD-${order.id.takeLast(4).uppercase(Locale.getDefault())}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2563EB)
                )
                
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // ✅ Hiển thị nhãn thanh toán
                    val methodLabel = if (order.paymentMethod == "ONLINE") "ĐÃ TRẢ" else "COD"
                    val methodColor = if (order.paymentMethod == "ONLINE") Color(0xFF2ECC71) else Color(0xFFEF4444)
                    
                    Surface(
                        color = methodColor.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = methodLabel,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = methodColor
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF2563EB))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Đang giao",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Hàng 2: Tên quán & Giá tiền cần thu/tiền ship
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = order.restaurantName.ifBlank { "Nhà hàng đối tác" },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1F2937),
                    modifier = Modifier.weight(1f)
                )
                
                Column(horizontalAlignment = Alignment.End) {
                    val priceLabel = if (order.paymentMethod == "ONLINE") "Đã thanh toán" else "Cần thu hộ"
                    Text(text = priceLabel, fontSize = 10.sp, color = Color.Gray)
                    Text(
                        text = "${String.format(Locale.getDefault(), "%,.0f", order.totalPrice)}đ",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (order.paymentMethod == "ONLINE") Color(0xFF2ECC71) else Color(0xFF1E3A8A)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Điểm nhận hàng (Giữ nguyên logic cũ hoặc lấy từ lat/lng nếu có địa chỉ quán)
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.LocationOn, null, tint = Color(0xFF3B82F6), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(text = "Điểm nhận", fontSize = 11.sp, color = Color.Gray)
                    Text(text = "Nhận tại: ${order.restaurantName}", fontSize = 13.sp, color = Color(0xFF374151))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Điểm giao hàng
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.OutlinedFlag, null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(text = "Điểm giao", fontSize = 11.sp, color = Color.Gray)
                    Text(text = order.customerAddress.ifBlank { "Địa chỉ khách hàng" }, fontSize = 13.sp, color = Color(0xFF374151))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color(0xFFF3F4F6))
            Spacer(modifier = Modifier.height(12.dp))

            // Hàng đáy: Nút bấm xem chi tiết
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Thu nhập từ đơn này", fontSize = 11.sp, color = Color.Gray)
                    Text(
                        text = "+${String.format(Locale.getDefault(), "%,.0f", order.shippingFee)}đ",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2563EB)
                    )
                }

                Button(
                    onClick = onDetailClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    shape = RoundedCornerShape(99.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    Text(text = "Xem chi tiết", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
