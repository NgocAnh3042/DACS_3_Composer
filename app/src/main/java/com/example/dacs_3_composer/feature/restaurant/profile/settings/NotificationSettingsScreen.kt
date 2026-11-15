package com.example.dacs_3_composer.feature.restaurant.profile.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    onBackClick: () -> Unit,
    viewModel: NotificationSettingsViewModel = viewModel()
) {
    val detail = viewModel.restaurantDetail

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Cài đặt thông báo",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF181C20)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF1A73E8)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { /* More options */ }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color(0xFF727785))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.White.copy(alpha = 0.7f)
                ),
                modifier = Modifier.clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            )
        },
        containerColor = Color(0xFFF7F9FF)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Tùy chỉnh cách bạn nhận thông tin về hoạt động của nhà hàng để đảm bảo vận hành trơn tru nhất.",
                fontSize = 14.sp,
                color = Color(0xFF414754),
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Section 1: Hoạt động chính
            NotificationSectionHeader(title = "Hoạt động chính")
            
            NotificationItemCard(
                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                title = "Thông báo đơn hàng mới",
                description = "Nhận tin ngay khi có khách đặt món",
                checked = detail?.orderNotify ?: true,
                onCheckedChange = { viewModel.updateNotificationSetting("orderNotify", it) }
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            NotificationItemCard(
                icon = Icons.Default.NotificationsActive,
                title = "Âm thanh thông báo",
                description = "Phát âm thanh khi có tin nhắn mới",
                checked = detail?.soundNotify ?: true,
                onCheckedChange = { viewModel.updateNotificationSetting("soundNotify", it) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Section 2: Kênh nhận tin
            NotificationSectionHeader(title = "Kênh nhận tin")
            
            NotificationItemCard(
                icon = Icons.Default.EdgesensorHigh,
                title = "Push notification",
                description = "Thông báo trên màn hình khóa",
                checked = detail?.pushNotify ?: true,
                onCheckedChange = { viewModel.updateNotificationSetting("pushNotify", it) }
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            NotificationItemCard(
                icon = Icons.Default.AlternateEmail,
                title = "Email notification",
                description = "Báo cáo tổng kết qua email hàng ngày",
                checked = detail?.emailNotify ?: false,
                onCheckedChange = { viewModel.updateNotificationSetting("emailNotify", it) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Section 3: Tiếp thị & Khuyến mãi
            NotificationSectionHeader(title = "Tiếp thị & Khuyến mãi")
            
            NotificationItemCard(
                icon = Icons.Default.Campaign,
                title = "Thông báo khuyến mãi",
                description = "Chương trình ưu đãi cho chủ nhà hàng",
                checked = detail?.promoNotify ?: false,
                onCheckedChange = { viewModel.updateNotificationSetting("promoNotify", it) }
            )

            // Verification Footer (Glass-card style)
            Spacer(modifier = Modifier.height(32.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFD8E2FF).copy(alpha = 0.3f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD8E2FF))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = Color(0xFF005BBF),
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Cài đặt của bạn được bảo mật",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF001A41)
                    )
                    Text(
                        "Chúng tôi chỉ gửi những thông báo quan trọng nhất để không làm phiền công việc của bạn.",
                        fontSize = 13.sp,
                        color = Color(0xFF004493),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
fun NotificationSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF005BBF),
        modifier = Modifier.padding(start = 8.dp, bottom = 12.dp),
        letterSpacing = 1.2.sp
    )
}

@Composable
fun NotificationItemCard(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.8f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE8EAED))
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFF1A73E8).copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFF1A73E8),
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF181C20)
                )
                Text(
                    text = description,
                    fontSize = 13.sp,
                    color = Color(0xFF727785)
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF1A73E8),
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color(0xFFDFE3E8)
                )
            )
        }
    }
}
