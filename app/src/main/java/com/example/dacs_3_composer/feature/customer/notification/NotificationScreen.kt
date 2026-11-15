package com.example.dacs_3_composer.feature.customer.notification

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.dacs_3_composer.R
import com.example.dacs_3_composer.core.model.NotificationItem
import com.example.dacs_3_composer.data.remote.FoodHubClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.dacs_3_composer.feature.customer.home.HomeViewModel // 🌟 Thêm import này

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(
    modifier: Modifier = Modifier,
    homeViewModel: HomeViewModel = viewModel() // 🌟 Lấy dữ liệu User dùng chung từ HomeViewModel đã load sẵn
) {
    // Tự động load lại thông tin user nếu cần thiết
    LaunchedEffect(Unit) {
        homeViewModel.loadUserData()
    }

    // Lấy thông tin động từ ViewModel
    val currentUserName = homeViewModel.userName.ifBlank { "Khách" }
    val currentUserImageUrl = homeViewModel.userImageUrl

    var notifications by remember { mutableStateOf<List<NotificationItem>>(emptyList()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        while (true) {
            runCatching { FoodHubClient.list<NotificationItem>("notifications") }.onSuccess { notifications = it }
            delay(8_000)
        }
    }

    var selectedTab by remember { mutableStateOf("all") }

    val filteredNotifications = when (selectedTab) {
        "order" -> notifications.filter { it.type == "order" }
        "system" -> notifications.filter { it.type == "system" }
        else -> notifications
    }

    Scaffold(
        topBar = {
            TopAppBar(

                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // 🌟 HIỂN THỊ AVATAR USER THẬT TỪ FIREBASE
                            AsyncImage(
                                model = currentUserImageUrl.ifBlank { R.drawable.ic_avatar_default }, // Nếu link trống dùng ảnh mặc định
                                contentDescription = "Avatar",
                                placeholder = painterResource(id = R.drawable.ic_avatar_default),
                                error = painterResource(id = R.drawable.ic_avatar_default),
                                modifier = Modifier
                                    .size(45.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(12.dp))

                            // 🌟 HIỂN THỊ TÊN USER THẬT TỪ FIREBASE
                            Text(
                                text = currentUserName,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF191C1D) // Thay màu xanh cũ bằng màu tối để nổi bật tên User như ảnh mẫu
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFFF8F9FA))
                .padding(paddingValues)
        ) {
            // Tiêu đề Trang & Nút Đánh dấu đã đọc
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Thông báo",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                Row(
                    modifier = Modifier.clickable {
                        scope.launch { runCatching { FoodHubClient.update("notifications", "read-all", emptyMap<String, String>()) }; notifications = notifications.map { it.copy(isRead = true) } }
                    },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF2159BC), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Đánh dấu tất cả là đã đọc", color = Color(0xFF2159BC), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }

            // Thanh Bộ Lọc Tab
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val tabs = listOf("all" to "Tất cả", "order" to "Đơn hàng", "system" to "Hệ thống")
                tabs.forEach { (type, label) ->
                    val isSelected = selectedTab == type
                    Button(
                        onClick = { selectedTab = type },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) Color(0xFF2159BC) else Color(0xFFE7E8E9)
                        ),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.White else Color(0xFF555555),
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Danh sách thông báo
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredNotifications) { item ->
                    NotificationCard(item = item)
                }
            }
        }
    }
}

// Bỏ hàm NotificationCard cũ bên dưới nếu đã có sẵn trong file của bạn rồi.
@Composable
fun NotificationCard(item: NotificationItem) {
    val icon = when {
        item.title.contains("Đơn hàng") -> Icons.Default.ReceiptLong
        item.title.contains("Chiến dịch") -> Icons.Default.CardGiftcard
        else -> Icons.Default.Settings
    }

    val iconBgColor = when {
        item.title.contains("Đơn hàng") -> Color(0xFFE8F0FE)
        item.title.contains("Chiến dịch") -> Color(0xFFFFE8EC)
        else -> Color(0xFFECEFF1)
    }

    val iconTint = when {
        item.title.contains("Đơn hàng") -> Color(0xFF2159BC)
        item.title.contains("Chiến dịch") -> Color(0xFFE91E63)
        else -> Color(0xFF607D8B)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            if (!item.isRead) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(50.dp)
                        .background(Color(0xFF2159BC), RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(iconBgColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.Black
                    )

                    if (!item.isRead) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color(0xFF2159BC), CircleShape)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = item.content,
                    fontSize = 13.sp,
                    color = Color(0xFF666666),
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = item.time,
                    fontSize = 11.sp,
                    color = Color(0xFF999999)
                )
            }
        }
    }
}
