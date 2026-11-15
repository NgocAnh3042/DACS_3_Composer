package com.example.dacs_3_composer.feature.admin.analytics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dacs_3_composer.core.model.Order
import com.example.dacs_3_composer.feature.admin.analytics.components.AdminRevenueBars
import com.example.dacs_3_composer.feature.admin.analytics.components.AdminRevenueLine
import com.example.dacs_3_composer.feature.admin.components.*
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardContent(data: AnalyticsState, onRefresh: () -> Unit, onUsers: () -> Unit, onPromotions: () -> Unit, onChat: () -> Unit, onProfile: () -> Unit) {
    var periodName by rememberSaveable { mutableStateOf(AdminPeriod.WEEK.name) }
    var anchor by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var selectedDate by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var detailOrder by remember { mutableStateOf<Order?>(null) }
    val period = AdminPeriod.valueOf(periodName)
    val today = LocalDate.now()
    val report = remember(data.orders, anchor, period, today) { adminRevenueSummary(data.orders, LocalDate.ofEpochDay(anchor), period, today) }
    val selected = report.days.firstOrNull { it.date.toEpochDay() == selectedDate } ?: report.days.first()
    fun move(amount: Long) {
        val date = if (period == AdminPeriod.WEEK) report.start.plusWeeks(amount) else report.start.plusMonths(amount)
        anchor = date.toEpochDay(); selectedDate = anchor
    }
    Scaffold(containerColor = AdminBackground, topBar = {
        TopAppBar(title = { Text("Tổng quan", color = AdminInk, fontWeight = FontWeight.Bold) },
            actions = {
                IconButton(onClick = onRefresh, enabled = !data.isLoading) { Icon(Icons.Default.Refresh, "Làm mới tổng quan", tint = AdminBlue) }
                IconButton(onClick = onChat) { Icon(Icons.Default.ChatBubbleOutline, "Hộp thư hỗ trợ", tint = AdminBlue) }
                IconButton(onClick = onProfile) { Icon(Icons.Default.AccountCircle, "Cá nhân quản trị", tint = AdminBlue) }
            }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White))
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Text("Xin chào, ${data.adminName.ifBlank { "quản trị viên" }}", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = AdminInk)
                Text(if (data.lastUpdated.isBlank()) "Theo dõi hoạt động FoodHub" else "Cập nhật lúc ${data.lastUpdated}", color = AdminMuted, style = MaterialTheme.typography.bodySmall)
            }
            if (data.isLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = AdminBlue) }
            if (data.error != null) item { AdminPanel { Text(data.error, color = MaterialTheme.colorScheme.error); TextButton(onClick = onRefresh) { Text("Thử lại") } } }
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    AdminPeriod.entries.forEachIndexed { index, option ->
                        SegmentedButton(selected = option == period, onClick = { periodName = option.name; selectedDate = anchor },
                            colors = SegmentedButtonDefaults.colors(activeContainerColor = AdminBlue, activeContentColor = Color.White,
                                inactiveContainerColor = Color.White, inactiveContentColor = AdminInk, activeBorderColor = AdminBlue, inactiveBorderColor = Color(0xFFD8E2F1)),
                            shape = SegmentedButtonDefaults.itemShape(index, 2), label = { Text(if (option == AdminPeriod.WEEK) "Tuần" else "Tháng") })
                    }
                }
            }
            item {
                Surface(color = Color.White, shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { move(-1) }) { Icon(Icons.Default.ChevronLeft, "Kỳ trước") }
                        Text(if (period == AdminPeriod.MONTH) "Tháng ${report.start.monthValue}/${report.start.year}" else "${report.start.format(AdminShortDate)} – ${report.end.format(AdminShortDate)}/${report.end.year}",
                            Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.SemiBold, color = AdminInk)
                        IconButton(onClick = { move(1) }, enabled = report.end.isBefore(LocalDate.now())) { Icon(Icons.Default.ChevronRight, "Kỳ sau") }
                    }
                }
            }
            item { AdminPanel {
                Text("Doanh thu đơn hoàn tất", color = AdminMuted)
                Text(adminMoney(report.total), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = AdminBlue)
                Text(report.growthPercent?.let { "${if (it > 0) "+" else ""}${String.format(AdminLocale, "%.1f", it)}% so với kỳ trước" }
                    ?: if (report.comparisonCurrent > 0) "Kỳ trước chưa có doanh thu để tính tăng trưởng" else "Chưa có doanh thu trong khoảng so sánh", color = AdminBlue, style = MaterialTheme.typography.bodySmall)
                Text("So sánh ${report.comparisonDays} ngày đầu của hai kỳ: ${adminMoney(report.comparisonCurrent)} / ${adminMoney(report.comparisonPrevious)}.",
                    color = AdminMuted, style = MaterialTheme.typography.labelSmall)
                Text("Tổng tiền đơn đã giao sau giảm giá, gồm phí giao hàng. Đây là doanh thu đơn hàng, không phải lợi nhuận.", color = AdminMuted, style = MaterialTheme.typography.bodySmall)
            } }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AdminMetric("Đơn tạo trong kỳ", report.createdOrders.size.toString(), "Theo ngày đặt", Modifier.weight(1f))
                        AdminMetric("Giao thành công", report.completedCount.toString(), "Theo ngày hoàn tất", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AdminMetric("Giá trị đơn trung bình", adminMoney(report.averageOrder), "Đơn giao thành công", Modifier.weight(1f))
                        AdminMetric("Tỷ lệ hủy", String.format(AdminLocale, "%.1f%%", report.cancellationRate), "${report.cancelledCount} / ${report.createdOrders.size} đơn tạo trong kỳ", Modifier.weight(1f))
                    }
                }
            }
            item { AdminPanel {
                Text("Doanh thu theo ngày", color = AdminInk, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(if (period == AdminPeriod.MONTH) "Vuốt ngang, chạm cột để chọn ngày" else "Chạm cột để chọn ngày", style = MaterialTheme.typography.bodySmall, color = AdminMuted)
                AdminRevenueBars(report, period, selected.date) { selectedDate = it.toEpochDay() }
                Text("${selected.date.format(AdminShortDate)} · ${selected.orders.size} đơn · ${adminMoney(selected.revenue)}", color = AdminBlue, fontWeight = FontWeight.SemiBold)
                report.bestDay?.let { Text("Ngày cao nhất: ${it.date.format(AdminShortDate)} · ${adminMoney(it.revenue)}", color = AdminMuted, style = MaterialTheme.typography.bodySmall) }
            } }
            item { AdminPanel {
                Text("Tăng trưởng doanh thu", color = AdminInk, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Doanh thu lũy kế theo ngày trong kỳ", color = AdminMuted, style = MaterialTheme.typography.bodySmall)
                AdminRevenueLine(report, selected.date)
            } }
            if (report.undatedCompleted > 0) item { Text("${report.undatedCompleted} đơn hoàn tất chưa có ngày giao nên chưa được tính vào biểu đồ.", color = AdminMuted, style = MaterialTheme.typography.bodySmall) }
            item { AdminPanel {
                Text("Hoạt động hiện tại", fontWeight = FontWeight.Bold, color = AdminInk)
                DashboardCount("Đơn đang xử lý / giao", data.shippingOrders.toString())
                DashboardCount("Tổng tài khoản", data.totalUsers.toString())
                DashboardCount("Khách hàng", data.users.count { it.role in setOf("user", "customer") }.toString())
                DashboardCount("Quán đang hoạt động", data.activeStores.toString())
                DashboardCount("Tài khoản shipper", data.totalShippers.toString())
                Text("Số liệu toàn hệ thống tại lần cập nhật gần nhất.", style = MaterialTheme.typography.labelSmall, color = AdminMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onUsers, modifier = Modifier.weight(1f)) { Text("Người dùng") }
                    OutlinedButton(onClick = onPromotions, modifier = Modifier.weight(1f)) { Text("Khuyến mãi") }
                }
            } }
            item {
                Text("Đơn mới trong kỳ", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AdminInk)
                Text("Tối đa 5 đơn gần nhất · Chạm để xem chi tiết", style = MaterialTheme.typography.bodySmall, color = AdminMuted)
            }
            if (report.createdOrders.isEmpty()) item { AdminPanel { Text("Chưa có đơn được tạo trong kỳ này.", color = AdminMuted) } }
            items(report.createdOrders.take(5), key = { it.id }) { order ->
                AdminPanel(Modifier.clickable { detailOrder = order }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("#${order.id} · ${adminStatusLabel(order.status)}", color = AdminBlue, style = MaterialTheme.typography.labelMedium)
                            Text(order.restaurantName.ifBlank { "Nhà hàng" }, color = AdminInk, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(order.time, style = MaterialTheme.typography.bodySmall, color = AdminMuted)
                        }
                        Text(adminMoney(order.totalPrice), color = AdminInk, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
    detailOrder?.let { order ->
        AlertDialog(onDismissRequest = { detailOrder = null }, title = { Text("Đơn hàng #${order.id}") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(adminStatusLabel(order.status), color = AdminBlue, fontWeight = FontWeight.Bold)
                Text("Quán: ${order.restaurantName}")
                Text("Khách: ${order.customerName} · ${order.customerPhone}")
                Text("Địa chỉ: ${order.customerAddress}")
                Text("Đặt lúc: ${order.time}")
                order.completedAt?.let { Text("Giao xong: $it") }
                order.items.forEach { Text("${it.quantity} × ${it.name} · ${adminMoney(it.price * it.quantity)}") }
                Text("Phí giao hàng: ${adminMoney(order.shippingFee)}")
                Text("Tổng tiền: ${adminMoney(order.totalPrice)}", fontWeight = FontWeight.Bold)
            }
        }, confirmButton = { TextButton(onClick = { detailOrder = null }) { Text("Đóng") } })
    }
}

@Composable
private fun DashboardCount(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = AdminMuted, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = AdminInk, fontWeight = FontWeight.SemiBold)
    }
}

private fun adminStatusLabel(status: String) = when (status) {
    "PENDING_PAYMENT" -> "Chờ thanh toán"
    "PENDING" -> "Chờ xác nhận"
    "PROCESSING" -> "Đang chuẩn bị"
    "ACCEPTED" -> "Sẵn sàng giao"
    "SHIPPING" -> "Đang giao"
    "COMPLETED" -> "Giao thành công"
    "CANCELLED" -> "Đã hủy"
    else -> "Chưa xác định"
}
