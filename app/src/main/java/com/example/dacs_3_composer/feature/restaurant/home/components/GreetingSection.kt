package com.example.dacs_3_composer.feature.restaurant.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ui/restaurant/home/components/GreetingSection.kt
@Composable
fun GreetingSection(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(top = 8.dp)) {
        Text("Chào buổi sáng, Restaurant!", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF191C1D))
        Text("Hôm nay hệ thống đang vận hành ổn định.", fontSize = 13.sp, color = Color(0xFF727785))
    }
}