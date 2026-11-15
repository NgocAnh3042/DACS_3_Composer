package com.example.dacs_3_composer.feature.admin.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.dacs_3_composer.R
import com.example.dacs_3_composer.feature.admin.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminProfileContent(state: ProfileUiState, onRefresh: () -> Unit, onPickAvatar: () -> Unit,
    onSave: (String, String, (Boolean) -> Unit) -> Unit, onOverview: () -> Unit, onUsers: () -> Unit,
    onPromotions: () -> Unit, onSupport: () -> Unit, onLogout: () -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var loggingOut by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    Scaffold(containerColor = AdminBackground, topBar = {
        TopAppBar(title = { Text("Cá nhân quản trị", color = AdminInk, fontWeight = FontWeight.Bold, fontSize = 22.sp) },
            actions = { IconButton(onClick = onRefresh, enabled = !state.isLoading && !state.isSaving) { Icon(Icons.Default.Refresh, "Làm mới hồ sơ", tint = AdminBlue) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White))
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (state.isLoading || state.isSaving) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = AdminBlue) }
            if (state.errorMessage != null) item { AdminPanel { Text(state.errorMessage, color = MaterialTheme.colorScheme.error); TextButton(onClick = onRefresh, enabled = !state.isSaving) { Text("Thử lại") } } }
            item { AdminPanel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.size(88.dp)) {
                        if (state.avatarUrl.isNotBlank()) AsyncImage(model = state.avatarUrl, contentDescription = "Ảnh đại diện quản trị viên",
                            placeholder = painterResource(R.drawable.ic_avatar_default), error = painterResource(R.drawable.ic_avatar_default),
                            modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                        else Box(Modifier.fillMaxSize().clip(CircleShape).background(Color(0xFFE8EFFB)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Person, null, tint = AdminBlue, modifier = Modifier.size(44.dp))
                        }
                        FilledIconButton(onClick = onPickAvatar, enabled = !state.isSaving && state.uid.isNotBlank(),
                            modifier = Modifier.align(Alignment.BottomEnd).size(48.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = AdminBlue)) {
                            Icon(Icons.Default.PhotoCamera, "Thay ảnh đại diện", modifier = Modifier.size(20.dp))
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(state.adminName.ifBlank { "Quản trị viên" }, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = AdminInk)
                        Text(state.adminEmail, style = MaterialTheme.typography.bodySmall, color = AdminMuted)
                        Text("Quản trị viên", color = AdminBlue, style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.background(Color(0xFFEDF3FF), CircleShape).padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                }
            } }
            item { AdminPanel {
                Text("Thông tin tài khoản", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AdminInk)
                ProfileField("Họ và tên", state.adminName)
                ProfileField("Email đăng nhập", state.adminEmail)
                ProfileField("Số điện thoại", state.adminPhone.ifBlank { "Chưa bổ sung" })
                OutlinedButton(onClick = { name = state.adminName; phone = state.adminPhone; editing = true },
                    enabled = state.uid.isNotBlank() && !state.isSaving && !state.isLoading, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Chỉnh sửa thông tin")
                }
            } }
            item {
                Text("Quy mô tài khoản hệ thống", fontWeight = FontWeight.Bold, color = AdminInk)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProfileStat("Tài khoản", state.totalUsersCount, Icons.Default.People, Modifier.weight(1f))
                    ProfileStat("Nhà hàng", state.totalRestaurants.toString(), Icons.Default.Storefront, Modifier.weight(1f))
                    ProfileStat("Shipper", state.totalShippers.toString(), Icons.Default.DeliveryDining, Modifier.weight(1f))
                }
            }
            item { AdminPanel {
                Text("Lối tắt quản trị", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AdminInk)
                ProfileShortcut("Tổng quan", "Doanh thu và hoạt động hệ thống", Icons.Default.Insights, onOverview)
                ProfileShortcut("Người dùng", "Danh sách và vai trò tài khoản", Icons.Default.ManageAccounts, onUsers)
                ProfileShortcut("Khuyến mãi", "Mã giảm giá và lượt sử dụng", Icons.Default.ConfirmationNumber, onPromotions)
                ProfileShortcut("Hộp thư hỗ trợ", "Xem và trả lời hội thoại", Icons.Default.ChatBubbleOutline, onSupport)
            } }
            item {
                OutlinedButton(onClick = { loggingOut = true }, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB42332))) {
                    Icon(Icons.Default.Logout, null); Spacer(Modifier.width(8.dp)); Text("Đăng xuất tài khoản")
                }
            }
        }
    }
    if (editing) AlertDialog(onDismissRequest = { if (!state.isSaving) editing = false }, title = { Text("Chỉnh sửa thông tin") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Họ và tên") }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !state.isSaving)
            OutlinedTextField(phone, { phone = it }, label = { Text("Số điện thoại") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), enabled = !state.isSaving)
            Text("Email đăng nhập: ${state.adminEmail}", style = MaterialTheme.typography.bodySmall, color = AdminMuted)
        }
    }, confirmButton = {
        Button(onClick = { onSave(name, phone) { success -> if (success) editing = false } }, enabled = !state.isSaving && name.isNotBlank() && name.length <= 160 && phone.length <= 32) {
            Text(if (state.isSaving) "Đang lưu…" else "Lưu thay đổi")
        }
    }, dismissButton = { TextButton(onClick = { editing = false }, enabled = !state.isSaving) { Text("Hủy") } })
    if (loggingOut) AlertDialog(onDismissRequest = { loggingOut = false }, title = { Text("Đăng xuất tài khoản?") },
        text = { Text("Bạn có thể đăng nhập lại để tiếp tục quản trị FoodHub.") },
        confirmButton = { TextButton(onClick = { loggingOut = false; onLogout() }) { Text("Đăng xuất") } },
        dismissButton = { TextButton(onClick = { loggingOut = false }) { Text("Ở lại") } })
}

@Composable
private fun ProfileField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = AdminMuted)
        Text(value.ifBlank { "Chưa có thông tin" }, color = AdminInk)
    }
}

@Composable
private fun ProfileStat(label: String, value: String, icon: ImageVector, modifier: Modifier) {
    AdminPanel(modifier) {
        Icon(icon, null, tint = AdminBlue)
        Text(value, color = AdminInk, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = AdminMuted)
    }
}

@Composable
private fun ProfileShortcut(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = AdminBlue, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = AdminInk, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = AdminMuted, style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.Default.ChevronRight, null, tint = AdminMuted)
    }
}
