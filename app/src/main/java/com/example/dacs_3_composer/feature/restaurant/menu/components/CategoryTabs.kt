package com.example.dacs_3_composer.feature.restaurant.menu.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CategoryTabs(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(), // Nhận modifier sạch định hình padding từ ngoài
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val categories = listOf("Tất cả", "Món chính", "Khai vị")
        categories.forEach { category ->
            val isSelected = selectedCategory == category
            Button(
                onClick = { onCategorySelected(category) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) Color(0xFF2159BC) else Color(0xFFEAECEF),
                    contentColor = if (isSelected) Color.White else Color(0xFF191C1D)
                ),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    text = category,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CategoryTabsPreview() {
    CategoryTabs(selectedCategory = "Tất cả", onCategorySelected = {})
}