package com.example.dacs_3_composer.feature.restaurant.store.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dacs_3_composer.core.model.RestaurantDetail

@Composable
fun RestaurantInfoManageCard(
    restaurant: RestaurantDetail,
    onEditInfoClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = restaurant.name.ifEmpty { "Tên nhà hàng" },
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1A1A)
                    )
                    Text(
                        text = restaurant.description.ifEmpty { "Chưa có mô tả cho nhà hàng này." },
                        fontSize = 14.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 4.dp),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onEditInfoClick,
                    modifier = Modifier
                        .background(Color(0xFFF0F4F8), CircleShape)
                        .size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Sửa thông tin",
                        tint = Color(0xFF1E56A0),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFF1F3F4))

            // Thông tin chi tiết với icon
            DetailRow(icon = Icons.Default.LocationOn, text = restaurant.address.ifEmpty { "Chưa cập nhật địa chỉ" })
            DetailRow(icon = Icons.Default.Email, text = restaurant.email.ifEmpty { "Chưa cập nhật email" })
            DetailRow(icon = Icons.Default.Phone, text = restaurant.phone.ifEmpty { "Chưa cập nhật số điện thoại" })
            
            val timeRange = if (restaurant.openTime.isNotBlank() && restaurant.closeTime.isNotBlank()) {
                "${restaurant.openTime} - ${restaurant.closeTime}"
            } else {
                "Chưa cập nhật"
            }
            DetailRow(icon = Icons.Default.AccessTime, text = "Giờ mở cửa: $timeRange")
        }
    }
}

@Composable
private fun DetailRow(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF2159BC),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            fontSize = 13.sp,
            color = Color(0xFF414754),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
