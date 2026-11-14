package com.example.dacs_3_composer.feature.shipper.dashboard.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CustomerInfoCard(
    customerName: String,
    customerPhone: String,
    customerAddress: String,
    restaurantName: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Phần thông tin định danh Khách hàng
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0xFFDBEAFE), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, null, tint = Color(0xFF2563EB))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(text = customerName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                        Text(text = customerPhone, fontSize = 13.sp, color = Color(0xFF6B7280))
                    }
                }

                IconButton(
                    onClick = {},
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color(0xFF2563EB), CircleShape)
                ) {
                    Icon(Icons.Default.Call, contentDescription = "Call Client", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color(0xFFF3F4F6), thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))

            // Địa chỉ giao hàng (Khách hàng)
            Row {
                Icon(Icons.Default.LocationOn, null, tint = Color(0xFF2563EB), modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(text = "Địa chỉ giao hàng", fontSize = 12.sp, color = Color(0xFF9CA3AF), fontWeight = FontWeight.Medium)
                    Text(text = customerAddress, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF374151))
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Điểm lấy hàng (Nhà hàng)
            Row {
                Icon(Icons.Default.Storefront, null, tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(text = "Điểm lấy hàng", fontSize = 12.sp, color = Color(0xFF9CA3AF), fontWeight = FontWeight.Medium)
                    Text(text = restaurantName, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF374151))
                }
            }
        }
    }
}