package com.example.dacs_3_composer.feature.shipper.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dacs_3_composer.feature.shipper.dashboard.ShipperViewModel
import com.example.dacs_3_composer.feature.shipper.orders.components.ActiveDeliveryCard
import com.example.dacs_3_composer.feature.shipper.orders.components.AvailableOrderCard
import com.example.dacs_3_composer.feature.shipper.orders.components.HistoryOrderCard // Thêm Component mới

@Composable
fun ShipperOrdersScreen(
    viewModel: ShipperViewModel,
    onNavigateToDetail: (String) -> Unit
) {
    val availableOrders by viewModel.availableOrders.collectAsState()
    val activeDeliveryOrder by viewModel.activeDeliveryOrder.collectAsState()
    val historyOrders by viewModel.historyOrders.collectAsState() // 🌟 LẤY DỮ LIỆU LỊCH SỬ TỪ VIEWMODEL

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Đang giao", "Lịch sử")

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
            }
        },
        containerColor = Color(0xFFF8FAFC)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Tiêu đề chức năng đơn hàng
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Đơn hàng của tôi",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827)
                )
                Text(
                    text = "Quản lý các chuyến giao hàng hiện tại và lịch sử.",
                    fontSize = 14.sp,
                    color = Color(0xFF6B7280),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Thanh chuyển đổi Tab
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = Color(0xFF2563EB),
                divider = {}
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                fontSize = 16.sp,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Nội dung bên trong danh sách theo từng Tab
            // --- TAB 1: ĐANG GIAO ---
            if (selectedTab == 0) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // CHỈ hiển thị nếu có đơn hàng đang trong tiến trình giao của chính Shipper này
                    if (activeDeliveryOrder != null) {
                        item {
                            ActiveDeliveryCard(
                                order = activeDeliveryOrder!!,
                                onDetailClick = { onNavigateToDetail(activeDeliveryOrder!!.id) }
                            )
                        }
                    } else {
                        // Nếu activeDeliveryOrder bằng null (chưa bấm nhận đơn nào hoặc đã giao xong)
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Bạn chưa nhận đơn hàng nào trong tiến trình.",
                                    color = Color.Gray,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            } else {
                // --- TAB 2: LỊCH SỬ ĐƠN GIAO (ĐÃ ĐƯỢC SỬA) ---
                if (historyOrders.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "Lịch sử đơn hàng trống", color = Color.Gray, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(historyOrders, key = { it.id }) { historicalOrder ->
                            HistoryOrderCard(
                                order = historicalOrder,
                                onCardClick = { onNavigateToDetail(historicalOrder.id) } // Vẫn xem được chi tiết đơn cũ nếu cần
                            )
                        }
                    }
                }
            }
        }
    }
}