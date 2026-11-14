package com.example.dacs_3_composer.feature.restaurant.menu.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(), // Không gán cứng padding bên trong nữa
        placeholder = { Text("Tìm kiếm món ăn...", color = Color(0xFF727785)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search Icon",
                tint = Color(0xFF727785)
            )
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            errorBorderColor = Color.Transparent,
            disabledBorderColor = Color.Transparent,
            focusedContainerColor = Color(0xFFEAECEF),
            unfocusedContainerColor = Color(0xFFEAECEF)
        ),
        shape = RoundedCornerShape(20.dp),
        singleLine = true
    )
}

@Preview(showBackground = true)
@Composable
fun SearchBarPreview() {
    SearchBar(query = "", onQueryChange = {})
}