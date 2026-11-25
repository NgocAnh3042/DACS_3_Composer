package com.example.dacs_3_composer.feature.customer.order.presentation

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import com.example.dacs_3_composer.core.ui.FoodHubPullToRefresh
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dacs_3_composer.feature.customer.order.components.EmptyOrderState
import com.example.dacs_3_composer.feature.customer.order.components.HistoryOrderItem
import com.example.dacs_3_composer.feature.customer.order.components.OngoingOrderItem
import com.example.dacs_3_composer.feature.customer.order.components.OrderTabBar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderScreen(
    suggestedRestaurants: List<com.example.dacs_3_composer.core.model.Restaurant>,
    chuyenHienThiTrangChiTiet: (String) -> Unit,
    onNavigateToPayment: (String, Double) -> Unit = { _, _ -> },
    onNavigateToChat: () -> Unit = {}, // 🎯 THÊM: Callback nhắn tin
    orderViewModel: OrderViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var visibleOrderLimit by remember(selectedTabIndex) { mutableIntStateOf(20) }
    val context = LocalContext.current

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, orderViewModel) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { orderViewModel.observeUserOrders() }
    }
    val ongoingOrders by orderViewModel.ongoingOrders.collectAsState()
    val historyOrders by orderViewModel.historyOrders.collectAsState()
    val isLoading by orderViewModel.isLoading.collectAsState()
    val isRefreshing by orderViewModel.isRefreshing.collectAsState()
    val refreshFailed by orderViewModel.refreshFailed.collectAsState()
    val pendingOrders = remember(ongoingOrders) { ongoingOrders.filter { it.status.equals("PENDING_PAYMENT", true) || it.status.equals("PENDING", true) } }
    val activeOrders = remember(ongoingOrders) { ongoingOrders.filter { it.status.trim().uppercase() in setOf("PROCESSING", "ACCEPTED", "SHIPPING") } }
    val visiblePendingOrders = remember(pendingOrders, visibleOrderLimit) { pendingOrders.take(visibleOrderLimit) }
    val visibleActiveOrders = remember(activeOrders, visibleOrderLimit) { activeOrders.take(visibleOrderLimit) }
    val visibleHistoryOrders = remember(historyOrders, visibleOrderLimit) { historyOrders.take(visibleOrderLimit) }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0.dp),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Đơn hàng", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF191C1D))
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // 🎯 THÊM: Icon Chat cho đồng bộ
                            IconButton(onClick = onNavigateToChat) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.Chat, contentDescription = "Tin nhắn", tint = Color(0xFF191C1D))
                            }
                            IconButton(onClick = { }) {
                                Icon(imageVector = Icons.Default.Search, contentDescription = "Tìm kiếm", tint = Color(0xFF191C1D), modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        // ... (Giữ nguyên toàn bộ nội dung bên dưới)
        Box(modifier = modifier.fillMaxSize().background(Color(0xFFF8F9FA)).padding(paddingValues)) {
            Column(modifier = Modifier.fillMaxSize()) {
                OrderTabBar(selectedTabIndex = selectedTabIndex, onTabSelected = { selectedTabIndex = it })

                if (isLoading) {
                    val transition = rememberInfiniteTransition(label = "order-skeleton")
                    val alpha by transition.animateFloat(0.35f, 0.75f, infiniteRepeatable(tween(750), RepeatMode.Reverse), label = "order-skeleton-alpha")
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(3, key = { "order-skeleton-$it" }) {
                            Card(modifier = Modifier.fillMaxWidth().height(136.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Box(Modifier.fillMaxSize().padding(16.dp).background(Color(0xFFE7ECF3).copy(alpha = alpha), RoundedCornerShape(12.dp)))
                            }
                        }
                    }
                } else {
                    FoodHubPullToRefresh(
                        refreshing = isRefreshing,
                        onRefresh = { orderViewModel.refreshOrders() },
                        modifier = Modifier.fillMaxSize()
                    ) {
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (refreshFailed) item { Text("Chưa thể làm mới; đang giữ dữ liệu đã lưu.", color = Color.Gray, style = MaterialTheme.typography.bodySmall) }
                        when (selectedTabIndex) {
                            0 -> { 
                                if (pendingOrders.isEmpty()) {
                                    item { EmptyOrderState() }
                                } else {
                                    items(visiblePendingOrders, key = { it.id }) { order ->
                                        val itemsSummaryText = order.items.joinToString(", ") { "${it.quantity}x ${it.name}" }
                                        val imgUrl = order.restaurantCoverImage
                                        val currentStatus = order.status.uppercase().trim()

                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(20.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color.White),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(16.dp)) {
                                                val statusMsg = when(currentStatus) {
                                                    "PENDING_PAYMENT" -> "đang chờ bạn thanh toán qua PayOS"
                                                    "PENDING" -> "đang chờ nhà hàng xác nhận"
                                                    else -> "Đang xử lý..."
                                                }
                                                OngoingOrderItem(
                                                    restaurantName = order.restaurantName,
                                                    statusText = statusMsg,
                                                    estimatedTime = "Mã đơn: ..${order.id.takeLast(6).uppercase()}",
                                                    itemsSummary = itemsSummaryText,
                                                    restaurantImageUrl = imgUrl,
                                                    paymentMethod = order.paymentMethod,
                                                    chuyenTheoDoiDonHang = { chuyenHienThiTrangChiTiet(order.id) }
                                                )

                                                Spacer(modifier = Modifier.height(12.dp))

                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    if (currentStatus == "PENDING_PAYMENT") {
                                                        Button(
                                                            onClick = { onNavigateToPayment(order.id, order.totalPrice) },
                                                            shape = RoundedCornerShape(14.dp),
                                                            modifier = Modifier.weight(1f),
                                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2159BC))
                                                        ) {
                                                            Text(text = "Thanh toán ngay", fontWeight = FontWeight.Bold, color = Color.White)
                                                        }
                                                    }
                                                    
                                                    OutlinedButton(
                                                        onClick = {
                                                            orderViewModel.cancelOrder(order.id, {
                                                                Toast.makeText(context, "Đã hủy đơn hàng!", Toast.LENGTH_SHORT).show()
                                                            }, { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() })
                                                        },
                                                        shape = RoundedCornerShape(14.dp),
                                                        modifier = Modifier.weight(1f),
                                                        border = BorderStroke(1.dp, Color(0xFFDC3545)),
                                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC3545))
                                                    ) {
                                                        Text(text = "Hủy đơn", fontWeight = FontWeight.SemiBold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    if (visiblePendingOrders.size < pendingOrders.size) item(key = "more-pending-orders") {
                                        TextButton(onClick = { visibleOrderLimit += 20 }, modifier = Modifier.fillMaxWidth()) { Text("Tải thêm đơn") }
                                    }
                                }
                            }
                            1 -> { 
                                if (activeOrders.isEmpty()) {
                                    item {
                                        Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                                            Text("Không có đơn hàng nào đang đến.", color = Color.Gray)
                                        }
                                    }
                                } else {
                                    items(visibleActiveOrders, key = { it.id }) { order ->
                                        val itemsSummaryText = order.items.joinToString(", ") { "${it.quantity}x ${it.name}" }
                                        val imgUrl = order.restaurantCoverImage
                                        val currentStatus = order.status.uppercase().trim()
                                        
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(20.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color.White),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(16.dp)) {
                                                val statusMsg = when(currentStatus) {
                                                    "PROCESSING" -> "đang được nhà hàng chuẩn bị (đang nấu)"
                                                    "ACCEPTED" -> "đã nấu xong và đang chờ shipper lấy hàng"
                                                    "SHIPPING" -> "đang trên đường giao đến bạn"
                                                    else -> ""
                                                }
                                                OngoingOrderItem(
                                                    restaurantName = order.restaurantName,
                                                    statusText = statusMsg,
                                                    estimatedTime = "Mã đơn: ..${order.id.takeLast(6).uppercase()}",
                                                    itemsSummary = itemsSummaryText,
                                                    restaurantImageUrl = imgUrl,
                                                    paymentMethod = order.paymentMethod,
                                                    chuyenTheoDoiDonHang = { chuyenHienThiTrangChiTiet(order.id) }
                                                )
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Button(
                                                    onClick = { chuyenHienThiTrangChiTiet(order.id) },
                                                    shape = RoundedCornerShape(14.dp),
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2159BC))
                                                ) {
                                                    Text(text = "Theo dõi đơn", fontWeight = FontWeight.Bold, color = Color.White)
                                                }
                                            }
                                        }
                                    }
                                    if (visibleActiveOrders.size < activeOrders.size) item(key = "more-active-orders") {
                                        TextButton(onClick = { visibleOrderLimit += 20 }, modifier = Modifier.fillMaxWidth()) { Text("Tải thêm đơn") }
                                    }
                                }
                            }
                            2 -> { 
                                if (historyOrders.isEmpty()) {
                                    item {
                                        Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                                            Text("Chưa có lịch sử đơn hàng.", color = Color.Gray)
                                        }
                                    }
                                } else {
                                    items(visibleHistoryOrders, key = { it.id }) { order ->
                                        val historyItemsSummary = order.items.joinToString(", ") { "${it.quantity}x ${it.name}" }
                                        val s = order.status.uppercase().trim()
                                        val displayStatus = if (s == "CANCELLED") " - đã bị hủy" else " - đã được giao thành công"
                                        val historyImgUrl = order.restaurantCoverImage
                                        val localeVi = Locale.forLanguageTag("vi-VN")
                                        val dinhDangGia = String.format(localeVi, "%,.0f đ", order.totalPrice)

                                        HistoryOrderItem(
                                            restaurantName = order.restaurantName.ifBlank { "Cửa hàng đối tác" } + displayStatus,
                                            date = order.time,
                                            price = dinhDangGia,
                                            itemsSummary = historyItemsSummary,
                                            restaurantImageUrl = historyImgUrl,
                                            onReorderClick = { /* Logic mua lại */ },
                                            onDetailClick = { chuyenHienThiTrangChiTiet(order.id) }
                                        )
                                    }
                                    if (visibleHistoryOrders.size < historyOrders.size) item(key = "more-history-orders") {
                                        TextButton(onClick = { visibleOrderLimit += 20 }, modifier = Modifier.fillMaxWidth()) { Text("Tải thêm đơn") }
                                    }
                                }
                            }
                        }
                    }
                    }
                }
            }
        }
    }
}
