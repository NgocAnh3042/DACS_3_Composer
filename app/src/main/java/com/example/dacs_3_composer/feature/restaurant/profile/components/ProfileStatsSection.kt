package com.example.dacs_3_composer.feature.restaurant.profile.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun ProfileStatsSection(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        RestauranStatCard(
            icon = Icons.Default.Wallet,
            iconColor = Color(0xFF2159BC),
            title = "Doanh thu tháng",
            value = "45.2M",
            modifier = Modifier.weight(1f)
        )
        RestauranStatCard(
            icon = Icons.Default.Star,
            iconColor = Color(0xFFE28743),
            title = "Đánh giá",
            value = "4.8/5.0",
            modifier = Modifier.weight(1f)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ProfileStatsSectionPreview() {
    ProfileStatsSection()
}