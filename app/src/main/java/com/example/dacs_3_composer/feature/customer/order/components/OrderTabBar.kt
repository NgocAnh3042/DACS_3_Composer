package com.example.dacs_3_composer.feature.customer.order.components

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun OrderTabBar(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf("Chờ xác nhận", "Đang đến", "Lịch sử", "Đánh giá")

    ScrollableTabRow(
        selectedTabIndex = selectedTabIndex,
        modifier = modifier.fillMaxWidth(),
        containerColor = Color.White,
        contentColor = Color(0xFF2159BC), // Màu xanh chủ đạo cho Tab được chọn
        edgePadding = 16.dp,
        indicator = { tabPositions ->
            TabRowDefaults.SecondaryIndicator(
                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                color = Color(0xFF2159BC) // Thanh gạch chân dưới Tab màu xanh
            )
        }
    ) {
        tabs.forEachIndexed { index, title ->
            Tab(
                selected = selectedTabIndex == index,
                onClick = { onTabSelected(index) },
                text = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        // Tab được chọn sẽ có chữ màu xanh dương đậm, tab khác màu xám
                        color = if (selectedTabIndex == index) Color(0xFF2159BC) else Color(0xFF727785)
                    )
                }
            )
        }
    }
}