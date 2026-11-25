package com.example.dacs_3_composer.feature.restaurant.home.components


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RevenueCard(
    revenue: String,
    modifier: Modifier = Modifier
) {
    RevenueCardContent(revenue = revenue, modifier = modifier, onClick = null)
}

@Composable
fun InteractiveRevenueCard(
    revenue: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    RevenueCardContent(revenue = revenue, modifier = modifier, onClick = onClick)
}

@Composable
private fun RevenueCardContent(
    revenue: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2159BC),
            disabledContainerColor = Color(0xFF2159BC)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "DOANH THU",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$revenue VNĐ",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
fun StatMiniCard(
    icon: @Composable () -> Unit,
    title: String,
    value: String,
    growth: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Icon hình tròn nền nhạt
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0xFFF1F3F4), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }

            Text(
                text = value,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF191C1D)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowDropUp,
                    contentDescription = null,
                    tint = Color(0xFF2ECC71),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = growth,
                    color = Color(0xFF2ECC71),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = title,
                    color = Color(0xFF727785),
                    fontSize = 11.sp
                )
            }
        }
    }
}