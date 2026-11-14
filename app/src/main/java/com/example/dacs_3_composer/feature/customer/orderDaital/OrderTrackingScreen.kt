package com.example.dacs_3_composer.feature.customer.orderDaital

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dacs_3_composer.R
import com.example.dacs_3_composer.feature.customer.order.OrderViewModel
import com.example.dacs_3_composer.feature.customer.orderDaital.components.DriverMapSection
import com.example.dacs_3_composer.feature.customer.orderDaital.components.OrderSummarySection
import com.example.dacs_3_composer.feature.customer.orderDaital.components.TimelineNode
import com.example.dacs_3_composer.feature.customer.orderDaital.components.TrackingHeader
import java.util.Locale

@Composable
fun OrderTrackingScreen(
    orderId: String, // 🌟 NHẬN ID ĐƠN HÀNG TỪ MÀN HÌNH TRƯỚC TRUYỀN SANG
    onBackClick: () -> Unit,
    orderViewModel: OrderViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    // Kích hoạt lắng nghe dữ liệu từ Firebase khi màn hình mở lên
    LaunchedEffect(orderId) {
        orderViewModel.observeOrderDetails(orderId)
    }

    // Thu thập dữ liệu đơn hàng thời gian thực
    val orderState by orderViewModel.currentTrackingOrder.collectAsState()
    Scaffold(
        topBar = {
            TrackingHeader(orderId = "#ORD-${orderId.takeLast(6)}", onBackClick = onBackClick)
        }
    ) { paddingValues ->
        val order = orderState

        if (order == null) {
            // Màn hình chờ tải dữ liệu đơn hàng
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF2159BC))
            }
        } else {
            // Xác định trạng thái để bật sáng các nút Timeline (Boolean)
            val isPending = true // Luôn bật vì đơn đã được gửi đi
            val isAccepted = order.status == "ACCEPTED" || order.status == "SHIPPING" || order.status == "COMPLETED"
            val isShipperGetted = (order.shipperId.isNotBlank() && order.status == "ACCEPTED") || order.status == "SHIPPING" || order.status == "COMPLETED"
            val isShipping = order.status == "SHIPPING" || order.status == "COMPLETED"
            val isCompleted = order.status == "COMPLETED"

            Column(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color(0xFFF8F9FA))
                    .padding(paddingValues)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Phần bản đồ & thông tin Tài xế (Chỉ hiện nếu đã có shipper nhận đơn)
                if (order.shipperId.isNotBlank()) {
                    DriverMapSection(
                        driverName = "Tài xế đối tác", // Sau này có bảng Shippers bạn query theo shipperId gán vào đây
                        licensePlate = "Đang đến lấy hàng",
                        rating = "4.9 ★",
                        driverAvatarRes = R.drawable.banner1
                    )
                } else {
                    // Nếu chưa có shipper, thông báo hệ thống đang tìm tài xế
                    Card(
                        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4E5))
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Moped, contentDescription = null, tint = Color(0xFFFFA000))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Hệ thống đang điều phối tài xế gần nhất...",
                                color = Color(0xFF191C1D),
                                fontSize = 14.sp // 🌟 Đổi từ .dp thành .sp tại đây
                            )
                        }
                    }
                }

                // 2. Khối Trạng thái đơn hàng (Timeline)
                Card(
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Trạng thái đơn hàng",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF191C1D),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        if (order.status == "CANCELLED") {
                            // Nếu đơn hàng bị hủy, báo trạng thái hủy luôn
                            TimelineNode(
                                icon = Icons.Default.Close,
                                title = "Đơn hàng đã hủy",
                                description = "Đơn hàng này đã bị hủy bỏ.",
                                time = order.time,
                                isCompleted = true,
                                isLast = true
                            )
                        } else {
                            // Node 1: Đã gửi đơn
                            TimelineNode(
                                icon = Icons.Default.Check,
                                title = "Đã nhận đơn",
                                description = "Hệ thống đã nhận đơn hàng của bạn.",
                                time = order.time.takeLast(5), // Lấy chuỗi giờ phút HH:mm cuối chuỗi time
                                isCompleted = isPending,
                                isLast = false
                            )

                            // Node 2: Nhà hàng chuẩn bị món
                            TimelineNode(
                                icon = Icons.Default.Restaurant,
                                title = "Đang chuẩn bị",
                                description = "Nhà hàng ${order.restaurantName} đang chế biến món ăn.",
                                time = null,
                                isCompleted = isAccepted,
                                isLast = false
                            )

                            // Node 3: 🌟 THÊM MỚI: Tài xế nhận đơn
                            TimelineNode(
                                icon = Icons.Default.Person,
                                title = "Tài xế đã nhận đơn",
                                description = if (order.shipperId.isNotBlank()) "Tài xế đang di chuyển đến nhà hàng lấy món." else "Đang chờ tài xế nhận đơn...",
                                time = null,
                                isCompleted = isShipperGetted,
                                isLast = false
                            )

                            // Node 4: Đang giao
                            TimelineNode(
                                icon = Icons.Default.DeliveryDining,
                                title = "Đang giao hàng",
                                description = "Tài xế đang trên đường mang món ăn tới bạn.",
                                time = null,
                                isCompleted = isShipping,
                                isLast = false,
                                extraContent = {
                                    if (order.status == "SHIPPING") {
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFFE8F0FE), RoundedCornerShape(12.dp))
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "🔵 Tài xế đang giao đi",
                                                fontSize = 11.sp,
                                                color = Color(0xFF2159BC),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            )

                            // Node 5: Hoàn thành
                            TimelineNode(
                                icon = Icons.Default.CheckCircle,
                                title = "Hoàn thành",
                                description = "Chúc bạn ngon miệng!",
                                time = null,
                                isCompleted = isCompleted,
                                isLast = true
                            )
                        }
                    }
                }

                // 3. Khối tóm tắt đơn hàng (Đổ dữ liệu động từ list items)
                val orderedItems = order.items.map { item ->
                    Pair(item.name, item.quantity)
                }

                val dinhDangGia = String.format(Locale("vi", "VN"), "%,.0f đ", order.totalPrice)

                OrderSummarySection(
                    items = orderedItems,
                    totalPrice = dinhDangGia, // 🚀 Hiện đúng số tiền thực tế (Ví dụ: 15.000 đ)
                    onDetailClick = { /* Xem hoá đơn chi tiết nếu cần */ },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                // 4. Khối liên hệ trợ giúp
                Card(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F3F4))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp).background(Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.HeadsetMic,
                                contentDescription = null,
                                tint = Color(0xFFE28743),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = "Gặp sự cố?", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                text = "Liên hệ ngay với bộ phận CSKH 24/7 để được hỗ trợ nhanh nhất.",
                                fontSize = 12.sp,
                                color = Color(0xFF727785)
                            )
                        }
                    }
                }
            }
        }
    }
}