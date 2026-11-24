package com.example.dacs_3_composer.feature.restaurant.menu.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
    modifier: Modifier = Modifier,
    categories: List<String> = listOf("Tất cả", "Món chính", "Khai vị")
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(categories, key = { it.lowercase() }) { category ->
            val isSelected = selectedCategory.equals(category, ignoreCase = true)
            Button(
                onClick = { onCategorySelected(category) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) Color(0xFF2159BC) else Color(0xFFEAECEF),
                    contentColor = if (isSelected) Color.White else Color(0xFF191C1D)
                ),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Text(
                    text = category,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    maxLines = 1
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