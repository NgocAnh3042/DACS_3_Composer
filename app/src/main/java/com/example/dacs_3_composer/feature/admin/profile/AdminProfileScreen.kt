package com.example.dacs_3_composer.feature.admin.profile

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun AdminProfileScreen(
    viewModel: AdminProfileViewModel = viewModel(),
    onNavigateToOverview: () -> Unit,
    onNavigateToUsers: () -> Unit,
    onNavigateToPromotions: () -> Unit,
    onNavigateToSupport: () -> Unit,
    onLogoutCallbackk: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.uploadAvatarToCloudinary(context, uri,
            onSuccess = { Toast.makeText(context, "Đã cập nhật ảnh đại diện", Toast.LENGTH_SHORT).show() },
            onFailure = { Toast.makeText(context, "Không lưu được ảnh đại diện. Hãy thử lại.", Toast.LENGTH_LONG).show() })
    }
    AdminProfileContent(state = state, onRefresh = viewModel::loadProfile, onPickAvatar = { picker.launch("image/*") },
        onSave = { name, phone, done ->
            viewModel.updateAccountInfo(name, phone) { success ->
                done(success)
                Toast.makeText(context, if (success) "Đã lưu thông tin tài khoản" else "Chưa lưu được thông tin. Hãy thử lại.", Toast.LENGTH_SHORT).show()
            }
        }, onOverview = onNavigateToOverview, onUsers = onNavigateToUsers, onPromotions = onNavigateToPromotions,
        onSupport = onNavigateToSupport, onLogout = { viewModel.logout(onLogoutCallbackk) })
}
