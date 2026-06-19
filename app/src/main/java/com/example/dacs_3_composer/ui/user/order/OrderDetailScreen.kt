package com.example.dacs_3_composer.ui.user.order

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dacs_3_composer.data.model.Order
import com.example.dacs_3_composer.data.model.OrderStatus
import com.example.dacs_3_composer.data.model.Conversation
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

@SuppressLint("NonObservableLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailScreen(
    orderId: String,
    orderViewModel: OrderViewModel,
    onBackClick: () -> Unit,
    onNavigateToPayment: (String, String) -> Unit,
    onNavigateToChat: (String) -> Unit = {}, // 🎯 THÊM: Điều hướng đến Chat
    modifier: Modifier = Modifier
) {
    LaunchedEffect(orderId) {
        orderViewModel.observeOrderDetails(orderId)
    }

    val orderState by orderViewModel.currentTrackingOrder.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chi tiết hóa đơn", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            val order = orderState
            if (order != null && order.status == OrderStatus.PENDING_PAYMENT.name) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    color = Color.White
                ) {
                    Button(
                        onClick = { onNavigateToPayment(order.id, order.totalPrice.toString()) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE67E22)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("THANH TOÁN NGAY", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFFF8F9FA))
                .padding(paddingValues)
        ) {
            val order = orderState
            if (order == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF2159BC))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Mã đơn hàng: #${order.id.takeLast(8).uppercase(Locale.getDefault())}",
                                    fontSize = 14.sp,
                                    color = Color.Gray,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                val statusEnum = try { OrderStatus.valueOf(order.status) } catch (e: Exception) { null }
                                val statusText = statusEnum?.displayName ?: order.status
                                val statusColor = when (order.status) {
                                    "PENDING" -> Color(0xFFFF9800)
                                    "PROCESSING" -> Color(0xFF00B0FF)
                                    "ACCEPTED" -> Color(0xFF9C27B0)
                                    "SHIPPING" -> Color(0xFF2159BC)
                                    "COMPLETED" -> Color(0xFF4CAF50)
                                    "CANCELLED" -> Color(0xFFDC3545)
                                    else -> Color.Gray
                                }

                                Surface(
                                    color = statusColor.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = statusText,
                                        color = statusColor,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Thời gian đặt: ${order.time}",
                                    fontSize = 13.sp,
                                    color = Color(0xFF727785)
                                )
                            }
                        }
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = "Liên hệ & Giao hàng", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                    
                                    // 🎯 Nút nhắn tin cho Nhà hàng
                                    TextButton(onClick = { 
                                        startChatWithPartner(order.restaurantId, "restaurant", onNavigateToChat) 
                                    }) {
                                        Icon(Icons.AutoMirrored.Filled.Chat, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Chat với Quán", fontSize = 12.sp)
                                    }
                                }
                                
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color(0xFFEEEEEE))

                                if (order.shipperId.isNotBlank()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Person, null, tint = Color(0xFF2159BC), modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(text = "Tài xế đang giao", fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                        
                                        // 🎯 Nút nhắn tin cho Shipper
                                        IconButton(onClick = { 
                                            startChatWithPartner(order.shipperId, "shipper", onNavigateToChat) 
                                        }) {
                                            Icon(Icons.AutoMirrored.Filled.Chat, null, tint = Color(0xFF2159BC), modifier = Modifier.size(20.dp))
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Person, null, tint = Color(0xFF2159BC), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = order.customerName, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Phone, null, tint = Color(0xFF2159BC), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = order.customerPhone, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.DarkGray)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(Icons.Default.LocationOn, null, tint = Color.Red, modifier = Modifier.size(18.dp).padding(top = 2.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = order.customerAddress, fontSize = 14.sp, color = Color.DarkGray, lineHeight = 20.sp)
                                }
                            }
                        }
                    }

                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
                            Icon(Icons.Default.ReceiptLong, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Chi tiết món ăn đã đặt", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        }
                    }

                    items(order.items) { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = item.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Đơn giá: ${String.format(Locale("vi", "VN"), "%,.0fđ", item.price)}",
                                        fontSize = 13.sp,
                                        color = Color.Gray
                                    )
                                }
                                Text(text = "x${item.quantity}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray, modifier = Modifier.padding(horizontal = 16.dp))
                                Text(text = String.format(Locale("vi", "VN"), "%,.0fđ", item.price * item.quantity), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                            }
                        }
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(text = "Tóm tắt thanh toán", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                HorizontalDivider(color = Color(0xFFF5F5F5), thickness = 1.dp)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Tổng giá gốc món ăn", color = Color.DarkGray, fontSize = 14.sp)
                                    Text(String.format(Locale("vi", "VN"), "%,.0f đ", order.totalDishPrice), fontSize = 14.sp)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Phí giao hàng", color = Color.DarkGray, fontSize = 14.sp)
                                    Text("+ ${String.format(Locale("vi", "VN"), "%,.0f đ", order.shippingFee)}", fontSize = 14.sp)
                                }
                                if (!order.appliedPromotionId.isNullOrBlank() || order.promotionDiscount > 0) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column {
                                            Text("Khuyến mãi áp dụng", color = Color.DarkGray, fontSize = 14.sp)
                                            Text(text = "(${order.appliedPromotionTitle.ifBlank { "Voucher" }})", fontSize = 12.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Medium)
                                        }
                                        Text(text = "- ${String.format(Locale("vi", "VN"), "%,.0f đ", order.promotionDiscount)}", fontSize = 14.sp, color = Color.Red, fontWeight = FontWeight.Medium)
                                    }
                                }
                                HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Tổng thanh toán thực tế", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text(text = String.format(Locale("vi", "VN"), "%,.0f đ", order.totalPrice), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2159BC))
                                }
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }
}

// Hàm bổ trợ để bắt đầu chat
private fun startChatWithPartner(partnerId: String, partnerRole: String, onNavigate: (String) -> Unit) {
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
            
            if (existing != null) {
                onNavigate(existing.id)
            } else {
                val newDoc = db.collection("conversations").document()
                val conv = Conversation(
                    id = newDoc.id,
                    participants = listOf(currentUid, partnerId),
                    participantRoles = mapOf(currentUid to "user", partnerId to partnerRole),
                    lastMessage = "Xin chào!",
                    lastMessageTime = com.google.firebase.Timestamp.now()
                )
                newDoc.set(conv).addOnSuccessListener { onNavigate(newDoc.id) }
            }
        }
}
