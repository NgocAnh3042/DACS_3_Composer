package com.example.dacs_3_composer.feature.restaurant.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dacs_3_composer.core.model.Order
import com.example.dacs_3_composer.feature.admin.analytics.AdminPeriod
import com.example.dacs_3_composer.feature.admin.analytics.adminRevenueSummary
import com.example.dacs_3_composer.feature.admin.analytics.components.AdminRevenueBars
import com.example.dacs_3_composer.feature.admin.analytics.components.AdminRevenueLine
import com.example.dacs_3_composer.feature.admin.components.*
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestaurantRevenueDetailScreen(
    onBackClick: () -> Unit,
    homeViewModel: RestaurantHomeViewModel = viewModel()
) {
    val orders by homeViewModel.orders.collectAsState()
    val isLoading by homeViewModel.isLoading.collectAsState()

    var periodName by rememberSaveable { mutableStateOf(AdminPeriod.WEEK.name) }
    var anchor by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var selectedDate by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var detailOrder by remember { mutableStateOf<Order?>(null) }

    val period = AdminPeriod.valueOf(periodName)
    val today = LocalDate.now()
    val report = remember(orders, anchor, period, today) {
        adminRevenueSummary(orders, LocalDate.ofEpochDay(anchor), period, today)
    }
    val selected = report.days.firstOrNull { it.date.toEpochDay() == selectedDate }
        ?: report.days.firstOrNull { it.date == today }
        ?: report.days.first()

    fun move(amount: Long) {
        val date = if (period == AdminPeriod.WEEK) report.start.plusWeeks(amount) else report.start.plusMonths(amount)
        anchor = date.toEpochDay()
        selectedDate = anchor
    }

    Scaffold(
        containerColor = AdminBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Chi tiết doanh thu", color = AdminInk, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        if (homeViewModel.restaurantName.isNotBlank()) {
                            Text(homeViewModel.restaurantName, color = AdminMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại", tint = AdminInk)
                    }
                },
                actions = {
                    IconButton(onClick = homeViewModel::refreshNow, enabled = !isLoading) {
                        Icon(Icons.Default.Refresh, contentDescription = "Làm mới", tint = AdminBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (isLoading) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = AdminBlue) }
            }

            if (homeViewModel.lastUpdated.isNotBlank()) {
                item {
                    Text(
                        text = "Cập nhật lúc ${homeViewModel.lastUpdated}",
                        color = AdminMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Chuyển đổi Tuần / Tháng giống Admin
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    AdminPeriod.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = option == period,
                            onClick = {
                                periodName = option.name
                                selectedDate = anchor
                            },
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = AdminBlue,
                                activeContentColor = Color.White,
                                inactiveContainerColor = Color.White,
                                inactiveContentColor = AdminInk,
                                activeBorderColor = AdminBlue,
                                inactiveBorderColor = Color(0xFFD8E2F1)
                            ),
                            shape = SegmentedButtonDefaults.itemShape(index, 2),
                            label = { Text(if (option == AdminPeriod.WEEK) "Tuần" else "Tháng") }
                        )
                    }
                }
            }

            // Điều hướng kỳ trước / kỳ sau
            item {
                Surface(color = Color.White, shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { move(-1) }) { Icon(Icons.Default.ChevronLeft, "Kỳ trước") }
                        Text(
                            text = if (period == AdminPeriod.MONTH) {
                                "Tháng ${report.start.monthValue}/${report.start.year}"
                            } else {
                                "${report.start.format(AdminShortDate)} – ${report.end.format(AdminShortDate)}/${report.end.year}"
                            },
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.SemiBold,
                            color = AdminInk
                        )
                        IconButton(onClick = { move(1) }, enabled = report.end.isBefore(LocalDate.now())) {
                            Icon(Icons.Default.ChevronRight, "Kỳ sau")
                        }
                    }
                }
            }

            // Tổng doanh thu đơn hoàn tất & so sánh tăng trưởng
            item {
                AdminPanel {
                    Text("Doanh thu đơn hoàn tất", color = AdminMuted)
                    Text(adminMoney(report.total), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = AdminBlue)
                    Text(
                        text = report.growthPercent?.let {
                            "${if (it > 0) "+" else ""}${String.format(AdminLocale, "%.1f", it)}% so với kỳ trước"
                        } ?: if (report.comparisonCurrent > 0) {
                            "Kỳ trước chưa có doanh thu để tính tăng trưởng"
                        } else {
                            "Chưa có doanh thu trong khoảng so sánh"
                        },
                        color = AdminBlue,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "So sánh ${report.comparisonDays} ngày đầu của hai kỳ: ${adminMoney(report.comparisonCurrent)} / ${adminMoney(report.comparisonPrevious)}.",
                        color = AdminMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            // 4 chỉ số tổng hợp giống Admin
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AdminMetric("Đơn tạo trong kỳ", report.createdOrders.size.toString(), "Theo ngày đặt", Modifier.weight(1f))
                        AdminMetric("Giao thành công", report.completedCount.toString(), "Theo ngày hoàn tất", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AdminMetric("Giá trị đơn trung bình", adminMoney(report.averageOrder), "Đơn giao thành công", Modifier.weight(1f))
                        AdminMetric(
                            "Tỷ lệ hủy",
                            String.format(AdminLocale, "%.1f%%", report.cancellationRate),
                            "${report.cancelledCount} / ${report.createdOrders.size} đơn tạo trong kỳ",
                            Modifier.weight(1f)
                        )
                    }
                }
            }

            // Biểu đồ cột doanh thu theo ngày (chạm cột xem chi tiết ngày)
            item {
                AdminPanel {
                    Text("Doanh thu theo ngày", color = AdminInk, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        text = if (period == AdminPeriod.MONTH) "Vuốt ngang, chạm cột để chọn ngày" else "Chạm cột để chọn ngày",
                        style = MaterialTheme.typography.bodySmall,
                        color = AdminMuted
                    )
                    AdminRevenueBars(report, period, selected.date) { selectedDate = it.toEpochDay() }
                    Text(
                        text = "${selected.date.format(AdminShortDate)} · ${selected.orders.size} đơn · ${adminMoney(selected.revenue)}",
                        color = AdminBlue,
                        fontWeight = FontWeight.SemiBold
                    )
                    report.bestDay?.let {
                        Text(
                            text = "Ngày cao nhất: ${it.date.format(AdminShortDate)} · ${adminMoney(it.revenue)}",
                            color = AdminMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Biểu đồ đường lũy kế tăng trưởng doanh thu
            item {
                AdminPanel {
                    Text("Tăng trưởng doanh thu", color = AdminInk, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Doanh thu lũy kế theo ngày trong kỳ", color = AdminMuted, style = MaterialTheme.typography.bodySmall)
                    AdminRevenueLine(report, selected.date)
                }
            }

            if (report.undatedCompleted > 0) {
                item {
                    Text(
                        text = "${report.undatedCompleted} đơn hoàn tất chưa có ngày giao nên chưa được tính vào biểu đồ.",
                        color = AdminMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Danh sách đơn hoàn tất của ngày đang chọn trên biểu đồ
            item {
                Text(
                    text = "Đơn hoàn tất ngày ${selected.date.format(AdminShortDate)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = AdminInk
                )
                Text(
                    text = if (selected.orders.isEmpty()) "Chưa có đơn hoàn tất trong ngày này" else "${selected.orders.size} đơn · Chạm vào đơn để xem chi tiết",
                    style = MaterialTheme.typography.bodySmall,
                    color = AdminMuted
                )
            }

            items(selected.orders, key = { "day-${it.id}" }) { order ->
                RestaurantRevenueOrderRow(order = order, onClick = { detailOrder = order })
            }

            // Đơn mới tạo trong kỳ
            item {
                Spacer(Modifier.height(4.dp))
                Text("Đơn mới trong kỳ", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = AdminInk)
                Text("Tối đa 5 đơn gần nhất · Chạm để xem chi tiết", style = MaterialTheme.typography.bodySmall, color = AdminMuted)
            }

            if (report.createdOrders.isEmpty()) {
                item {
                    AdminPanel { Text("Chưa có đơn được tạo trong kỳ này.", color = AdminMuted) }
                }
            } else {
                items(report.createdOrders.take(5), key = { "recent-${it.id}" }) { order ->
                    RestaurantRevenueOrderRow(order = order, onClick = { detailOrder = order })
                }
            }
        }
    }

    detailOrder?.let { order ->
        AlertDialog(
            onDismissRequest = { detailOrder = null },
            title = { Text("Đơn hàng #${order.id}") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(restaurantOrderStatusLabel(order.status), color = AdminBlue, fontWeight = FontWeight.Bold)
                    if (order.customerName.isNotBlank()) {
                        Text("Khách: ${order.customerName} · ${order.customerPhone}")
                    }
                    if (order.customerAddress.isNotBlank()) {
                        Text("Địa chỉ: ${order.customerAddress}")
                    }
                    Text("Đặt lúc: ${order.time}")
                    order.completedAt?.let { Text("Giao xong: $it") }
                    order.items.forEach {
                        Text("${it.quantity} × ${it.name} · ${adminMoney(it.price * it.quantity)}")
                    }
                    Text("Phí giao hàng: ${adminMoney(order.shippingFee)}")
                    Text("Tổng tiền: ${adminMoney(order.totalPrice)}", fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = {
                TextButton(onClick = { detailOrder = null }) { Text("Đóng") }
            }
        )
    }
}

@Composable
private fun RestaurantRevenueOrderRow(order: Order, onClick: () -> Unit) {
    AdminPanel(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "#${order.id} · ${restaurantOrderStatusLabel(order.status)}",
                    color = AdminBlue,
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = order.customerName.ifBlank { "Khách hàng" },
                    color = AdminInk,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(order.time, style = MaterialTheme.typography.bodySmall, color = AdminMuted)
            }
            Text(adminMoney(order.totalPrice), color = AdminInk, fontWeight = FontWeight.Bold)
        }
    }
}

private fun restaurantOrderStatusLabel(status: String) = when (status) {
    "PENDING_PAYMENT" -> "Chờ thanh toán"
    "PENDING" -> "Chờ xác nhận"
    "PROCESSING" -> "Đang chuẩn bị"
    "ACCEPTED" -> "Sẵn sàng giao"
    "SHIPPING" -> "Đang giao"
    "COMPLETED" -> "Giao thành công"
    "CANCELLED" -> "Đã hủy"
    else -> "Chưa xác định"
}
