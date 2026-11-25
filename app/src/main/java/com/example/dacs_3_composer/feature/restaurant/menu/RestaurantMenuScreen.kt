package com.example.dacs_3_composer.feature.restaurant.menu

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dacs_3_composer.core.model.Dish
import com.example.dacs_3_composer.feature.restaurant.menu.components.*

private val DEFAULT_DISH_CATEGORIES = listOf(
    "Món chính",
    "Khai vị",
    "Ăn vặt",
    "Nước giải khát",
    "Tráng miệng",
    "Trà sữa",
    "Cà phê",
    "Đồ uống",
    "Món phụ",
    "Món chay",
    "Đồ nướng",
    "Bánh ngọt",
    "Combo"
)

@Composable
fun RestaurantMenuScreen(
    modifier: Modifier = Modifier,
    viewModel: RestaurantViewModel = viewModel()
) {
    val dishes by viewModel.dishes.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val context = LocalContext.current

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryTab by remember { mutableStateOf("Tất cả") }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingDish by remember { mutableStateOf<Dish?>(null) }

    LaunchedEffect(error) {
        error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    val storeCategories = remember(dishes) {
        dishes.map { it.category.trim() }
            .filter { it.isNotBlank() && !it.equals("Tất cả", ignoreCase = true) }
            .distinctBy { it.lowercase() }
    }

    val categoryTabs = remember(storeCategories) {
        if (storeCategories.isEmpty()) listOf("Tất cả", "Món chính", "Khai vị", "Ăn vặt", "Nước giải khát")
        else listOf("Tất cả") + storeCategories
    }

    LaunchedEffect(categoryTabs) {
        if (categoryTabs.none { it.equals(selectedCategoryTab, ignoreCase = true) }) {
            selectedCategoryTab = "Tất cả"
        }
    }

    val filteredDishes = remember(searchQuery, selectedCategoryTab, dishes) {
        dishes.filter { dish ->
            val matchesSearch = dish.name.contains(searchQuery, ignoreCase = true) ||
                dish.category.contains(searchQuery, ignoreCase = true)
            val matchesCategory = selectedCategoryTab == "Tất cả" ||
                dish.category.trim().equals(selectedCategoryTab, ignoreCase = true)
            matchesSearch && matchesCategory
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            // Đã xóa phần Header theo yêu cầu
            item { ScreenTitle(title = "Quản lý thực đơn", modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) }
            item { SearchBar(query = searchQuery, onQueryChange = { searchQuery = it }, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
            item {
                DynamicCategoryTabs(
                    selectedCategory = selectedCategoryTab,
                    onCategorySelected = { selectedCategoryTab = it },
                    categories = categoryTabs,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }

            if (isLoading && dishes.isEmpty()) {
                item { Box(Modifier.fillMaxWidth().padding(50.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            }

            items(items = filteredDishes, key = { it.id }) { dish ->
                RestaurantDishItem(
                    dishName = if (dish.id.startsWith("pending-")) "${dish.name} · Đang lưu" else dish.name,
                    restaurantName = dish.category.ifBlank { "Món chính" },
                    price = "${dish.price.toLong()}đ",
                    isAvailable = dish.available,
                    imageUrl = dish.imageUrl,
                    onEditClick = { if (!dish.id.startsWith("pending-")) { editingDish = dish; showAddDialog = true } },
                    onDeleteClick = { if (!dish.id.startsWith("pending-")) viewModel.deleteDish(dish.id) },
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                    onToggleAvailability = { isChecked ->
                        if (!dish.id.startsWith("pending-")) viewModel.toggleDishAvailability(dish)
                    }
                )
            }
        }

        FloatingActionButton(
            onClick = { editingDish = null; showAddDialog = true },
            containerColor = Color(0xFF2159BC),
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 16.dp, end = 20.dp)
        ) {
            Icon(Icons.Default.Add, "Thêm món", modifier = Modifier.size(28.dp))
        }

        if (showAddDialog) {
            DishDialog(
                dish = editingDish,
                existingCategories = storeCategories,
                onDismiss = { showAddDialog = false },
                onConfirm = { name, price, cat, desc, uri ->
                    if (editingDish == null) {
                        selectedCategoryTab = "Tất cả"
                        viewModel.addDish(name, price, cat, desc, uri)
                    } else {
                        val updatedWithId = editingDish!!.copy(
                            id = editingDish!!.id,
                            name = name,
                            price = price,
                            category = cat,
                            description = desc
                        )
                        viewModel.updateDish(updatedWithId, uri)
                    }
                    showAddDialog = false
                }
            )
        }
    }
}

@Composable
fun DishDialog(
    dish: Dish?,
    existingCategories: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (String, Double, String, String, Uri?) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(dish?.name ?: "") }
    var price by remember { mutableStateOf(dish?.price?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "") }
    var category by remember { mutableStateOf(dish?.category?.takeIf { it.isNotBlank() } ?: "") }
    var description by remember { mutableStateOf(dish?.description ?: "") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }

    val suggestedCategories = remember(existingCategories) {
        (existingCategories + DEFAULT_DISH_CATEGORIES)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        imageUri = uri
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (dish == null) "Thêm món mới" else "Sửa món ăn") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Tên món *") },
                    placeholder = { Text("VD: Coca-Cola ướp lạnh, Phở bò...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it.take(120) },
                    label = { Text("Danh mục món ăn *") },
                    placeholder = { Text("VD: Nước giải khát, Ăn vặt, Món chính...") },
                    supportingText = {
                        Text(
                            text = "Chọn nhanh bên dưới hoặc tự nhập thể loại mới bất kỳ",
                            fontSize = 11.sp
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(suggestedCategories, key = { it.lowercase() }) { suggestion ->
                        val selected = category.trim().equals(suggestion, ignoreCase = true)
                        FilterChip(
                            selected = selected,
                            onClick = { category = suggestion },
                            label = { Text(suggestion, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF2159BC),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Giá (VNĐ)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Mô tả") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { launcher.launch("image/*") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2159BC)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (imageUri != null) "Đã chọn ảnh mới" else "Chọn ảnh món ăn")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cleanName = name.trim()
                if (cleanName.isBlank()) {
                    Toast.makeText(context, "Vui lòng nhập tên món ăn", Toast.LENGTH_SHORT).show()
                    return@TextButton
                }
                val cleanCategory = category.trim().replace(Regex("\\s+"), " ").ifBlank { "Món chính" }
                onConfirm(cleanName, price.toDoubleOrNull() ?: 0.0, cleanCategory, description.trim(), imageUri)
            }) { Text("Lưu") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
    )
}
