package com.example.dacs_3_composer.feature.customer.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.dacs_3_composer.core.model.Dish

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllDishesScreen(dishes: List<Dish>, loading: Boolean, failed: Boolean, onRetry: () -> Unit, onBack: () -> Unit, onDish: (Dish) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Tất cả món ăn") }, windowInsets = WindowInsets(0), navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") }
        })
        if (dishes.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                when {
                    loading -> CircularProgressIndicator()
                    failed -> TextButton(onClick = onRetry) { Text("Chưa tải được món · Thử lại") }
                    else -> Text("Chưa có món ăn.")
                }
            }
        } else LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(dishes, key = { it.id }) { dish -> DeliciousDishItem(dish, onClick = { onDish(dish) }) }
        }
    }
}
