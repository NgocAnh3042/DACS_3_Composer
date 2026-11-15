package com.example.dacs_3_composer.feature.customer.search.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SearchResultTitle(
    query: String,
    resultCount: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Kết quả cho ",
                color = Color(0xFF191C1D),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "\"$query\"",
                color = Color(0xFF2159BC),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tìm thấy $resultCount nhà hàng gần bạn",
            color = Color(0xFF727785),
            fontSize = 14.sp
        )
    }
}