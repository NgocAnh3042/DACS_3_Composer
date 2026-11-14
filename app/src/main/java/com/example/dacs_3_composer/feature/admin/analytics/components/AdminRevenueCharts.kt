package com.example.dacs_3_composer.feature.admin.analytics.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dacs_3_composer.feature.admin.analytics.*
import com.example.dacs_3_composer.feature.admin.components.*
import java.time.LocalDate

@Composable
fun AdminRevenueBars(summary: AdminRevenueSummary, period: AdminPeriod, selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val maximum = summary.days.maxOf { it.revenue }.coerceAtLeast(1.0)
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columnWidth = if (period == AdminPeriod.WEEK) maxOf(48.dp, maxWidth / 7) else 48.dp
        LaunchedEffect(summary.start, period) {
            val index = summary.days.indexOfFirst { it.date == selected }.coerceAtLeast(0)
            scroll.scrollTo(with(density) { (columnWidth * index - maxWidth / 2).roundToPx() }.coerceAtLeast(0))
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(scroll), verticalAlignment = Alignment.Bottom) {
            summary.days.forEach { day ->
                val active = day.date == selected
                val future = day.date.isAfter(LocalDate.now())
                Column(Modifier.width(columnWidth).height(210.dp)
                    .semantics { contentDescription = "${day.date.format(AdminFullDate)}, ${adminMoney(day.revenue)}, ${day.orders.size} đơn giao thành công" }
                    .selectable(active, enabled = !future, role = Role.RadioButton, onClick = { onSelect(day.date) }).padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (active) adminCompactMoney(day.revenue) else "", fontSize = 10.sp, color = AdminBlue, maxLines = 1)
                    Canvas(Modifier.weight(1f).fillMaxWidth()) {
                        val height = if (day.revenue == 0.0) 2.dp.toPx() else (size.height * day.revenue / maximum).toFloat()
                        drawRect(if (future) Color(0xFFF0F3F7) else if (active) AdminBlue else Color(0xFFD7E3F7),
                            topLeft = Offset(0f, size.height - height), size = Size(size.width, height))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(if (period == AdminPeriod.WEEK) { if (day.date.dayOfWeek.value == 7) "CN" else "T${day.date.dayOfWeek.value + 1}" } else day.date.dayOfMonth.toString(),
                        fontSize = 12.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal, color = if (active) AdminBlue else AdminMuted)
                    if (period == AdminPeriod.WEEK) Text(day.date.format(AdminShortDate), fontSize = 9.sp, color = AdminMuted)
                }
            }
        }
    }
}

@Composable
fun AdminRevenueLine(summary: AdminRevenueSummary, selected: LocalDate) {
    val current = summary.currentCumulative
    val previous = summary.previousCumulative
    val selectedIndex = summary.days.indexOfFirst { it.date == selected }
    val maximum = maxOf(current.maxOrNull() ?: 0.0, previous.maxOrNull() ?: 0.0)
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("● Kỳ này", color = AdminBlue, style = MaterialTheme.typography.labelMedium)
        Text("┄ Kỳ trước", color = Color(0xFF7D96BF), style = MaterialTheme.typography.labelMedium)
    }
    Text(if (selectedIndex in current.indices) "Đến ${selected.format(AdminShortDate)}: ${adminMoney(current[selectedIndex])}" else "Chưa có dữ liệu cho ngày này",
        style = MaterialTheme.typography.bodySmall, color = AdminInk)
    Row(Modifier.fillMaxWidth().height(185.dp)) {
        Column(Modifier.width(48.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            Text(adminCompactMoney(maximum), fontSize = 10.sp, color = AdminMuted)
            Text(adminCompactMoney(maximum / 2), fontSize = 10.sp, color = AdminMuted)
            Text("0", fontSize = 10.sp, color = AdminMuted)
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            Canvas(Modifier.fillMaxSize().padding(vertical = 6.dp)
                .semantics { contentDescription = "Biểu đồ doanh thu lũy kế kỳ này ${adminMoney(current.lastOrNull() ?: 0.0)}, kỳ trước cùng số ngày ${adminMoney(previous.lastOrNull() ?: 0.0)}" }) {
                val top = maximum.coerceAtLeast(1.0)
                val divisions = (summary.days.size - 1).coerceAtLeast(1)
                fun point(index: Int, value: Double) = Offset(size.width * index / divisions, size.height * (1 - value / top).toFloat())
                for (part in 0..2) {
                    val y = size.height * part / 2
                    drawLine(Color(0xFFE8EDF4), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                }
                fun series(values: List<Double>, color: Color, dashed: Boolean) {
                    if (values.isEmpty() || maximum == 0.0) return
                    val path = Path()
                    values.forEachIndexed { index, value -> val p = point(index, value); if (index == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
                    drawPath(path, color, style = Stroke(width = 2.5.dp.toPx(), pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(10f, 7f)) else null))
                    drawCircle(color, 3.dp.toPx(), point(values.lastIndex, values.last()))
                }
                series(previous, Color(0xFF93AACE), true)
                series(current, AdminBlue, false)
                if (selectedIndex in current.indices && maximum > 0) {
                    val p = point(selectedIndex, current[selectedIndex])
                    drawLine(Color(0xFFD0DCEC), Offset(p.x, 0f), Offset(p.x, size.height), 1.dp.toPx())
                    drawCircle(Color.White, 5.dp.toPx(), p)
                    drawCircle(AdminBlue, 3.dp.toPx(), p)
                }
            }
            if (maximum == 0.0) Text("Chưa có doanh thu trong khoảng so sánh", Modifier.align(Alignment.Center).padding(12.dp), style = MaterialTheme.typography.bodySmall, color = AdminMuted)
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = 48.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(summary.start.format(AdminShortDate), style = MaterialTheme.typography.labelSmall, color = AdminMuted)
        Text(summary.end.format(AdminShortDate), style = MaterialTheme.typography.labelSmall, color = AdminMuted)
    }
    Text("Đường dừng ở ngày có thể quan sát; không dự báo các ngày còn lại.", style = MaterialTheme.typography.labelSmall, color = AdminMuted)
}
