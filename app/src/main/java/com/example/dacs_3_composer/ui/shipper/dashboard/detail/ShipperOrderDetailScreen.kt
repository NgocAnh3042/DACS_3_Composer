package com.example.dacs_3_composer.ui.shipper.dashboard.detail

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
import com.google.firebase.firestore.FirebaseFirestore
import com.example.dacs_3_composer.data.model.Order
import com.example.dacs_3_composer.data.model.OrderItem
import com.example.dacs_3_composer.data.model.OrderStatus
import com.example.dacs_3_composer.data.model.Conversation
import com.example.dacs_3_composer.ui.shipper.dashboard.detail.components.*

@Composable
fun ShipperOrderDetailScreen(
    orderId: String,
    onBackClick: () -> Unit,
    onNavigateToChat: (String) -> Unit = {} // 🎯 THÊM: Callback điều hướng chat
) {
    val firestore = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val currentShipperId = remember { auth.currentUser?.uid ?: "" }

    var orderState by remember { mutableStateOf<Order?>(null) }
    var isUpdating by remember { mutableStateOf(false) }

    LaunchedEffect(orderId) {
        if (orderId.isBlank()) return@LaunchedEffect
        val docRef = firestore.collection("orders").document(orderId)
        docRef.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
            try {
                val itemsRaw = snapshot.get("items") as? List<Map<String, Any>>
                val parsedItems = itemsRaw?.map { map ->
                    OrderItem(
                        dishId = map["dishId"] as? String ?: "",
                        name = map["name"] as? String ?: "",
                        quantity = (map["quantity"] as? Long)?.toInt() ?: 0,
                        price = (map["price"] as? Double) ?: ((map["price"] as? Long)?.toDouble() ?: 0.0)
                    )
                } ?: emptyList()

                orderState = Order(
                    id = snapshot.id,
                    time = snapshot.getString("time") ?: "",
                    status = snapshot.getString("status") ?: "PENDING",
                    paymentMethod = snapshot.getString("paymentMethod") ?: "CASH",
                    totalDishPrice = snapshot.getDouble("totalDishPrice") ?: 0.0,
                    shippingFee = snapshot.getDouble("shippingFee") ?: 20000.0,
                    totalPrice = snapshot.getDouble("totalPrice") ?: 0.0,
                    userId = snapshot.getString("userId") ?: "",
                    customerName = snapshot.getString("customerName") ?: "",
                    customerPhone = snapshot.getString("customerPhone") ?: "",
                    customerAddress = snapshot.getString("customerAddress") ?: "",
                    restaurantId = snapshot.getString("restaurantId") ?: "",
                    restaurantName = snapshot.getString("restaurantName") ?: "",
                    shipperId = snapshot.getString("shipperId") ?: "",
                    items = parsedItems
                )
            } catch (e: Exception) { }
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
                                    firestore.collection("orders").document(orderId).update("shipperId", currentShipperId)
                                        .addOnSuccessListener { isUpdating = false }
                                        .addOnFailureListener { isUpdating = false }
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
                                        firestore.collection("orders").document(orderId).update("status", OrderStatus.SHIPPING.name)
                                            .addOnSuccessListener { isUpdating = false }
                                            .addOnFailureListener { isUpdating = false }
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
                                        firestore.collection("orders").document(orderId).update("status", OrderStatus.COMPLETED.name)
                                            .addOnSuccessListener { isUpdating = false; onBackClick() }
                                            .addOnFailureListener { isUpdating = false }
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
            LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                item { MapRouteSection() }
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
                                    IconButton(onClick = { startChat(order.userId, "user", onNavigateToChat) }) {
                                        Icon(Icons.AutoMirrored.Filled.Chat, "Chat Khách", tint = Color(0xFF1E40AF))
                                    }
                                    // Chat với Nhà hàng
                                    IconButton(onClick = { startChat(order.restaurantId, "restaurant", onNavigateToChat) }) {
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

private fun startChat(partnerId: String, partnerRole: String, onNavigate: (String) -> Unit) {
    if (partnerId.isBlank()) return
    val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val db = FirebaseFirestore.getInstance()
    
    db.collection("conversations")
        .whereArrayContains("participants", currentUid)
        .get()
        .addOnSuccessListener { snapshot ->
            val existing = snapshot.documents.find { doc ->
                val participants = doc.get("participants") as? List<*>
                participants?.contains(partnerId) == true && doc.getString("type") != "SUPPORT"
            }
            if (existing != null) onNavigate(existing.id)
            else {
                val newDoc = db.collection("conversations").document()
                val conv = Conversation(
                    id = newDoc.id,
                    participants = listOf(currentUid, partnerId),
                    participantRoles = mapOf(currentUid to "shipper", partnerId to partnerRole),
                    lastMessage = "Xin chào, tôi là tài xế giao đơn hàng của bạn.",
                    lastMessageTime = com.google.firebase.Timestamp.now()
                )
                newDoc.set(conv).addOnSuccessListener { onNavigate(newDoc.id) }
            }
        }
}
