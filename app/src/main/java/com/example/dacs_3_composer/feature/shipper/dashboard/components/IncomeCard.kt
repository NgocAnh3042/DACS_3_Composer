package com.example.dacs_3_composer.feature.shipper.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun IncomeCard(
    todayIncome: String = "0đ",
    growthText: String = "Xem thu nhập theo tuần và tháng",
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Thu nhập hôm nay",
                    fontSize = 14.sp,
                    color = Color(0xFF6B7280),
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = todayIncome,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF2159BC)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "↗ ",
                        fontSize = 12.sp,
                        color = Color(0xFF2563EB),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = growthText,
                        fontSize = 12.sp,
                        color = Color(0xFF2563EB),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Icon ví tiền bên phải nền xanh nhạt
            IconButton(
                onClick = onClick,
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFFEFF6FF), RoundedCornerShape(12.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.AccountBalanceWallet,
                    contentDescription = "Xem biểu đồ và chi tiết thu nhập",
                    tint = Color(0xFF2563EB),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
