package com.example.dacs_3_composer.ui.admin.analytics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dacs_3_composer.ui.admin.analytics.components.*

@Composable
fun AdminAnalyticsScreen(
    onNavigateToChat: () -> Unit = {}, // 🎯 THÊM: Tham số điều hướng chat
    viewModel: AdminAnalyticsViewModel = viewModel()
) {
    val analyticsData by viewModel.analyticsState.collectAsState()

    Scaffold(
        topBar = { 
            AnalyticsTopBar(
                onChatClick = onNavigateToChat // 🎯 TRUYỀN: Sự kiện click
            ) 
        },
        containerColor = Color(0xFFF8F9FA)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            item {
                OverviewStatsGrid(data = analyticsData)
            }

            item {
                RevenueChartCard(revenueDays = analyticsData.weeklyRevenue)
            }

            item { DeepInsightCard() }
        }
    }
}
