package com.example.dacs_3_composer.feature.restaurant.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dacs_3_composer.R
import com.example.dacs_3_composer.feature.restaurant.profile.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestauranProfileScreen(
    onStoreInfoClick: () -> Unit,
    onNotificationSettingClick: () -> Unit,
    onActivityHistoryClick: () -> Unit,
    onSecurityClick: () -> Unit,
    onHelpClick: () -> Unit,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier,
    profileViewModel: RestaurantProfileViewModel = viewModel()
) {
    var showEditNameDialog by remember { mutableStateOf(false) }
    var inputName by remember { mutableStateOf("") }
    val context = LocalContext.current
    LaunchedEffect(profileViewModel.error) {
        profileViewModel.error?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            profileViewModel.clearError()
        }
    }

    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { profileViewModel.uploadAndSaveAvatar(it) }
    }

    val coverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { profileViewModel.uploadAndSaveCover(it) }
    }

    LaunchedEffect(showEditNameDialog) {
        if (showEditNameDialog) {
            inputName = profileViewModel.name
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 🎯 LIÊN KẾT ĐỘNG: Gắn biến trực tiếp từ database
            item {
                ProfileImages(
                    coverUrl = profileViewModel.coverUrl,
                    avatarUrl = profileViewModel.avatarUrl,
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
                        name = profileViewModel.name,
                        email = profileViewModel.email
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
                ProfileStatsSection(
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            item {
                SystemSettingsSection(
                    onStoreInfoClick = onStoreInfoClick,
                    onNotificationSettingClick = onNotificationSettingClick,
                    onActivityHistoryClick = onActivityHistoryClick,
                    onSecurityClick = onSecurityClick,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            item {
                SupportAndLogoutSection(
                    onHelpClick = onHelpClick,
                    onLogoutClick = onLogoutClick,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }

        // Lớp mờ và vòng xoay tiến trình khi đang thực hiện tải file
        if (profileViewModel.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = Color(0xFF2159BC))
                        Text(
                            text = "Đang xử lý tải ảnh lên đám mây...",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF191C1D)
                        )
                    }
                }
            }
        }

        if (showEditNameDialog) {
            AlertDialog(
                onDismissRequest = { showEditNameDialog = false },
                title = { Text(text = "Thay đổi tên nhà hàng", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = inputName,
                        onValueChange = { inputName = it },
                        label = { Text("Tên mới") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            profileViewModel.updateName(inputName)
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
