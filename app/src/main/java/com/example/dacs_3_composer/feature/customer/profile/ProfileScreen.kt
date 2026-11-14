package com.example.dacs_3_composer.feature.customer.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dacs_3_composer.data.repository.ChatRepository
import com.example.dacs_3_composer.feature.customer.profile.components.MembershipAndWalletSection
import com.example.dacs_3_composer.feature.customer.profile.components.ProfileHeader
import com.example.dacs_3_composer.feature.customer.profile.components.ProfileMenuRow
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    profileViewModel: ProfileViewModel = viewModel(),
    onLogoutClick: () -> Unit = {},
    onNavigateToOrderHistory: () -> Unit = {},
    onNavigateToManageAddress: () -> Unit = {},
    onNavigateToPaymentHistory: () -> Unit = {},
    onNavigateToChatDetail: (String) -> Unit = {} // 🎯 THÊM: Callback để đi thẳng vào chat với Admin
) {
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val chatRepository = ChatRepository() // Dùng để gọi contactSupport

    LaunchedEffect(Unit) {
        profileViewModel.loadUserData()
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { profileViewModel.uploadAvatar(it) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFFF8F9FA))
                .padding(horizontal = 20.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "Tài khoản",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF191C1D),
                modifier = Modifier.padding(top = 12.dp)
            )

            ProfileHeader(
                name = profileViewModel.userName,
                email = profileViewModel.userEmail,
                avatarState = profileViewModel.avatarState,
                onEditAvatarClick = {
                    imagePickerLauncher.launch("image/*")
                }
            )

            MembershipAndWalletSection(
                memberRank = "Gold Member",
                walletBalance = "1.250.000đ"
            )

            Text(
                text = "TIỆN ÍCH CÁ NHÂN",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                color = Color(0xFF727785),
                modifier = Modifier.padding(start = 2.dp)
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    ProfileMenuRow(
                        icon = Icons.Default.ReceiptLong,
                        iconTint = Color(0xFF2159BC),
                        iconBgColor = Color(0xFFE8F0FE),
                        title = "Lịch sử đơn hàng",
                        subtitle = "Theo dõi các đơn hàng đã đặt",
                        onClick = { onNavigateToOrderHistory() }
                    )
                    ProfileMenuDivider()
                    ProfileMenuRow(
                        icon = Icons.Default.LocationOn,
                        iconTint = Color(0xFF2159BC),
                        iconBgColor = Color(0xFFE8F0FE),
                        title = "Địa chỉ đã lưu",
                        subtitle = "Quản lý địa chỉ giao hàng",
                        onClick = { onNavigateToManageAddress() }
                    )
                    ProfileMenuDivider()
                    ProfileMenuRow(
                        icon = Icons.Default.History,
                        iconTint = Color(0xFF2159BC),
                        iconBgColor = Color(0xFFE8F0FE),
                        title = "Lịch sử thanh toán",
                        subtitle = "Xem các giao dịch của bạn",
                        onClick = { onNavigateToPaymentHistory() }
                    )
                }
            }

            Text(
                text = "CÀI ĐẶT & HỖ TRỢ",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                color = Color(0xFF727785),
                modifier = Modifier.padding(start = 2.dp)
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column {
                    ProfileMenuRow(
                        icon = Icons.Default.Settings,
                        iconTint = Color(0xFF2159BC),
                        iconBgColor = Color(0xFFE8F0FE),
                        title = "Cài đặt tài khoản",
                        onClick = { /* Cài đặt app */ }
                    )
                    ProfileMenuDivider()
                    ProfileMenuRow(
                        icon = Icons.Default.SupportAgent,
                        iconTint = Color(0xFF2159BC),
                        iconBgColor = Color(0xFFE8F0FE),
                        title = "Chat với hỗ trợ & Admin",
                        subtitle = "Nhắn tin với quản trị viên",
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    val conversationId = chatRepository.contactSupport("user")
                                    onNavigateToChatDetail(conversationId)
                                } catch (e: Exception) {
                                    // Preserve the existing silent error handling.
                                }
                            }
                        }
                    )
                }
            }

            Button(
                onClick = { onLogoutClick() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE8F0FE),
                    contentColor = Color(0xFF2159BC)
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .padding(bottom = 8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        tint = Color(0xFF2159BC)
                    )
                    Text(text = "Đăng xuất tài khoản", color = Color(0xFF2159BC), fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (profileViewModel.isUploading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

@Composable
private fun ProfileMenuDivider() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 72.dp)
            .height(1.dp)
            .background(Color(0xFFF0F2F5))
    )
}
