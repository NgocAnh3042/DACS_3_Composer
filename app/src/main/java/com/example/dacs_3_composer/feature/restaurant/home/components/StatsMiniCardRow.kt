package com.example.dacs_3_composer.feature.restaurant.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.dacs_3_composer.feature.restaurant.home.components.StatMiniCard

@Composable
fun StatsMiniCardRow(
    totalOrders: String, // 🌟 Nhận tham số động
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatMiniCard(
            icon = { Icon(Icons.Default.ShoppingBag, null, tint = Color(0xFF2159BC), modifier = Modifier.size(18.dp)) },
            title = "Tổng đơn hoàn thành",
            value = totalOrders,
            growth = "+100%",
            modifier = Modifier.weight(1f)
        )
    }
}