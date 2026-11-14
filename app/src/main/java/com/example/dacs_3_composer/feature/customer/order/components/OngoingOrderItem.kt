package com.example.dacs_3_composer.feature.customer.order.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.dacs_3_composer.R

@Composable
fun OngoingOrderItem(
    restaurantName: String,
    statusText: String,
    estimatedTime: String,
    itemsSummary: String,
    restaurantImageUrl: String,
    modifier: Modifier = Modifier, // ✅ Đã đưa lên đầu các tham số tùy chọn
    paymentMethod: String = "CASH",
    chuyenTheoDoiDonHang: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { chuyenTheoDoiDonHang() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                AsyncImage(
                    model = restaurantImageUrl.ifBlank { R.drawable.banner1 },
                    placeholder = painterResource(id = R.drawable.banner1),
                    error = painterResource(id = R.drawable.banner1),
                    contentDescription = null,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = restaurantName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFF191C1D),
                            modifier = Modifier.weight(1f)
                        )
                        
                        val methodLabel = if (paymentMethod == "ONLINE") "ONLINE" else "TIỀN MẶT"
                        val methodColor = if (paymentMethod == "ONLINE") Color(0xFF2ECC71) else Color(0xFF727785)
                        
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
                    }
                    
                    Text(
                        text = statusText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (statusText.contains("thanh toán")) Color(0xFFE67E22) else Color(0xFF2159BC)
                    )
                    Text(
                        text = itemsSummary,
                        fontSize = 13.sp,
                        color = Color(0xFF727785),
                        modifier = Modifier.padding(top = 2.dp),
                        maxLines = 1
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color(0xFFE7E8E9), thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFFE8F0FE), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(text = estimatedTime, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2159BC))
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Xem chi tiết",
                        fontSize = 13.sp,
                        color = Color(0xFF2159BC),
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color(0xFF2159BC),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
