package com.example.dacs_3_composer.feature.customer.profileRestaurant.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.dacs_3_composer.R
import com.example.dacs_3_composer.core.model.DishItem

@Composable
fun DishRowUserItem(
    dish: DishItem,
    onAddToCartClick: () -> Unit // Sự kiện khi User bấm nút dấu cộng
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thông tin món ăn bên trái
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(text = dish.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
                Text(
                    text = dish.description,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Text(
                    text = "${String.format("%,.0f", dish.price)} đ",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E56A0)
                )
            }

            // Khối ảnh món ăn + Nút dấu cộng lồng vào nhau
            Box(
                modifier = Modifier.size(80.dp),
                contentAlignment = Alignment.BottomEnd // Đặt nút cộng ở góc dưới bên phải của ảnh
            ) {
                if (dish.imageUrl.isEmpty()) {
                    Image(
                        painter = painterResource(id = R.drawable.pizza_banner),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    AsyncImage(
                        model = dish.imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                // 🌟 THÊM MỚI: Nút dấu cộng (+) thêm vào giỏ hàng
                Box(
                    modifier = Modifier
                        .padding(4.dp) // Cách lề mép ảnh một chút cho đẹp
                        .size(28.dp)
                        .background(Color(0xFF1E56A0), shape = CircleShape) // Vòng tròn màu xanh
                        .clickable { onAddToCartClick() }, // Kích hoạt sự kiện thêm vào giỏ
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Thêm món vào giỏ",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}