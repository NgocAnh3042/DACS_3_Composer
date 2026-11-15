package com.example.dacs_3_composer.feature.admin.complaint

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dacs_3_composer.feature.admin.complaint.components.*

@Composable
fun AdminComplaintScreen() {
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = {}, containerColor = Color(0xFF2159BC), contentColor = Color.White) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        },
        containerColor = Color(0xFFF8F9FA)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
        ) {
            item {
                Text(
                    text = "Quản lý đơn hàng & khiếu nại",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF191C1D)
                )
            }

            item { ComplaintStatsRow() }
            item { AdvancedFilterCard() }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Danh sách đơn hàng (24)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(text = "Mới nhất ▾", fontSize = 13.sp, color = Color.Gray)
                }
            }

            item {
                AdminOrderCardItem(
                    orderId = "#ORD-88291",
                    restaurantName = "Gourmet Central - Q1",
                    details = "Khách hàng: Trần Văn Anh • 3 món • 1.250.000đ",
                    time = "14:20 - Hôm nay",
                    status = "PENDING"
                )
            }
            item { ActiveComplaintsSection() }
            item { SystemLogCard() }
        }
    }
}