package com.example.dacs_3_composer.feature.restaurant.menu.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp

@Composable
fun ScreenTitle(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF191C1D),
        modifier = modifier // Áp dụng trực tiếp Modifier sạch được truyền từ HomeScreen/Screen gốc
    )
}

@Preview(showBackground = true)
@Composable
fun ScreenTitlePreview() {
    ScreenTitle(title = "Quản lý thực đơn")
}