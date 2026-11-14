package com.example.dacs_3_composer.feature.shipper.dashboard.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.AddCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.example.dacs_3_composer.data.remote.FoodHubClient
import com.example.dacs_3_composer.data.repository.ChatRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.dacs_3_composer.core.model.Order
import com.example.dacs_3_composer.core.model.OrderItem
import com.example.dacs_3_composer.core.model.OrderStatus
import com.example.dacs_3_composer.core.model.Conversation
import com.example.dacs_3_composer.feature.shipper.dashboard.detail.components.*

@Composable
fun ShipperOrderDetailScreen(
    orderId: String,
    onBackClick: () -> Unit,
    onNavigateToChat: (String) -> Unit = {} // 🎯 THÊM: Callback điều hướng chat
) {
    val auth = FirebaseAuth.getInstance()
    val scope = rememberCoroutineScope()
    val currentShipperId = remember { auth.currentUser?.uid ?: "" }

    var orderState by remember { mutableStateOf<Order?>(null) }
    var isUpdating by remember { mutableStateOf(false) }
    var touchingMap by remember { mutableStateOf(false) }

    LaunchedEffect(orderId) {
        if (orderId.isBlank()) return@LaunchedEffect
        while (true) {
            runCatching { FoodHubClient.get<Order>("orders", orderId) }.onSuccess { orderState = it }
            delay(4000)
        }
    }

    val order = orderState

    if (order == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF2563EB))
        }
    } else {
        Scaffold(
            topBar = { OrderDetailTopBar(onBackClick = onBackClick) },
            bottomBar = {
                if (order.status != OrderStatus.COMPLETED.name && order.status != OrderStatus.CANCELLED.name) {
                    Box(modifier = Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp, vertical = 12.dp)) {
                        if (order.shipperId.isBlank()) {
                            Button(
                                onClick = {
                                    isUpdating = true
                                    scope.launch { isUpdating=true; runCatching { FoodHubClient.api.create("deliveries/$orderId/accept", com.google.gson.JsonObject()) }; isUpdating=false }
                                },
                                enabled = !isUpdating,
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.AddCard, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "Nhận giao đơn hàng này", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        else if (order.shipperId == currentShipperId) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = {
                                        isUpdating = true
                                        scope.launch { isUpdating=true; runCatching { FoodHubClient.update("orders", orderId, mapOf("status" to OrderStatus.SHIPPING.name)) }; isUpdating=false }
                                    },
                                    enabled = !isUpdating && order.status == OrderStatus.ACCEPTED.name,
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEFF6FF), contentColor = Color(0xFF2563EB)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = "Đã lấy hàng", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        isUpdating = true
                                        scope.launch { isUpdating=true; runCatching { FoodHubClient.update("orders", orderId, mapOf("status" to OrderStatus.COMPLETED.name)) }; isUpdating=false; onBackClick() }
                                    },
                                    enabled = !isUpdating && order.status == OrderStatus.SHIPPING.name,
                                    modifier = Modifier.weight(1.2f).height(48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2ECC71)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.LocalShipping, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = "Giao xong", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }
                }
            },
            containerColor = Color(0xFFF8F9FA)
        ) { innerPadding ->
            LazyColumn(userScrollEnabled = !touchingMap, modifier = Modifier.fillMaxSize().padding(innerPadding), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                item { MapRouteSection(order = order, enableTracking = order.shipperId == currentShipperId, onMapTouchChange = { touchingMap = it }) }
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

                        // 🎯 CẬP NHẬT: Thêm khu vực Chat nhanh cho Shipper
                        if (order.shipperId == currentShipperId) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFE0E7FF))
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Liên hệ hỗ trợ đơn:", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Color(0xFF1E40AF))

                                    // Chat với Khách hàng
                                    IconButton(onClick = { scope.launch { runCatching { ChatRepository().createConversation(order.userId, "user", order.id) }.onSuccess(onNavigateToChat) } }) {
                                        Icon(Icons.AutoMirrored.Filled.Chat, "Chat Khách", tint = Color(0xFF1E40AF))
                                    }
                                    // Chat với Nhà hàng
                                    IconButton(onClick = { scope.launch { runCatching { ChatRepository().createConversation(order.restaurantId, "restaurant", order.id) }.onSuccess(onNavigateToChat) } }) {
                                        Icon(Icons.AutoMirrored.Filled.Chat, "Chat Quán", tint = Color(0xFFD97706))
                                    }
                                }
                            }
                        }

                        CustomerInfoCard(
                            customerName = order.customerName,
                            customerPhone = order.customerPhone,
                            customerAddress = order.customerAddress,
                            restaurantName = order.restaurantName
                        )

                        ItemsBillCard(
                            orderId = order.id,
                            items = order.items,
                            totalPrice = order.totalPrice,
                            shippingFee = order.shippingFee,
                            paymentMethod = order.paymentMethod
                        )
                        CustomerNoteBox(noteText = "Vui lòng gọi điện trước khi giao hàng.")
                    }
                }
            }
        }
    }
}
