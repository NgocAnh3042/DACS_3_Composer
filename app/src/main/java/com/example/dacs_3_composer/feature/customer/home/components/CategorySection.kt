package com.example.dacs_3_composer.feature.customer.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.dacs_3_composer.R
import com.example.dacs_3_composer.feature.customer.home.HomeViewModel
import java.util.Locale

private val CategoryBlue = Color(0xFF2159BC)
private val CategorySoftBlue = Color(0xFFEAF2FA)
private val CategoryBorder = Color(0xFFE6ECF5)
private val CategoryInk = Color(0xFF191C1D)
private val CategoryMuted = Color(0xFF6B7280)

fun fallbackDrawableForCategory(name: String): Int {
    val norm = name.lowercase(Locale.ROOT)
    return when {
        "phở" in norm || "nhật" in norm || "hải sản" in norm -> R.drawable.food_pho_v2
        "trà" in norm || "cà phê" in norm || "giải khát" in norm || "sinh tố" in norm || "chè" in norm -> R.drawable.food_tra_sua_v2
        "bánh" in norm || "ăn vặt" in norm || "khai vị" in norm -> R.drawable.food_banh_mi_v2
        "bún" in norm || "chay" in norm || "hàn" in norm -> R.drawable.food_bun_v2
        else -> R.drawable.food_com_v2
    }
}

@Composable
fun CategorySection(
    categories: List<HomeViewModel.HomeCategory>,
    totalDishCount: Int,
    selectedCategory: String?,
    isLoading: Boolean = false,
    onCategoryClick: (String?) -> Unit,
    onSeeAllClick: () -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Danh mục món ăn",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = CategoryInk
                )
                if (categories.isNotEmpty()) {
                    Text(
                        text = "${categories.size} nhóm món · chạm để lọc nhanh",
                        style = MaterialTheme.typography.bodySmall,
                        color = CategoryMuted
                    )
                }
            }
            TextButton(
                onClick = onSeeAllClick,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Xem tất cả",
                    color = CategoryBlue,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (isLoading && categories.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.5.dp,
                    color = CategoryBlue
                )
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(start = 2.dp, end = 12.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "cat-all") {
                    AllCategoryCard(
                        totalCount = totalDishCount,
                        isSelected = selectedCategory == null,
                        onClick = { onCategoryClick(null) }
                    )
                }
                items(categories, key = { "cat-${it.name}" }) { category ->
                    val isSelected = selectedCategory != null &&
                        selectedCategory.equals(category.name, ignoreCase = true)
                    CategoryItemCard(
                        category = category,
                        isSelected = isSelected,
                        onClick = { onCategoryClick(category.name) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AllCategoryCard(
    totalCount: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.width(92.dp)
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) CategorySoftBlue else Color.White
        ),
        border = BorderStroke(
            width = if (isSelected) 1.8.dp else 1.dp,
            color = if (isSelected) CategoryBlue else CategoryBorder
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 3.dp else 1.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(66.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) CategoryBlue else CategorySoftBlue),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.GridView,
                    contentDescription = "Tất cả danh mục",
                    tint = if (isSelected) Color.White else CategoryBlue,
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(modifier = Modifier.height(7.dp))

            Text(
                text = "Tất cả",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isSelected) CategoryBlue else CategoryInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            if (totalCount > 0) {
                Text(
                    text = "$totalCount món",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) CategoryBlue else CategoryMuted,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun CategoryItemCard(
    category: HomeViewModel.HomeCategory,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.width(96.dp)
) {
    val context = LocalContext.current
    val fallbackRes = fallbackDrawableForCategory(category.name)

    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) CategorySoftBlue else Color.White
        ),
        border = BorderStroke(
            width = if (isSelected) 1.8.dp else 1.dp,
            color = if (isSelected) CategoryBlue else CategoryBorder
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 3.dp else 1.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 8.dp)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(category.imageUrl.ifBlank { fallbackRes })
                    .crossfade(true)
                    .build(),
                contentDescription = "Danh mục ${category.name}",
                placeholder = painterResource(id = fallbackRes),
                error = painterResource(id = fallbackRes),
                modifier = Modifier
                    .size(66.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.height(7.dp))

            Text(
                text = category.name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isSelected) CategoryBlue else CategoryInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                text = "${category.dishCount} món",
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) CategoryBlue else CategoryMuted,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllCategoriesBottomSheet(
    categories: List<HomeViewModel.HomeCategory>,
    totalDishCount: Int,
    selectedCategory: String?,
    onSelectCategory: (String?) -> Unit,
    onOpenFullMenu: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Khám phá danh mục",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = CategoryInk
                    )
                    Text(
                        text = "${categories.size} nhóm món ăn đang mở bán",
                        style = MaterialTheme.typography.bodySmall,
                        color = CategoryMuted
                    )
                }
                FilledTonalButton(
                    onClick = onOpenFullMenu,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = CategorySoftBlue,
                        contentColor = CategoryBlue
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Mở thực đơn", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "sheet-cat-all") {
                    AllCategoryCard(
                        totalCount = totalDishCount,
                        isSelected = selectedCategory == null,
                        onClick = {
                            onSelectCategory(null)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                items(categories, key = { "sheet-cat-${it.name}" }) { cat ->
                    val isSelected = selectedCategory != null &&
                        selectedCategory.equals(cat.name, ignoreCase = true)
                    CategoryItemCard(
                        category = cat,
                        isSelected = isSelected,
                        onClick = {
                            onSelectCategory(cat.name)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
