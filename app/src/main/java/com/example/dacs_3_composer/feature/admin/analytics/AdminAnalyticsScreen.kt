package com.example.dacs_3_composer.feature.admin.analytics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun AdminAnalyticsScreen(
    onNavigateToChat: () -> Unit = {},
    viewModel: AdminAnalyticsViewModel = viewModel(),
    onNavigateToUsers: () -> Unit = {},
    onNavigateToPromotions: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {}
) {
    val data by viewModel.analyticsState.collectAsState()
    AdminDashboardContent(data, viewModel::refresh, onNavigateToUsers, onNavigateToPromotions, onNavigateToChat, onNavigateToProfile)
}
