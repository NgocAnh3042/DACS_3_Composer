package com.example.dacs_3_composer.feature.admin.overview.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StatsMiniCardRow(
    completedCount: String = "18",
    shippingCount: String = "2"
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatMiniCard(
            icon = Icons.Outlined.CheckCircleOutline,
            count = completedCount,
            label = "HOÀN THÀNH",
            iconColor = Color(0xFF2159BC),
            modifier = Modifier.weight(1f)
        )
        StatMiniCard(
            icon = Icons.Outlined.DirectionsCar,
            count = shippingCount,
            label = "ĐANG GIAO",
            iconColor = Color(0xFF4FAAFF),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatMiniCard(
    icon: ImageVector,
    count: String,
    label: String,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = count,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF191C1D)
            )
            Text(
                text = label,
                fontSize = 11.sp,
                color = Color(0xFF727785),
                fontWeight = FontWeight.Bold
            )
        }
    }
}