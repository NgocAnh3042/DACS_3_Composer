package com.example.dacs_3_composer.feature.restaurant.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
fun TopSellingDishItem(
    rank: Int,
    dishName: String,
    ordersCount: Int,
    imageUrl: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = imageUrl.ifBlank { R.drawable.images1 }, // Sử dụng ảnh dự phòng nếu chuỗi link trống
                placeholder = painterResource(id = R.drawable.images1),
                error = painterResource(id = R.drawable.images1),
                contentDescription = dishName,
                modifier = Modifier.size(54.dp).clip(CircleShape),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = dishName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF191C1D))
                Text(text = "$ordersCount lượt đặt thành công", fontSize = 12.sp, color = Color(0xFF727785))
            }

            Box(
                modifier = Modifier.background(Color(0xFFFDF2E9), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(text = "#$rank", color = Color(0xFFE28743), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}