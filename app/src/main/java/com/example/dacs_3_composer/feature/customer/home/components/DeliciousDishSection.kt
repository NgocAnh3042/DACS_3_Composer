package com.example.dacs_3_composer.feature.customer.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.dacs_3_composer.core.model.Dish
import com.example.dacs_3_composer.feature.customer.home.presentation.HomeViewModel

private val MenuBlue = Color(0xFF2159BC)
private val MenuSoftBlue = Color(0xFFEAF2FA)
private val MenuBg = Color(0xFFF5F7FA)
private val MenuInk = Color(0xFF191C1D)
private val MenuMuted = Color(0xFF6B7280)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllDishesScreen(
    dishes: List<Dish>,
    loading: Boolean,
    failed: Boolean,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    categories: List<HomeViewModel.HomeCategory> = emptyList(),
    selectedCategory: String? = null,
    onSelectCategory: (String?) -> Unit = {},
    onDish: (Dish) -> Unit
) {
    var keyword by rememberSaveable { mutableStateOf("") }
    var visibleDishLimit by rememberSaveable(selectedCategory, keyword) { mutableIntStateOf(20) }

    val resolvedCategories = remember(dishes, categories) {
        categories.ifEmpty { HomeViewModel.buildCategories(dishes) }
    }

    val filteredDishes = remember(dishes, selectedCategory, keyword) {
        val byCategory = HomeViewModel.filterDishesByCategory(dishes, selectedCategory)
        val q = keyword.trim()
        if (q.isEmpty()) {
            byCategory
        } else {
            byCategory.filter { d ->
                d.name.contains(q, ignoreCase = true) ||
                    d.description.contains(q, ignoreCase = true) ||
                    d.category.contains(q, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MenuBg)
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = selectedCategory?.let { "Danh mục: $it" } ?: "Tất cả món ăn",
                        fontWeight = FontWeight.Bold,
                        color = MenuInk
                    )
                    Text(
                        text = "${filteredDishes.size} món ăn phù hợp",
                        style = MaterialTheme.typography.bodySmall,
                        color = MenuMuted
                    )
                }
            },
            windowInsets = WindowInsets(0),
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại", tint = MenuInk)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(bottom = 8.dp)
        ) {
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                placeholder = {
                    Text(
                        text = if (selectedCategory != null) "Tìm món trong $selectedCategory…" else "Tìm nhanh món ăn…",
                        color = MenuMuted
                    )
                },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MenuBlue) },
                trailingIcon = {
                    if (keyword.isNotEmpty()) {
                        IconButton(onClick = { keyword = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Xóa từ khóa", tint = MenuMuted)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MenuBg,
                    unfocusedContainerColor = MenuBg,
                    focusedBorderColor = MenuBlue,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = MenuInk,
                    unfocusedTextColor = MenuInk
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )

            if (resolvedCategories.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(key = "chip-all") {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { onSelectCategory(null) },
                            label = {
                                Text(
                                    text = "Tất cả (${dishes.size})",
                                    fontWeight = if (selectedCategory == null) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MenuBlue,
                                selectedLabelColor = Color.White,
                                containerColor = MenuSoftBlue,
                                labelColor = MenuInk
                            ),
                            border = null
                        )
                    }
                    items(resolvedCategories, key = { "chip-${it.name}" }) { cat ->
                        val isSelected = selectedCategory != null &&
                            selectedCategory.equals(cat.name, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectCategory(cat.name) },
                            label = {
                                Text(
                                    text = "${cat.name} (${cat.dishCount})",
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MenuBlue,
                                selectedLabelColor = Color.White,
                                containerColor = MenuSoftBlue,
                                labelColor = MenuInk
                            ),
                            border = null
                        )
                    }
                }
            }
        }

        if (filteredDishes.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when {
                    loading -> CircularProgressIndicator(color = MenuBlue)
                    failed -> TextButton(onClick = onRetry) { Text("Chưa tải được món · Thử lại", color = MenuBlue) }
                    selectedCategory != null || keyword.isNotBlank() -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Không tìm thấy món phù hợp bộ lọc hiện tại.", color = MenuMuted)
                            OutlinedButton(
                                onClick = {
                                    keyword = ""
                                    onSelectCategory(null)
                                }
                            ) {
                                Text("Xem tất cả món ăn", color = MenuBlue)
                            }
                        }
                    }
                    else -> Text("Chưa có món ăn.", color = MenuMuted)
                }
            }
        } else {
            val visibleDishes = remember(filteredDishes, visibleDishLimit) { filteredDishes.take(visibleDishLimit) }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(visibleDishes, key = { it.id }) { dish ->
                    DeliciousDishItem(dish = dish, onClick = { onDish(dish) })
                }
                if (visibleDishes.size < filteredDishes.size) {
                    item(key = "load-more-dishes", span = { GridItemSpan(maxLineSpan) }) {
                        TextButton(onClick = { visibleDishLimit += 20 }, modifier = Modifier.fillMaxWidth()) { Text("Tải thêm món") }
                    }
                }
            }
        }
    }
}
