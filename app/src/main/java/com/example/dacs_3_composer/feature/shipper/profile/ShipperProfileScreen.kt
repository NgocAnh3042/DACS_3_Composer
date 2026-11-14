package com.example.dacs_3_composer.feature.shipper.profile

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.dacs_3_composer.feature.shipper.profile.components.ProfileMenuItem
import java.text.NumberFormat
import java.util.Locale

private val ProfileBlue = Color(0xFF2159BC)
private val ProfileInk = Color(0xFF172333)
private val ProfileMuted = Color(0xFF66758A)
private val ProfileBackground = Color(0xFFF5F8FC)

@Composable
fun ShipperProfileScreen(
    onLogoutClick: () -> Unit,
    onNavigateToSection: (String) -> Unit,
    viewModel: ShipperProfileViewModel = viewModel()
) {
    val user by viewModel.user.collectAsState()
    val totalOrders by viewModel.totalOrders.collectAsState()
    val totalEarnings by viewModel.totalEarnings.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val preview by viewModel.avatarPreview.collectAsState()
    val error by viewModel.errorMessage.collectAsState()
    val context = LocalContext.current
    val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(viewModel::changeAvatar)
    }
    LaunchedEffect(error) {
        error?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    Scaffold(containerColor = ProfileBackground) { padding ->
        if (isLoading && user.uid.isBlank()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ProfileBlue)
            }
        } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).statusBarsPadding(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Cá nhân", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = ProfileInk)
                Text("Thông tin và hoạt động của bạn", fontSize = 14.sp, color = ProfileMuted)
            }
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(modifier = Modifier.size(104.dp).clickable { if (!isLoading) pickAvatar.launch("image/*") }) {
                            Box(
                                modifier = Modifier.size(96.dp).clip(CircleShape).background(Color(0xFFE8F0FF)),
                                contentAlignment = Alignment.Center
                            ) {
                                val image = preview ?: user.avatarUrl.takeIf { it.isNotBlank() }
                                if (image != null) {
                                    AsyncImage(model = image, contentDescription = "Ảnh đại diện tài xế", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                } else {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = ProfileBlue, modifier = Modifier.size(44.dp))
                                }
                                if (isLoading && preview != null) {
                                    Box(Modifier.fillMaxSize().background(Color(0x77000000)), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(26.dp), strokeWidth = 2.dp)
                                    }
                                }
                            }
                            Box(
                                modifier = Modifier.align(Alignment.BottomEnd).size(34.dp).clip(CircleShape).background(ProfileBlue),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = "Đổi ảnh đại diện", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(user.getDisplayName(), fontSize = 21.sp, fontWeight = FontWeight.Bold, color = ProfileInk)
                        Text(user.vehicleName.ifBlank { "Chưa cập nhật phương tiện" }, fontSize = 14.sp, color = ProfileMuted)
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = { onNavigateToSection("EDIT_PROFILE") },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.height(48.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ProfileBlue)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Chỉnh sửa thông tin", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    ProfileStat("Đơn hoàn thành", "$totalOrders", Icons.Default.CheckCircle, Modifier.weight(1f))
                    val earnings = NumberFormat.getNumberInstance(Locale("vi", "VN")).format(totalEarnings.toLong())
                    ProfileStat("Tổng thu nhập", "$earnings đ", Icons.Default.AccountBalanceWallet, Modifier.weight(1f))
                }
            }
            item {
                Text("Tài khoản", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = ProfileInk)
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProfileMenuItem(Icons.Default.Badge, Color(0xFFEAF1FF), ProfileBlue, "Thông tin tài xế", "Tên, số điện thoại và phương tiện") {
                        onNavigateToSection("EDIT_PROFILE")
                    }
                    ProfileMenuItem(Icons.Default.SupportAgent, Color(0xFFEAF1FF), ProfileBlue, "Trợ giúp và hỗ trợ", "Liên hệ khi cần hỗ trợ giao hàng") {
                        onNavigateToSection("SUPPORT")
                    }
                }
            }
            item {
                TextButton(onClick = onLogoutClick, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Default.Logout, contentDescription = null, tint = Color(0xFFB42318))
                    Spacer(Modifier.width(8.dp))
                    Text("Đăng xuất", color = Color(0xFFB42318), fontWeight = FontWeight.SemiBold)
                }
            }
        }
        }
    }
}

@Composable
private fun ProfileStat(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    Card(modifier = modifier.height(112.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(15.dp)) {
            Icon(icon, contentDescription = null, tint = ProfileBlue, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(8.dp))
            Text(value, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = ProfileInk, maxLines = 1)
            Text(label, fontSize = 12.sp, color = ProfileMuted)
        }
    }
}
