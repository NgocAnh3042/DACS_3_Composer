package com.example.dacs_3_composer.feature.restaurant.profile.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.dacs_3_composer.feature.shared.location.AddressLocationPicker
import com.example.dacs_3_composer.feature.shared.location.AddressPlace
import coil.request.ImageRequest
import com.example.dacs_3_composer.R
import kotlinx.coroutines.delay
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestaurantInfoScreen(
    onBackClick: () -> Unit,
    viewModel: RestaurantInfoViewModel = viewModel()
) {
    val context = LocalContext.current
    val detail = viewModel.restaurantDetail

    // --- State cho form ---
    var name by remember(detail) { mutableStateOf(detail?.name ?: "") }
    var email by remember(detail) { mutableStateOf(detail?.email ?: "") }
    var phone by remember(detail) { mutableStateOf(detail?.phone ?: "") }
    var openTime by remember(detail) { mutableStateOf(detail?.openTime ?: "") }
    var closeTime by remember(detail) { mutableStateOf(detail?.closeTime ?: "") }
    var description by remember(detail) { mutableStateOf(detail?.description ?: "") }

    var selectedPlace by remember(detail) {
        mutableStateOf(detail?.takeIf { it.latitude != null && it.longitude != null &&
            (it.latitude != 0.0 || it.longitude != 0.0) && it.address.isNotBlank() }
            ?.let { AddressPlace(it.address, it.latitude!!, it.longitude!!) })
    }
    var addressExtra by remember(detail) { mutableStateOf("") }
    var searchPending by remember(detail) { mutableStateOf(false) }
    var selectedAvatarUri by remember { mutableStateOf<Uri?>(null) }
    var selectedCoverUri by remember { mutableStateOf<Uri?>(null) }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { selectedAvatarUri = it }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { selectedCoverUri = it }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F9FF))
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- 1. PHẦN HÌNH NỀN (COVER IMAGE) ---
        Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(selectedCoverUri ?: detail?.coverImage ?: R.drawable.banner1)
                    .crossfade(true).build(),
                contentDescription = "Cover Image",
                modifier = Modifier.fillMaxSize().clickable { coverPicker.launch("image/*") },
                contentScale = ContentScale.Crop
            )

            IconButton(
                onClick = onBackClick,
                modifier = Modifier.padding(16.dp).background(Color.White.copy(alpha = 0.7f), CircleShape)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }

            Box(
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).background(Color.Black.copy(alpha = 0.6f), CircleShape).clickable { coverPicker.launch("image/*") }.padding(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Text("Sửa nền", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // --- 2. PHẦN ẢNH ĐẠI DIỆN (AVATAR) ---
        Box(modifier = Modifier.offset(y = (-60).dp)) {
            Box(contentAlignment = Alignment.BottomEnd) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(selectedAvatarUri ?: detail?.avatarUrl ?: R.drawable.banner1)
                        .crossfade(true).build(),
                    contentDescription = "Avatar",
                    modifier = Modifier.size(120.dp).clip(CircleShape).background(Color.White).padding(4.dp).clip(CircleShape).clickable { avatarPicker.launch("image/*") },
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier.size(36.dp).background(Color(0xFF1A73E8), CircleShape).clickable { avatarPicker.launch("image/*") }.padding(8.dp).offset(x = (-2).dp, y = (-2).dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Sửa đại diện", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }

        // --- 3. FORM THÔNG TIN CHI TIẾT ---
        Column(modifier = Modifier.padding(horizontal = 20.dp).offset(y = (-40).dp)) {

            Text("Địa chỉ lấy hàng của quán", style = MaterialTheme.typography.titleSmall, color = Color(0xFF2159BC))
            AddressLocationPicker(
                selected = selectedPlace,
                onSelected = { place -> selectedPlace = place },
                modifier = Modifier.fillMaxWidth(),
                mapHeight = 230.dp,
                onSearchPendingChange = { searchPending = it }
            )
            InfoTextField(label = "Số nhà, cổng hoặc hướng dẫn thêm (tùy chọn)", value = addressExtra,
                onValueChange = { addressExtra = it }, icon = Icons.Default.LocationOn)
            InfoTextField(label = "Tên nhà hàng", value = name, onValueChange = { name = it }, icon = Icons.Default.Storefront)
            InfoTextField(label = "Email liên hệ", value = email, onValueChange = { email = it }, icon = Icons.Default.Mail)
            InfoTextField(label = "Số điện thoại", value = phone, onValueChange = { phone = it }, icon = Icons.Default.Call)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    InfoTextField(label = "Giờ mở cửa", value = openTime, onValueChange = { openTime = it }, icon = Icons.Default.Schedule, placeholder = "08:00")
                }
                Box(modifier = Modifier.weight(1f)) {
                    InfoTextField(label = "Giờ đóng cửa", value = closeTime, onValueChange = { closeTime = it }, icon = Icons.Default.HistoryToggleOff, placeholder = "22:00")
                }
            }

            InfoTextField(label = "Mô tả quán", value = description, onValueChange = { description = it }, icon = Icons.Default.Description, singleLine = false, minLines = 3)

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    val place = selectedPlace
                    if (searchPending) {
                        Toast.makeText(context, "Hãy chọn một gợi ý hoặc chờ vị trí tải xong trước khi lưu.", Toast.LENGTH_LONG).show()
                    } else if (place == null) {
                        Toast.makeText(context, "Hãy chọn GPS hoặc một địa chỉ trên bản đồ.", Toast.LENGTH_LONG).show()
                    } else {
                        val fullAddress = if (addressExtra.isBlank()) place.address else "${addressExtra.trim()}, ${place.address}"
                        viewModel.updateRestaurantInfo(
                            name, email, phone, fullAddress, place.latitude, place.longitude,
                            openTime, closeTime, description, selectedAvatarUri, selectedCoverUri
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (viewModel.saveSuccess) Color(0xFF008939) else Color(0xFF1A73E8)
                ),
                enabled = !viewModel.isSaving && detail != null
            ) {
                if (viewModel.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    Text(
                        if (viewModel.saveSuccess) "Đã lưu thành công" else "Lưu thay đổi",
                        fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp
                    )
                }
            }

            LaunchedEffect(viewModel.saveSuccess) {
                if (viewModel.saveSuccess) {
                    delay(2500)
                    viewModel.resetSaveSuccess()
                }
            }
            LaunchedEffect(viewModel.error) {
                viewModel.error?.let {
                    Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                    viewModel.clearError()
                }
            }
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
fun InfoTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    icon: ImageVector,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF414754),
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        OutlinedTextField(
            value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(icon, contentDescription = null, tint = Color(0xFF2159BC)) },
            placeholder = { Text(placeholder, color = Color(0xFF727785)) },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color(0xFFC1C6D6), focusedBorderColor = Color(0xFF005BBF),
                unfocusedContainerColor = Color.White, focusedContainerColor = Color.White
            ),
            singleLine = singleLine, minLines = minLines
        )
    }
}
