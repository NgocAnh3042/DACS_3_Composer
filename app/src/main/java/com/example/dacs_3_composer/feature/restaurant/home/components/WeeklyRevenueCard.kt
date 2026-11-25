package com.example.dacs_3_composer.feature.restaurant.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dacs_3_composer.core.model.Order
import com.example.dacs_3_composer.feature.admin.analytics.AdminPeriod
import com.example.dacs_3_composer.feature.admin.analytics.adminRevenueSummary
import com.example.dacs_3_composer.feature.admin.analytics.components.AdminRevenueBars
import com.example.dacs_3_composer.feature.admin.components.AdminBlue
import com.example.dacs_3_composer.feature.admin.components.AdminMuted
import com.example.dacs_3_composer.feature.admin.components.AdminShortDate
import com.example.dacs_3_composer.feature.admin.components.adminMoney
import java.time.LocalDate

// BIỂU ĐỒ HOẠT ĐỘNG DOANH THU TƯƠNG TÁC (CHẠM CỘT & XEM CHI TIẾT)
@Composable
fun WeeklyRevenueCard(
    chartWeights: List<Float>,
    modifier: Modifier = Modifier
) {
    InteractiveWeeklyRevenueCard(
        chartWeights = chartWeights,
        orders = emptyList(),
        onDetailClick = {},
        modifier = modifier
    )
}

@Composable
fun InteractiveWeeklyRevenueCard(
    chartWeights: List<Float>,
    orders: List<Order> = emptyList(),
    onDetailClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now()
    var selectedEpoch by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    val summary = remember(orders, today) {
        adminRevenueSummary(orders, today, AdminPeriod.WEEK, today)
    }
    val selected = summary.days.firstOrNull { it.date.toEpochDay() == selectedEpoch }
        ?: summary.days.firstOrNull { it.date == today }
        ?: summary.days.first()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Doanh thu hàng tuần",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF191C1D)
                    )
                    Text(
                        text = "Chạm cột để xem từng ngày",
                        fontSize = 11.sp,
                        color = AdminMuted
                    )
                }
                TextButton(
                    onClick = onDetailClick,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Chi tiết", color = Color(0xFF2159BC), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Xem chi tiết biểu đồ doanh thu",
                        tint = Color(0xFF2159BC),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AdminRevenueBars(
                summary = summary,
                period = AdminPeriod.WEEK,
                selected = selected.date,
                onSelect = { selectedEpoch = it.toEpochDay() }
            )

            Text(
                text = "${selected.date.format(AdminShortDate)} · ${selected.orders.size} đơn · ${adminMoney(selected.revenue)}",
                color = AdminBlue,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium
            )
            summary.bestDay?.let { best ->
                Text(
                    text = "Ngày cao nhất tuần: ${best.date.format(AdminShortDate)} · ${adminMoney(best.revenue)}",
                    color = AdminMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}