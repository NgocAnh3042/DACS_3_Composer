package com.example.dacs_3_composer.ui.user.profile

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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dacs_3_composer.data.repository.ChatRepository
import com.example.dacs_3_composer.ui.user.profile.components.MembershipAndWalletSection
import com.example.dacs_3_composer.ui.user.profile.components.ProfileHeader
import com.example.dacs_3_composer.ui.user.profile.components.ProfileMenuRow
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
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
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
                walletBalance = "1.250k"
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    ProfileMenuRow(
                        icon = Icons.Default.ReceiptLong,
                        iconTint = Color(0xFF2159BC),
                        iconBgColor = Color(0xFFE8F0FE),
                        title = "Lịch sử đơn hàng",
                        onClick = { onNavigateToOrderHistory() }
                    )
                    ProfileMenuRow(
                        icon = Icons.Default.LocationOn,
                        iconTint = Color(0xFF2159BC),
                        iconBgColor = Color(0xFFE8F0FE),
                        title = "Địa chỉ đã lưu",
                        onClick = { onNavigateToManageAddress() }
                    )
                    ProfileMenuRow(
                        icon = Icons.Default.History,
                        iconTint = Color(0xFF2159BC),
                        iconBgColor = Color(0xFFE8F0FE),
                        title = "Lịch sử thanh toán",
                        onClick = { onNavigateToPaymentHistory() }
                    )
                    
                    // 🎯 THÊM: Menu Hỗ trợ để nhắn tin cho Admin
                    ProfileMenuRow(
                        icon = Icons.Default.SupportAgent,
                        iconTint = Color(0xFFE67E22),
                        iconBgColor = Color(0xFFFEF5E7),
                        title = "Trợ giúp & Hỗ trợ (Chat với Admin)",
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    val conversationId = chatRepository.contactSupport("user")
                                    onNavigateToChatDetail(conversationId)
                                } catch (e: Exception) {
                                    // Xử lý lỗi nếu cần
                                }
                            }
                        }
                    )

                    ProfileMenuRow(
                        icon = Icons.Default.Settings,
                        iconTint = Color(0xFF727785),
                        iconBgColor = Color(0xFFF1F3F4),
                        title = "Cài đặt",
                        onClick = { /* Cài đặt app */ }
                    )
                }
            }

            Button(
                onClick = { onLogoutClick() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F3F4)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        tint = Color(0xFFC0392B)
                    )
                    Text(text = "Đăng xuất", color = Color(0xFFC0392B), fontWeight = FontWeight.Bold)
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
