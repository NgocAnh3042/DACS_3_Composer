package com.example.dacs_3_composer.feature.restaurant.order

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dacs_3_composer.core.model.Order
import com.example.dacs_3_composer.core.model.OrderStatus

@Composable
fun RestaurantOrderScreen(
    modifier: Modifier = Modifier,
    viewModel: RestaurantOrderViewModel = viewModel()
) {
    val availableTabs = remember { OrderStatus.values().filter { it != OrderStatus.PENDING_PAYMENT } }
    var selectedTab by remember { mutableStateOf(OrderStatus.PENDING) }
    val allOrders by viewModel.orders.collectAsState()

    val filteredOrders = allOrders.filter { it.status == selectedTab.name }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
    ) {
        Text(
            text = "Quản lý đơn hàng",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 24.dp, start = 16.dp, end = 16.dp, bottom = 12.dp),
            color = Color(0xFF191C1D)
        )

        ScrollableTabRow(
            selectedTabIndex = availableTabs.indexOf(selectedTab).coerceAtLeast(0),
            containerColor = Color.Transparent,
            contentColor = Color(0xFF2159BC),
            edgePadding = 16.dp,
            divider = {}
        ) {
            availableTabs.forEach { status ->
                Tab(
                    selected = selectedTab == status,
                    onClick = { selectedTab = status },
                    text = {
                        Text(
                            text = status.displayName,
                            fontSize = 14.sp,
                            fontWeight = if (selectedTab == status) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (viewModel.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color(0xFF2159BC))
            } else if (filteredOrders.isEmpty()) {
                Text(text = "Không có đơn hàng nào ở mục này.", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(filteredOrders, key = { it.id }) { order ->
                        OrderItemCard(
                            order = order,
                            onPrimaryAction = {
                                when (order.status) {
                                    OrderStatus.PENDING.name -> viewModel.updateOrderStatus(order.id, OrderStatus.PROCESSING)
                                    OrderStatus.PROCESSING.name -> viewModel.updateOrderStatus(order.id, OrderStatus.ACCEPTED)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OrderItemCard(
    order: Order,
    onPrimaryAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "#MÃ: ${order.id.takeLast(6).uppercase()}",
                        fontSize = 14.sp,
                        color = Color(0xFF727785),
                        fontWeight = FontWeight.Bold
                    )
                    // ✅ HIỂN THỊ PHƯƠNG THỨC THANH TOÁN
                    val methodText = if (order.paymentMethod == "ONLINE") "Đã thanh toán (ONLINE)" else "Thanh toán mặt (COD)"
                    val methodColor = if (order.paymentMethod == "ONLINE") Color(0xFF2ECC71) else Color(0xFFE67E22)
                    Text(text = methodText, fontSize = 12.sp, color = methodColor, fontWeight = FontWeight.Bold)
                }

                val currentStatus = try { OrderStatus.valueOf(order.status) } catch (e: Exception) { OrderStatus.PENDING }
                StatusBadge(status = currentStatus)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(text = order.customerName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF191C1D))
            if (order.customerPhone.isNotBlank()) {
                Text(text = "SĐT Khách: ${order.customerPhone}", fontSize = 13.sp, color = Color(0xFF555555))
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = Color(0xFFF1F3F4))
            Spacer(modifier = Modifier.height(8.dp))

            order.items.forEach { item ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = item.name, fontSize = 14.sp, color = Color(0xFF191C1D), modifier = Modifier.weight(1f))
                    Text(text = "x${item.quantity}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color(0xFFF1F3F4))
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    val label = if (order.paymentMethod == "ONLINE") "Đã trả (Doanh thu)" else "Cần thu (COD)"
                    Text(text = label, fontSize = 12.sp, color = Color(0xFF727785))
                    Text(
                        text = "${String.format("%,.0f", order.totalPrice)}đ",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2159BC)
                    )
                }

                when (order.status) {
                    OrderStatus.PENDING.name -> {
                        Button(
                            onClick = onPrimaryAction,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE67E22)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(text = "Nhận đơn ngay", fontSize = 14.sp)
                        }
                    }
                    OrderStatus.PROCESSING.name -> {
                        Button(
                            onClick = onPrimaryAction,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2ECC71)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(text = "Đã nấu xong", fontSize = 14.sp, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: OrderStatus) {
    val bgColor = when (status) {
        OrderStatus.PENDING_PAYMENT -> Color(0xFFFCE4EC)
        OrderStatus.PENDING -> Color(0xFFFFF4E5)
        OrderStatus.PROCESSING -> Color(0xFFE8F0FE)
        OrderStatus.ACCEPTED -> Color(0xFFE3F2FD)
        OrderStatus.SHIPPING -> Color(0xFFE8F8F5)
        OrderStatus.COMPLETED -> Color(0xFFF1F3F4)
        OrderStatus.CANCELLED -> Color(0xFFFFEBEE)
    }
    val textColor = when (status) {
        OrderStatus.PENDING_PAYMENT -> Color(0xFFC2185B)
        OrderStatus.PENDING -> Color(0xFFFFA000)
        OrderStatus.PROCESSING -> Color(0xFF2159BC)
        OrderStatus.ACCEPTED -> Color(0xFF1976D2)
        OrderStatus.SHIPPING -> Color(0xFF2ECC71)
        OrderStatus.COMPLETED -> Color(0xFF727785)
        OrderStatus.CANCELLED -> Color(0xFFD32F2F)
    }

    Box(modifier = Modifier.background(bgColor, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(text = status.displayName.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textColor)
    }
}
