package com.example.dacs_3_composer.feature.restaurant.home.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ui/restaurant/home/components/TopDishesSectionTitle.kt
@Composable
fun TopDishesSectionTitle(modifier: Modifier = Modifier) {
    Text(
        text = "Món ăn bán chạy",
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        color = Color(0xFF191C1D),
        modifier = modifier.padding(vertical = 4.dp)
    )
}