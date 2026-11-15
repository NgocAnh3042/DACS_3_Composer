package com.example.dacs_3_composer.feature.customer.search.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBar(
    onFilterClick: () -> Unit,
    onPriceClick: () -> Unit,
    onDistanceClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Nút Lọc chính
        Button(
            onClick = onFilterClick,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2159BC)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text(text = "Lọc", color = Color.White)
        }

        // Nút lọc Giá
        AssistChip(
            onClick = onPriceClick,
            label = { Text("Giá", color = Color(0xFF191C1D)) },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = Color(0xFFE7E8E9)
            ),
            border = null,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.weight(1f)
        )

        // Nút lọc Khoảng cách
        AssistChip(
            onClick = onDistanceClick,
            label = { Text("Khoảng cách", color = Color(0xFF191C1D)) },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = Color(0xFFE7E8E9)
            ),
            border = null,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.weight(1.5f)
        )
    }
}