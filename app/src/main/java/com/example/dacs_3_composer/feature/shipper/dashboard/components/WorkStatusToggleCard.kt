package com.example.dacs_3_composer.feature.shipper.dashboard.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WorkStatusToggleCard(
    isReady: Boolean,
    onStatusChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Sẵn sàng nhận đơn",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isReady) "Đang tìm đơn hàng mới" else "Bật để bắt đầu nhận đơn",
                    fontSize = 12.sp,
                    color = Color(0xFF6B7280)
                )
            }

            Switch(
                checked = isReady,
                onCheckedChange = onStatusChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF2563EB),
                    uncheckedThumbColor = Color(0xFF9CA3AF),
                    uncheckedTrackColor = Color(0xFFE5E7EB)
                )
            )
        }
    }
}
