package com.example.dacs_3_composer.feature.customer.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
// 🌟 Nhớ import class dữ liệu thật vào đây:
import com.example.dacs_3_composer.core.model.RestaurantDetail

@Composable
fun RestaurantItem(
    restaurant: RestaurantDetail, // 🌟 SỬA TẠI ĐÂY: Đổi từ Restaurant sang RestaurantDetail
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            // 1. Ảnh bìa nhà hàng lấy từ link ảnh thật trên Firebase (Dùng AsyncImage của Coil)
            AsyncImage(
                model = restaurant.coverImage, // Đổi từ imageRes sang coverImage
                contentDescription = restaurant.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                contentScale = ContentScale.Crop
            )

            // 2. Phần thông tin chữ bên dưới ảnh
            Column(modifier = Modifier.padding(12.dp)) {
                // Tên quán ăn
                Text(
                    text = restaurant.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A1A1A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Mô tả quán ăn
                Text(
                    text = restaurant.description,
                    fontSize = 13.sp,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                // Hàng hiển thị Đánh giá, Thời gian ship và Khoảng cách
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    // Điểm đánh giá sao
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Rating",
                            tint = Color(0xFFFFB200),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = " ${restaurant.rating}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Thời gian giao hàng (Ví dụ: "25-30 phút")
                    Text(
                        text = "⏱️ ${restaurant.deliveryTime}", // Sửa lại đúng tên trường trong RestaurantDetail của bạn
                        fontSize = 13.sp,
                        color = Color.DarkGray
                    )

                    // Khoảng cách (Ví dụ: "1.2 km")
                    Text(
                        text = "📍 ${restaurant.distance}",
                        fontSize = 13.sp,
                        color = Color.DarkGray
                    )
                }
            }
        }
    }
}