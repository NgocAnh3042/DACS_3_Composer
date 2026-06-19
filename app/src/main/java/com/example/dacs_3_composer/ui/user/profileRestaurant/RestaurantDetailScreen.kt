package com.example.dacs_3_composer.ui.user.profileRestaurant

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.dacs_3_composer.data.model.DishItem
import com.example.dacs_3_composer.ui.user.profileRestaurant.components.DishRowUserItem
import com.example.dacs_3_composer.ui.user.profileRestaurant.components.RestaurantInfoUserCard
import com.example.dacs_3_composer.ui.user.cart.CartViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.dacs_3_composer.data.model.Conversation

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
    
    LaunchedEffect(restaurantId) {
        viewModel.fetchStoreDataForUser(restaurantId)
    }

    val restaurant = viewModel.restaurantDetail.value
    val dishes = viewModel.dishesList.value

    LaunchedEffect(restaurant) {
        if (restaurant != null) {
            cartViewModel.currentRestaurantId = restaurant.id
            cartViewModel.currentRestaurantName = restaurant.name
        }
    }

    val cartItems = cartViewModel.cartItems
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
                                    startChatWithRestaurant(restaurant.id, restaurant.name, onNavigateToChat)
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

                items(dishes) { dish ->
                    DishRowUserItem(
                        dish = dish,
                        onAddToCartClick = { onAddToCart(dish) }
                    )
                }
            }
        }
    }
}

// Logic khởi tạo chat giống Repository nhưng đặt tại UI để tiện điều hướng
private fun startChatWithRestaurant(resId: String, resName: String, onNavigate: (String) -> Unit) {
    val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val db = FirebaseFirestore.getInstance()
    
    db.collection("conversations")
        .whereArrayContains("participants", currentUid)
        .get()
        .addOnSuccessListener { snapshot ->
            val existing = snapshot.documents.find { doc ->
                val participants = doc.get("participants") as? List<*>
                participants?.contains(resId) == true && doc.getString("type") != "SUPPORT"
            }
            
            if (existing != null) {
                onNavigate(existing.id)
            } else {
                val newDoc = db.collection("conversations").document()
                val conv = Conversation(
                    id = newDoc.id,
                    participants = listOf(currentUid, resId),
                    participantRoles = mapOf(currentUid to "user", resId to "restaurant"),
                    lastMessage = "Chào bạn! Tôi muốn hỏi về thực đơn.",
                    lastMessageTime = com.google.firebase.Timestamp.now()
                )
                newDoc.set(conv).addOnSuccessListener { onNavigate(newDoc.id) }
            }
        }
}
