package com.example.dacs_3_composer.feature.customer.profile.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MembershipAndWalletSection(
    memberRank: String,
    walletBalance: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Thẻ thành viên (Bên trái)
        Card(
            modifier = Modifier
                .weight(1f)
                .height(140.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Icon ngôi sao xanh
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFFE8F0FE), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFF2159BC),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(text = memberRank, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color(0xFF191C1D))
                    Text(
                        text = "HẠNG VÀNG",
                        fontSize = 10.sp,
                        color = Color(0xFF2159BC),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Thẻ Ví Gourmet Pay (Bên phải)
        Card(
            modifier = Modifier
                .weight(1f)
                .height(140.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Icon Ví
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFFE8F0FE), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Wallet,
                        contentDescription = null,
                        tint = Color(0xFF2159BC),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column {
                    Text(text = walletBalance, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        text = "VÍ GOURMET PAY",
                        fontSize = 10.sp,
                        color = Color(0xFF2159BC),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
