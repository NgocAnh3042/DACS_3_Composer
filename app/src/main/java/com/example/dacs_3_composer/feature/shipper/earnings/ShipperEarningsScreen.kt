package com.example.dacs_3_composer.feature.shipper.earnings

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dacs_3_composer.feature.shipper.dashboard.ShipperViewModel
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val IncomeBlue = Color(0xFF2159BC)
private val IncomeInk = Color(0xFF172333)
private val IncomeMuted = Color(0xFF66758A)
private val vi = Locale.forLanguageTag("vi-VN")
private val shortDate = DateTimeFormatter.ofPattern("dd/MM", vi)
private val fullDate = DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", vi)
private fun money(value: Double) = NumberFormat.getNumberInstance(vi).apply { maximumFractionDigits = 0 }.format(value) + " đ"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShipperEarningsScreen(viewModel: ShipperViewModel, onBack: () -> Unit) {
    val orders by viewModel.historyOrders.collectAsState()
    val loading by viewModel.historyLoading.collectAsState()
    val error by viewModel.historyError.collectAsState()
    var periodName by rememberSaveable { mutableStateOf(EarningsPeriod.WEEK.name) }
    var anchorDay by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var selectedDay by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    val period = EarningsPeriod.valueOf(periodName)
    val summary = remember(orders, anchorDay, period, viewModel.currentShipperId) {
        earningsSummary(orders, viewModel.currentShipperId, LocalDate.ofEpochDay(anchorDay), period)
    }
    val chosen = summary.days.firstOrNull { it.date.toEpochDay() == selectedDay } ?: summary.days.first()
    fun movePeriod(amount: Long) {
        val next = if (period == EarningsPeriod.WEEK) summary.start.plusWeeks(amount) else summary.start.plusMonths(amount)
        anchorDay = next.toEpochDay()
        selectedDay = next.toEpochDay()
    }
    Scaffold(
        containerColor = Color(0xFFF5F8FC),
        topBar = {
            TopAppBar(title = { Text("Thu nhập giao hàng", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") } },
                actions = { IconButton(onClick = { viewModel.refreshEarnings() }, enabled = !loading) { Icon(Icons.Default.Refresh, "Làm mới thu nhập") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White))
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Color(0xFFE8EEF7)).padding(4.dp)) {
                    EarningsPeriod.entries.forEach { option ->
                        val active = option == period
                        Box(Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp))
                            .background(if (active) IncomeBlue else Color.Transparent)
                            .selectable(active, role = Role.Tab, onClick = {
                                periodName = option.name
                                selectedDay = anchorDay
                            }), contentAlignment = Alignment.Center) {
                            Text(if (option == EarningsPeriod.WEEK) "Tuần" else "Tháng", color = if (active) Color.White else IncomeInk, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            item {
                IncomePanel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { movePeriod(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Kỳ trước") }
                        Text(if (period == EarningsPeriod.MONTH) "Tháng ${summary.start.monthValue}/${summary.start.year}"
                            else "${summary.start.format(shortDate)} – ${summary.end.format(shortDate)}/${summary.end.year}",
                            Modifier.weight(1f), fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        IconButton(onClick = { movePeriod(1) }, enabled = summary.end.isBefore(LocalDate.now())) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Kỳ sau")
                        }
                    }
                }
            }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = IncomeBlue) }
            if (error != null) item {
                IncomePanel { Text(error.orEmpty(), color = MaterialTheme.colorScheme.error); TextButton(onClick = { viewModel.refreshEarnings() }) { Text("Thử lại") } }
            }
            item {
                IncomePanel {
                    Text("Tổng phí giao hàng", color = IncomeMuted)
                    Text(money(summary.total), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = IncomeInk)
                    val comparison = if (summary.previousTotal == 0.0) "Kỳ trước: 0 đ" else {
                        val change = (summary.total - summary.previousTotal) / summary.previousTotal * 100
                        "${if (change > 0) "+" else ""}${String.format(vi, "%.1f", change)}% so với trọn kỳ trước"
                    }
                    Text(comparison, color = IncomeBlue, style = MaterialTheme.typography.bodySmall)
                    Surface(color = Color(0xFFF0F5FD), shape = RoundedCornerShape(12.dp)) {
                        Text("Chỉ tính đơn đã giao thành công. Không bao gồm tiền thu hộ hoặc tiền ứng món.", Modifier.padding(12.dp), color = IncomeMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(summary.bestDay?.let { "Ngày cao nhất: ${it.date.format(shortDate)} · ${money(it.total)}" } ?: "Chưa có đơn giao thành công trong kỳ này.",
                        style = MaterialTheme.typography.bodySmall, color = IncomeMuted)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(Color.White).padding(16.dp)) {
                        Text("Tổng số đơn", style = MaterialTheme.typography.bodySmall, color = IncomeMuted)
                        Text("${summary.orderCount} đơn", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = IncomeInk)
                    }
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(Color.White).padding(16.dp)) {
                        Text("Trung bình mỗi đơn", style = MaterialTheme.typography.bodySmall, color = IncomeMuted)
                        Text(money(summary.averagePerOrder), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = IncomeInk)
                    }
                }
            }
            item {
                IncomePanel {
                    Text("Thu nhập theo ngày", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = IncomeInk)
                    Text(if (period == EarningsPeriod.MONTH) "Vuốt ngang và chạm cột để xem từng ngày" else "Chạm cột để xem đơn giao trong ngày",
                        style = MaterialTheme.typography.bodySmall, color = IncomeMuted)
                    EarningsChart(summary, period, chosen.date) { selectedDay = it.toEpochDay() }
                }
            }
            if (summary.undatedCount > 0) item {
                Text("Có ${summary.undatedCount} đơn chưa có ngày hoàn tất nên chưa được đưa vào biểu đồ.", style = MaterialTheme.typography.bodySmall, color = IncomeMuted)
            }
            item {
                Text("Chi tiết ${chosen.date.format(fullDate)}", fontWeight = FontWeight.Bold, color = IncomeInk)
                Text("${chosen.orders.size} đơn · ${money(chosen.total)}", style = MaterialTheme.typography.bodySmall, color = IncomeMuted)
            }
            if (chosen.orders.isEmpty()) item { IncomePanel { Text("Chưa có đơn giao thành công trong ngày này.", color = IncomeMuted) } }
            items(chosen.orders, key = { it.id }) { order ->
                IncomePanel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("#${order.id} · ${order.completedAt?.drop(11)?.take(5).orEmpty()}", style = MaterialTheme.typography.labelMedium, color = IncomeMuted)
                            Text(order.restaurantName.ifBlank { "Nhà hàng" }, fontWeight = FontWeight.SemiBold, color = IncomeInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("Giao thành công", color = Color(0xFF007C64), style = MaterialTheme.typography.bodySmall)
                        }
                        Text("+${money(order.shippingFee)}", Modifier.padding(start = 8.dp), color = IncomeBlue, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun IncomePanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = Color.White) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun EarningsChart(summary: EarningsSummary, period: EarningsPeriod, selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val maximum = summary.days.maxOf { it.total }.coerceAtLeast(1.0)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columnWidth = if (period == EarningsPeriod.WEEK) maxOf(48.dp, maxWidth / 7) else 48.dp
        LaunchedEffect(summary.start, period) {
            val index = summary.days.indexOfFirst { it.date == selected }.coerceAtLeast(0)
            scroll.scrollTo(with(density) { (columnWidth * index - maxWidth / 2 + columnWidth / 2).roundToPx() }.coerceAtLeast(0))
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(scroll), verticalAlignment = Alignment.Bottom) {
            summary.days.forEach { day ->
                val active = day.date == selected
                Column(Modifier.width(columnWidth).height(230.dp)
                    .semantics { contentDescription = "${day.date.format(fullDate)}, ${money(day.total)}, ${day.orders.size} đơn" }
                    .selectable(active, role = Role.RadioButton, onClick = { onSelect(day.date) }).padding(horizontal = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (active) shortMoney(day.total) else "", fontSize = 10.sp, color = IncomeBlue, maxLines = 1)
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        Box(Modifier.fillMaxWidth().height(if (day.total == 0.0) 2.dp else (155 * day.total / maximum).dp)
                            .clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp))
                            .background(if (active) IncomeBlue else Color(0xFFDEE6F1)))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(if (period == EarningsPeriod.WEEK) { if (day.date.dayOfWeek.value == 7) "CN" else "T${day.date.dayOfWeek.value + 1}" } else day.date.dayOfMonth.toString(),
                        color = if (active) IncomeBlue else IncomeMuted, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp)
                    if (period == EarningsPeriod.WEEK) Text(day.date.format(shortDate), color = IncomeMuted, fontSize = 9.sp)
                }
            }
        }
    }
}

private fun shortMoney(value: Double) = when {
    value >= 1_000_000 -> String.format(vi, "%.1ftr", value / 1_000_000)
    value >= 1_000 -> String.format(vi, "%.0fk", value / 1_000)
    else -> value.toInt().toString()
}
