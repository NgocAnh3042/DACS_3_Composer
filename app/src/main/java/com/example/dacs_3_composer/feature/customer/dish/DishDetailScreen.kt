package com.example.dacs_3_composer.feature.customer.dish

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.dacs_3_composer.R
import com.example.dacs_3_composer.core.model.Dish
import com.example.dacs_3_composer.core.model.DishItem
import com.example.dacs_3_composer.core.model.RestaurantDetail
import com.example.dacs_3_composer.data.remote.FoodHubClient
import com.example.dacs_3_composer.feature.customer.cart.CartViewModel
import com.example.dacs_3_composer.feature.customer.home.components.DeliciousDishItem
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

private val FoodHubBlue = Color(0xFF2159BC)
private val SoftBg = Color(0xFFF5F7FA)
private val InkDark = Color(0xFF191C1D)
private val MutedText = Color(0xFF6B7280)

private fun formatVnd(amount: Double): String {
    val nf = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"))
    return "${nf.format(amount)} đ"
}

@Composable
fun DishDetailScreen(
    dishId: String,
    initialDish: Dish?,
    initialRestaurant: RestaurantDetail?,
    relatedStoreDishes: List<Dish>,
    cartViewModel: CartViewModel,
    onBackClick: () -> Unit,
    onOpenRestaurant: (String) -> Unit,
    onOpenDish: (String) -> Unit,
    onViewCartClick: () -> Unit
) {
    var dish by remember(dishId) { mutableStateOf(initialDish) }
    var restaurantName by remember(dishId) { mutableStateOf(initialRestaurant?.name.orEmpty()) }
    var restaurantAddress by remember(dishId) { mutableStateOf(initialRestaurant?.address.orEmpty()) }
    var otherDishes by remember(dishId) { mutableStateOf(relatedStoreDishes) }
    var isLoading by remember(dishId) { mutableStateOf(initialDish == null) }
    var errorMessage by remember(dishId) { mutableStateOf<String?>(null) }

    var quantity by remember(dishId) { mutableIntStateOf(1) }
    var showSwitchRestaurantDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Tải bổ sung dữ liệu chi tiết món & quán (tương thích cả khi server chưa bật GET dishes/{id})
    LaunchedEffect(dishId) {
        errorMessage = null
        try {
            val detailResp = runCatching { FoodHubClient.api.get("dishes", dishId) }.getOrNull()
            val detailData = if (detailResp != null && detailResp.isSuccessful) {
                detailResp.body()?.getAsJsonObject("data")
            } else null

            if (detailData != null) {
                dish = FoodHubClient.gson.fromJson(detailData, Dish::class.java)
                val rName = detailData.get("restaurantName")?.asString.orEmpty()
                val rAddr = detailData.get("restaurantAddress")?.asString.orEmpty()
                if (rName.isNotBlank()) restaurantName = rName
                if (rAddr.isNotBlank()) restaurantAddress = rAddr
            } else if (dish == null) {
                val allDishes = FoodHubClient.list<Dish>("dishes")
                dish = allDishes.firstOrNull { it.id == dishId }
            }

            val resolvedDish = dish
            if (resolvedDish == null) {
                errorMessage = "Không tìm thấy thông tin món ăn này."
            } else {
                if (restaurantName.isBlank() && resolvedDish.restaurantId.isNotBlank()) {
                    runCatching {
                        val store = FoodHubClient.get<RestaurantDetail>("stores", resolvedDish.restaurantId)
                        restaurantName = store.name
                        restaurantAddress = store.address
                    }
                }
                if (otherDishes.isEmpty() && resolvedDish.restaurantId.isNotBlank()) {
                    runCatching {
                        val storeDishes = FoodHubClient.list<Dish>(
                            "dishes",
                            mapOf("restaurantId" to resolvedDish.restaurantId)
                        )
                        otherDishes = storeDishes.filter { it.id != resolvedDish.id }
                    }
                }
            }
        } catch (e: Exception) {
            if (dish == null) {
                errorMessage = "Chưa tải được thông tin món ăn. Vui lòng thử lại."
            }
        } finally {
            isLoading = false
        }
    }

    val totalCartQuantity = cartViewModel.cartItems.sumOf { it.quantity }

    fun performAddToCart(target: Dish, qty: Int, clearExisting: Boolean) {
        if (clearExisting) {
            cartViewModel.clearCart()
        }
        val resolvedRestaurantName = restaurantName.ifBlank { "Nhà hàng đối tác" }
        val dishItem = DishItem(
            id = target.id,
            name = target.name,
            price = target.price,
            description = target.description,
            imageUrl = target.imageUrl,
            category = target.category,
            isAvailable = target.available,
            restaurantId = target.restaurantId,
            restaurantName = resolvedRestaurantName
        )
        cartViewModel.addToCart(dishItem, qty)
        scope.launch {
            val res = snackbarHostState.showSnackbar(
                message = "Đã thêm $qty phần ${target.name} vào giỏ hàng",
                actionLabel = "Xem giỏ",
                duration = SnackbarDuration.Short
            )
            if (res == SnackbarResult.ActionPerformed) {
                onViewCartClick()
            }
        }
    }

    if (showSwitchRestaurantDialog && dish != null) {
        AlertDialog(
            onDismissRequest = { showSwitchRestaurantDialog = false },
            title = { Text("Đổi nhà hàng trong giỏ?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Giỏ hàng hiện đang có món từ quán khác. Bạn có muốn xóa giỏ cũ để thêm ${dish!!.name} không?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSwitchRestaurantDialog = false
                        performAddToCart(dish!!, quantity, clearExisting = true)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FoodHubBlue)
                ) {
                    Text("Đồng ý")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSwitchRestaurantDialog = false }) {
                    Text("Hủy")
                }
            }
        )
    }

    Scaffold(
        containerColor = SoftBg,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            val currentDish = dish
            if (currentDish != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    shadowElevation = 10.dp,
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .navigationBarsPadding(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Bộ chọn số lượng
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFFF1F5FB)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                IconButton(
                                    onClick = { if (quantity > 1) quantity-- },
                                    enabled = quantity > 1,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Remove,
                                        contentDescription = "Giảm số lượng",
                                        tint = if (quantity > 1) FoodHubBlue else Color.Gray
                                    )
                                }
                                Text(
                                    text = "$quantity",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = InkDark,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                                IconButton(
                                    onClick = { if (quantity < 99) quantity++ },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Add,
                                        contentDescription = "Tăng số lượng",
                                        tint = FoodHubBlue
                                    )
                                }
                            }
                        }

                        // Nút thêm vào giỏ hàng
                        Button(
                            onClick = {
                                val hasOtherStore = cartViewModel.cartItems.isNotEmpty() &&
                                    cartViewModel.currentRestaurantId.isNotBlank() &&
                                    currentDish.restaurantId.isNotBlank() &&
                                    cartViewModel.currentRestaurantId != currentDish.restaurantId
                                if (hasOtherStore) {
                                    showSwitchRestaurantDialog = true
                                } else {
                                    performAddToCart(currentDish, quantity, clearExisting = false)
                                }
                            },
                            enabled = currentDish.available,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = FoodHubBlue)
                        ) {
                            val total = currentDish.price * quantity
                            Text(
                                text = if (currentDish.available) "Thêm · ${formatVnd(total)}" else "Tạm hết món",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        if (isLoading && dish == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = FoodHubBlue)
            }
        } else if (dish == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = errorMessage ?: "Không tìm thấy món ăn.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MutedText
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onBackClick,
                    colors = ButtonDefaults.buttonColors(containerColor = FoodHubBlue),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Quay lại")
                }
            }
        } else {
            val currentDish = dish!!
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Hero image + nút Back & Giỏ hàng nổi
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(275.dp)) {
                        AsyncImage(
                            model = currentDish.imageUrl.ifBlank { R.drawable.food_pho_v2 },
                            contentDescription = currentDish.name,
                            placeholder = painterResource(id = R.drawable.food_pho_v2),
                            error = painterResource(id = R.drawable.food_pho_v2),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                onClick = onBackClick,
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.92f),
                                shadowElevation = 4.dp,
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Quay lại",
                                        tint = InkDark
                                    )
                                }
                            }

                            Surface(
                                onClick = onViewCartClick,
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.92f),
                                shadowElevation = 4.dp,
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    BadgedBox(
                                        badge = {
                                            if (totalCartQuantity > 0) {
                                                Badge(containerColor = FoodHubBlue) {
                                                    Text("$totalCartQuantity", color = Color.White)
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            Icons.Filled.ShoppingCart,
                                            contentDescription = "Giỏ hàng",
                                            tint = FoodHubBlue
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Thẻ thông tin chính của món ăn (Profile món ăn)
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(22.dp),
                        color = Color.White,
                        shadowElevation = 1.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (currentDish.category.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFFE8F0FE)
                                    ) {
                                        Text(
                                            text = currentDish.category,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = FoodHubBlue,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (currentDish.available) Color(0xFFE6F4EA) else Color(0xFFFCE8E6)
                                ) {
                                    Text(
                                        text = if (currentDish.available) "Đang phục vụ" else "Tạm hết món",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (currentDish.available) Color(0xFF137333) else Color(0xFFC5221F),
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Text(
                                text = currentDish.name,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = InkDark
                            )

                            Text(
                                text = formatVnd(currentDish.price),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = FoodHubBlue
                            )

                            HorizontalDivider(color = Color(0xFFEDF1F7))

                            Text(
                                text = "Mô tả món ăn",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = InkDark
                            )
                            Text(
                                text = currentDish.description.ifBlank {
                                    "Quán chưa bổ sung mô tả chi tiết cho món này."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF465568),
                                lineHeight = 22.sp
                            )
                        }
                    }
                }

                // 3. Thẻ nhà hàng cung cấp món (bấm để xem toàn bộ thực đơn quán khi cần)
                if (currentDish.restaurantId.isNotBlank()) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .clickable {
                                    cartViewModel.currentRestaurantId = currentDish.restaurantId
                                    cartViewModel.currentRestaurantName = restaurantName
                                    onOpenRestaurant(currentDish.restaurantId)
                                },
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White,
                            shadowElevation = 1.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFFE8F0FE)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.Storefront,
                                        contentDescription = null,
                                        tint = FoodHubBlue,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = restaurantName.ifBlank { "Nhà hàng đối tác" },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = InkDark,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = restaurantAddress.ifBlank { "Xem toàn bộ thực đơn của quán" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MutedText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Xem quán",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = FoodHubBlue,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = FoodHubBlue
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. Các món khác cùng quán
                if (otherDishes.isNotEmpty()) {
                    item {
                        Text(
                            text = "Món khác từ quán này",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = InkDark,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(otherDishes.take(10), key = { "same-store-" + it.id }) { related ->
                                DeliciousDishItem(
                                    dish = related,
                                    modifier = Modifier.width(166.dp)
                                ) {
                                    onOpenDish(related.id)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
