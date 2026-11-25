package com.example.dacs_3_composer.feature.customer.home.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalDensity
import coil.compose.AsyncImage
import com.example.dacs_3_composer.core.model.RestaurantDetail
import com.example.dacs_3_composer.core.image.cloudinaryThumbnail
import com.example.dacs_3_composer.feature.shared.location.DeliveryEstimate
import java.util.Locale

@Composable
fun RestaurantItem(
    restaurant: RestaurantDetail,
    modifier: Modifier = Modifier
) {
    val ratingText = if (restaurant.rating > 0.0) {
        String.format(Locale.US, "%.1f", restaurant.rating)
    } else {
        "Mới"
    }
    val timeText = restaurant.deliveryTime.let { raw ->
        if (raw.contains("phút") || raw.contains("min")) raw else "${raw} phút"
    }
    val distanceText = restaurant.distance.ifBlank { "Chưa rõ vị trí" }
    val feeText = DeliveryEstimate.formatFee(restaurant.shippingFee)
    val imageWidthPx = with(LocalDensity.current) { 360.dp.roundToPx() }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column {
            AsyncImage(
                model = cloudinaryThumbnail(restaurant.coverImage, imageWidthPx),
                contentDescription = restaurant.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(145.dp)
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)),
                contentScale = ContentScale.Crop
            )

            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = restaurant.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A1A1A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (restaurant.description.isNotBlank()) {
                    Text(
                        text = restaurant.description,
                        fontSize = 13.sp,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Hàng hiển thị Đánh giá sao, Thời gian dự tính, Khoảng cách thực tế và Phí giao hàng dự kiến
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Đánh giá",
                            tint = Color(0xFFFFB200),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = " $ratingText",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF191C1D)
                        )
                    }

                    Text(
                        text = "⏱️ $timeText",
                        fontSize = 12.sp,
                        color = Color(0xFF42474E),
                        maxLines = 1
                    )

                    Text(
                        text = "📍 $distanceText",
                        fontSize = 12.sp,
                        color = Color(0xFF42474E),
                        maxLines = 1
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    if (restaurant.distanceKm != null) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFEAF2FA), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "Ship $feeText",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF2159BC),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
