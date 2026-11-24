package com.example.dacs_3_composer.feature.customer.profileRestaurant.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dacs_3_composer.core.model.RestaurantDetail
import com.example.dacs_3_composer.feature.shared.location.DeliveryEstimate
import java.util.Locale

@Composable
fun RestaurantInfoUserCard(restaurant: RestaurantDetail) {
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
            Text(
                text = restaurant.name,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1A1A)
            )

            if (restaurant.description.isNotBlank()) {
                Text(
                    text = restaurant.description,
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Icon(
                    painter = painterResource(id = com.example.dacs_3_composer.R.drawable.ic_location),
                    contentDescription = null,
                    tint = Color(0xFF2159BC),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = restaurant.address.ifBlank { "Đang cập nhật địa chỉ" },
                    fontSize = 13.sp,
                    color = Color.Gray,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            HorizontalDivider(color = Color(0xFFEEEEEE), modifier = Modifier.padding(vertical = 10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
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

                Text(text = "⏱️ $timeText", fontSize = 12.sp, color = Color(0xFF42474E))
                Text(text = "📍 $distanceText", fontSize = 12.sp, color = Color(0xFF42474E))

                Spacer(modifier = Modifier.weight(1f))

                Box(
                    modifier = Modifier
                        .background(Color(0xFFEAF2FA), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "Ship $feeText",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF2159BC)
                    )
                }
            }
        }
    }
}