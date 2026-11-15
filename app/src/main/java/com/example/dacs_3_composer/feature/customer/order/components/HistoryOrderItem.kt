package com.example.dacs_3_composer.feature.customer.order.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
fun HistoryOrderItem(
    restaurantName: String,
    date: String,
    price: String,
    itemsSummary: String,
    restaurantImageUrl: String,
    modifier: Modifier = Modifier, // ✅ Đã đưa lên vị trí tham số tùy chọn đầu tiên
    paymentMethod: String = "CASH",
    onReorderClick: () -> Unit,
    onDetailClick: () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                        
                        val methodLabel = if (paymentMethod == "ONLINE") "ONLINE" else "CASH"
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
                    Text(text = date, fontSize = 12.sp, color = Color(0xFF727785))
                    Text(
                        text = itemsSummary,
                        fontSize = 13.sp,
                        color = Color(0xFF191C1D),
                        modifier = Modifier.padding(top = 4.dp),
                        maxLines = 1
                    )
                }
                Text(
                    text = price,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF2159BC),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDetailClick,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF727785))
                ) {
                    Text(text = "Chi tiết", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onReorderClick,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2159BC))
                ) {
                    Text(text = "Mua lại", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}
