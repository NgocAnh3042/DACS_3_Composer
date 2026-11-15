package com.example.dacs_3_composer.feature.admin.overview.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
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
fun OrderItemCard(
    storeName: String,
    distanceInfo: String,
    price: String,
    deliveryAddress: String,
    routeDistance: String,
    status: String = "PENDING" // PENDING: Chờ duyệt, PREPARING: Đang chuẩn bị
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Ảnh đại diện nhà hàng/món ăn từ Cloudinary hoặc drawable mặc định
                AsyncImage(
                    model = "https://res.cloudinary.com/dhscw17vq/image/upload/v1710000000/sample.jpg",
                    placeholder = painterResource(id = R.drawable.banner1),
                    error = painterResource(id = R.drawable.banner1),
                    contentDescription = null,
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = storeName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF191C1D)
                    )
                    Text(
                        text = "📍 $distanceInfo",
                        fontSize = 12.sp,
                        color = Color(0xFF727785)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = price,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF2159BC)
                    )
                    Text(
                        text = "Phí giao hàng",
                        fontSize = 11.sp,
                        color = Color(0xFF727785)
                    )
                }
            }

            if (status == "PREPARING") {
                Box(
                    modifier = Modifier
                        .background(Color(0xFFFFEAEA), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "ĐANG CHUẨN BỊ (5P NỮA)",
                        fontSize = 10.sp,
                        color = Color.Red,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            HorizontalDivider(color = Color(0xFFF1F3F4))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "GIAO ĐẾN",
                        fontSize = 11.sp,
                        color = Color(0xFF727785),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = deliveryAddress,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF191C1D),
                        maxLines = 1
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "QUÃNG ĐƯỜNG",
                        fontSize = 11.sp,
                        color = Color(0xFF727785),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = routeDistance,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF191C1D)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            if (status == "PENDING") {
                Button(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2159BC)),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(text = "Chấp nhận đơn hàng ", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            } else {
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = Color(0xFFF1F3F4),
                        disabledContentColor = Color(0xFF727785)
                    ),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Text(text = "Chờ nhà hàng xác nhận", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}