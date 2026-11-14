package com.example.dacs_3_composer.feature.admin.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.example.dacs_3_composer.feature.restaurant.profile.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminProfileScreen(
    onVoucherManagementClick: () -> Unit,
    onNotificationSettingClick: () -> Unit,
    onSecurityClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AdminProfileViewModel = viewModel()
) {
    var showEditNameDialog by remember { mutableStateOf(false) }
    var inputName by remember { mutableStateOf("") }

    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.uploadAndSaveAvatar(it) }
    }

    val coverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.uploadAndSaveCover(it) }
    }

    LaunchedEffect(showEditNameDialog) {
        if (showEditNameDialog) {
            inputName = viewModel.name
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Hồ sơ Admin", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color(0xFFF8F9FA)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item {
                    ProfileImages(
                        coverUrl = viewModel.coverUrl,
                        avatarUrl = viewModel.avatarUrl,
                        onEditCoverClick = { coverPickerLauncher.launch("image/*") },
                        onEditAvatarClick = { avatarPickerLauncher.launch("image/*") }
                    )
                }

                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ProfileInfo(
                            name = viewModel.name,
                            email = viewModel.email
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { showEditNameDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2159BC)),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(text = "Đổi tên hiển thị", fontSize = 13.sp)
                        }
                    }
                }

                item {
                    AdminSettingsSection(
                        onVoucherManagementClick = onVoucherManagementClick,
                        onNotificationSettingClick = onNotificationSettingClick,
                        onSecurityClick = onSecurityClick,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }

                item {
                    SupportAndLogoutSection(
                        onHelpClick = {},
                        onLogoutClick = {
                            viewModel.logout()
                            onLogoutClick()
                        },
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }

            if (viewModel.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = Color(0xFF2159BC))
                            Text("Đang cập nhật...", fontSize = 13.sp)
                        }
                    }
                }
            }

            if (showEditNameDialog) {
                AlertDialog(
                    onDismissRequest = { showEditNameDialog = false },
                    title = { Text("Thay đổi tên Admin") },
                    text = {
                        OutlinedTextField(
                            value = inputName,
                            onValueChange = { inputName = it },
                            label = { Text("Tên mới") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.updateName(inputName)
                                showEditNameDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2159BC))
                        ) {
                            Text("Xác nhận")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showEditNameDialog = false }) {
                            Text("Hủy", color = Color.Gray)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun AdminSettingsSection(
    onVoucherManagementClick: () -> Unit,
    onNotificationSettingClick: () -> Unit,
    onSecurityClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "QUẢN TRỊ HỆ THỐNG",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF727785),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column {
                RestauranSettingItem(
                    icon = Icons.Default.ConfirmationNumber,
                    title = "Quản lý ưu đãi (Voucher)",
                    onClick = onVoucherManagementClick
                )
                HorizontalDivider(color = Color(0xFFF1F3F4), thickness = 1.dp)
                RestauranSettingItem(
                    icon = Icons.Default.NotificationsActive,
                    title = "Cài đặt thông báo",
                    onClick = onNotificationSettingClick
                )
                HorizontalDivider(color = Color(0xFFF1F3F4), thickness = 1.dp)
                RestauranSettingItem(
                    icon = Icons.Default.Security,
                    title = "Bảo mật tài khoản",
                    onClick = onSecurityClick
                )
            }
        }
    }
}
