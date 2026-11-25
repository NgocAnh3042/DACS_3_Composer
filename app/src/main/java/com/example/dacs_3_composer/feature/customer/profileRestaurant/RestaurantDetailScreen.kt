package com.example.dacs_3_composer.feature.customer.profileRestaurant

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.dacs_3_composer.core.model.DishItem
import com.example.dacs_3_composer.feature.customer.profileRestaurant.components.DishRowUserItem
import com.example.dacs_3_composer.feature.customer.profileRestaurant.components.RestaurantInfoUserCard
import com.example.dacs_3_composer.feature.customer.cart.presentation.CartViewModel
import com.example.dacs_3_composer.data.repository.ChatRepository
import com.example.dacs_3_composer.feature.shared.location.DeliveryEstimate
import com.example.dacs_3_composer.feature.shared.location.rememberUserCoordinates
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitCancellation

@Composable
fun RestaurantDetailScreen(
    restaurantId: String,
    onBackClick: () -> Unit,
    onAddToCart: (DishItem) -> Unit,
    onViewCartClick: () -> Unit,
    onNavigateToChat: (String) -> Unit = {}, // 🎯 THÊM: Callback đi đến chat
    viewModel: UserStoreViewModel = viewModel(),
    cartViewModel: CartViewModel = viewModel()
) {
    val coroutineScope = rememberCoroutineScope()
    val userCoords = rememberUserCoordinates()

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, restaurantId) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.fetchStoreDataForUser(restaurantId)
            try { awaitCancellation() } finally { viewModel.stopStoreDataObservation() }
        }
    }

    val rawRestaurant = viewModel.restaurantDetail.value
    val restaurant = remember(rawRestaurant, userCoords) {
        rawRestaurant?.let { DeliveryEstimate.enrichRestaurant(it, userCoords?.first, userCoords?.second) }
    }
    val dishes = viewModel.dishesList.value

    LaunchedEffect(restaurant) {
        if (restaurant != null) {
            cartViewModel.setCurrentRestaurant(restaurant.id, restaurant.name)
        }
    }

    val cartUiState by cartViewModel.uiState.collectAsStateWithLifecycle()
    val cartItems = cartUiState.cartItems
    val totalQuantity = cartItems.sumOf { it.quantity }
    val totalPrice = cartViewModel.getTotalPrice()

    Scaffold(
        bottomBar = {
            if (totalQuantity > 0) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    color = Color.White
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .navigationBarsPadding(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(Color(0xFF1E56A0), RoundedCornerShape(6.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$totalQuantity",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "món đã chọn", fontSize = 14.sp, color = Color.Gray)
                            }

                            Text(
                                text = "${String.format("%,.0f", totalPrice)} đ",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E56A0),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        Button(
                            onClick = onViewCartClick,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E56A0)),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            Text(text = "Tiếp tục", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        if (restaurant == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF1E56A0))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Color(0xFFF8F9FA))
            ) {
                if (viewModel.refreshFailed) {
                    item(key = "cached-data-notice") {
                        Text("Chưa thể làm mới; đang giữ thực đơn đã lưu.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    }
                }
                item {
                    Box(modifier = Modifier.height(220.dp).fillMaxWidth()) {
                        AsyncImage(
                            model = restaurant.coverImage,
                            contentDescription = "Ảnh bìa nhà hàng",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = onBackClick,
                                modifier = Modifier
                                    .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.Black)
                            }

                            // 🎯 THÊM: Nút Chat với Nhà hàng
                            IconButton(
                                onClick = {
                                    coroutineScope.launch { runCatching { ChatRepository().createConversation(restaurant.id, "restaurant") }.onSuccess(onNavigateToChat) }
                                },
                                modifier = Modifier
                                    .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Chat", tint = Color(0xFF1E56A0))
                            }
                        }
                    }
                }

                item {
                    RestaurantInfoUserCard(restaurant = restaurant)
                }

                item {
                    Text(
                        text = "Thực đơn cửa hàng",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1A1A),
                        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 12.dp)
                    )
                }

                items(dishes, key = { it.id }) { dish ->
                    DishRowUserItem(
                        dish = dish,
                        onAddToCartClick = { onAddToCart(dish) }
                    )
                }
            }
        }
    }
}
