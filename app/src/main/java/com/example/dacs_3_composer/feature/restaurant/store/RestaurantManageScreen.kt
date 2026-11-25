package com.example.dacs_3_composer.feature.restaurant.store

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.dacs_3_composer.core.model.DishItem
import com.example.dacs_3_composer.feature.shared.location.AddressLocationPicker
import com.example.dacs_3_composer.feature.shared.location.AddressPlace
import com.example.dacs_3_composer.feature.restaurant.store.components.DishRowManageItem
import com.example.dacs_3_composer.feature.restaurant.store.components.RestaurantInfoManageCard

@Composable
fun RestaurantManageScreen(
    restaurantId: String, // ID của quán ăn cần quản lý
    onBackClick: () -> Unit,
    onEditCoverClick: () -> Unit,
    onEditDishClick: (DishItem) -> Unit,
    viewModel: StoreViewModel = viewModel()
) {
    val context = LocalContext.current
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedImageUri ->
            // Gửi uri ảnh cục bộ trong máy này qua ViewModel để thảy lên Cloudinary
            viewModel.uploadCoverToCloudinary(
                imageUri = selectedImageUri,
                restaurantId = restaurantId,
                uploadPreset = "ml_default",
                onSuccess = {
                    Log.d("UI", "Đã cập nhật xong ảnh bìa lấy từ Cloudinary!")
                }
            )
        }
    }
    // Tự động tải dữ liệu thật từ Firebase về khi vừa mở màn hình
    LaunchedEffect(restaurantId) {
        viewModel.fetchStoreData(restaurantId)
    }

    val restaurant = viewModel.restaurantDetail.value
    val dishes = viewModel.dishesList.value
    LaunchedEffect(viewModel.error.value) {
        viewModel.error.value?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            viewModel.error.value = null
        }
    }

    // Các trạng thái để quản lý việc Ẩn/Hiện Dialog chỉnh sửa thông tin
    var showDialog by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf("") }
    var editDescription by remember { mutableStateOf("") }
    var editAddressExtra by remember { mutableStateOf("") }
    var editPlace by remember { mutableStateOf<AddressPlace?>(null) }
    var searchPending by remember { mutableStateOf(false) }
    var editEmail by remember { mutableStateOf("") }
    var editPhone by remember { mutableStateOf("") }
    var editOpenTime by remember { mutableStateOf("") }
    var editCloseTime by remember { mutableStateOf("") }
    var selectedLogoUri by remember { mutableStateOf<Uri?>(null) }
    var selectedCoverUri by remember { mutableStateOf<Uri?>(null) }
    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { selectedLogoUri = it }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { selectedCoverUri = it }

    var showCoverDialog by remember { mutableStateOf(false) }
    var editCoverUrl by remember { mutableStateOf("") }

// Cập nhật giá trị link ảnh mặc định khi dữ liệu quán load xong
    LaunchedEffect(restaurant) {
        restaurant?.let {
            editName = it.name
            editDescription = it.description
            editAddressExtra = ""
            editPlace = if (it.latitude != null && it.longitude != null &&
                (it.latitude != 0.0 || it.longitude != 0.0) && it.address.isNotBlank())
                AddressPlace(it.address, it.latitude, it.longitude) else null
            editEmail = it.email
            editPhone = it.phone
            editOpenTime = it.openTime
            editCloseTime = it.closeTime
            editCoverUrl = it.coverImage
        }
    }

    Scaffold { innerPadding ->
        if (restaurant == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF1E56A0))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Color(0xFFF8F9FA))
            ) {
                // Phần 1: Ảnh bìa
                item {
                    Box(modifier = Modifier.height(220.dp).fillMaxWidth()) {
                        AsyncImage(
                            model = restaurant.coverImage,
                            contentDescription = "Ảnh bìa nhà hàng",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )

                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier
                                .padding(16.dp)
                                .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.Black)
                        }

                        SmallFloatingActionButton(
                            onClick = {
                                galleryLauncher.launch("image/*")
                            },
                            containerColor = Color.White,
                            contentColor = Color(0xFF1E56A0),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp),
                            shape = CircleShape
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Sửa ảnh bìa", modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // Phần 2: Khối thông tin chi tiết Nhà hàng + Khi bấm nút sửa sẽ MỞ DIALOG
                item {
                    RestaurantInfoManageCard(
                        restaurant = restaurant,
                        onEditInfoClick = {
                            editName = restaurant.name
                            editDescription = restaurant.description
                            editAddressExtra = ""
                            editPlace = if (restaurant.latitude != null && restaurant.longitude != null &&
                                (restaurant.latitude != 0.0 || restaurant.longitude != 0.0) && restaurant.address.isNotBlank())
                                AddressPlace(restaurant.address, restaurant.latitude, restaurant.longitude) else null
                            editEmail = restaurant.email
                            editPhone = restaurant.phone
                            editOpenTime = restaurant.openTime
                            editCloseTime = restaurant.closeTime
                            selectedLogoUri = null
                            selectedCoverUri = null
                            searchPending = false
                            showDialog = true
                        }
                    )
                }

                // Phần 3: Tiêu đề danh sách món
                item {
                    Text(
                        text = "Danh sách món ăn hiện tại",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1A1A),
                        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 12.dp)
                    )
                }

                // Phần 4: Danh sách món ăn
                items(dishes, key = { it.id }) { dish ->
//                    Để sản phẩm vào và vẽ ra kiểu để hiển thị món ăn
                    DishRowManageItem(
                        dish = dish,
                        onItemClick = { onEditDishClick(dish) }
                    )
                }
            }
        }

        // 🌟 HỘP THOẠI (DIALOG) CHỈNH SỬA THÔNG TIN CỦA CỬA HÀNG
        if (showDialog) {
            AlertDialog(
                onDismissRequest = { if (!viewModel.isSaving.value) showDialog = false },
                title = { Text(text = "Chỉnh sửa thông tin quán", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                text = {
                    Column(
                        modifier = Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = editName,
                            onValueChange = { editName = it },
                            label = { Text("Tên quán *") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true
                        )
                        Text("Liên hệ và địa chỉ", style = MaterialTheme.typography.titleSmall, color = Color(0xFF2159BC))
                        OutlinedTextField(value = editEmail, onValueChange = { editEmail = it }, label = { Text("Email liên hệ") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                        OutlinedTextField(value = editPhone, onValueChange = { editPhone = it }, label = { Text("Số điện thoại") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                        AddressLocationPicker(
                            selected = editPlace,
                            onSelected = { place -> editPlace = place },
                            mapHeight = 170.dp,
                            onSearchPendingChange = { searchPending = it }
                        )
                        OutlinedTextField(value = editAddressExtra, onValueChange = { editAddressExtra = it },
                            label = { Text("Số nhà, cổng hoặc hướng dẫn thêm") }, modifier = Modifier.fillMaxWidth())
                        Text("Giờ hoạt động", style = MaterialTheme.typography.titleSmall, color = Color(0xFF2159BC))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = editOpenTime, onValueChange = { editOpenTime = it }, label = { Text("Mở cửa") }, placeholder = { Text("08:00") }, modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedTextField(value = editCloseTime, onValueChange = { editCloseTime = it }, label = { Text("Đóng cửa") }, placeholder = { Text("22:00") }, modifier = Modifier.weight(1f), singleLine = true)
                        }
                        OutlinedTextField(value = editDescription, onValueChange = { editDescription = it }, label = { Text("Giới thiệu quán") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                        Text("Hình ảnh", style = MaterialTheme.typography.titleSmall, color = Color(0xFF2159BC))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { logoPicker.launch("image/*") }, modifier = Modifier.weight(1f)) { Text(if (selectedLogoUri == null) "Ảnh đại diện" else "Đã chọn ảnh") }
                            OutlinedButton(onClick = { coverPicker.launch("image/*") }, modifier = Modifier.weight(1f)) { Text(if (selectedCoverUri == null) "Ảnh bìa" else "Đã chọn ảnh") }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E56A0)),
                        enabled = !viewModel.isSaving.value,
                        onClick = {
                            val place = editPlace
                            if (searchPending) {
                                android.widget.Toast.makeText(context, "Hãy chọn một gợi ý hoặc chờ vị trí tải xong trước khi lưu.", android.widget.Toast.LENGTH_LONG).show()
                            } else if (place == null) {
                                android.widget.Toast.makeText(context, "Hãy chọn GPS hoặc một địa chỉ trên bản đồ", android.widget.Toast.LENGTH_LONG).show()
                            } else {
                                val fullAddress = if (editAddressExtra.isBlank()) place.address else "${editAddressExtra.trim()}, ${place.address}"
                                restaurant?.let { current ->
                                    viewModel.updateRestaurantInfo(
                                        current.copy(name = editName.trim(), email = editEmail.trim(), phone = editPhone.trim(),
                                            address = fullAddress, description = editDescription.trim(),
                                            openTime = editOpenTime.trim(), closeTime = editCloseTime.trim(), latitude = place.latitude, longitude = place.longitude),
                                        selectedLogoUri, selectedCoverUri,
                                        onSuccess = { showDialog = false }
                                    )
                                }
                            }
                        }
                    ) {
                        Text(if (viewModel.isSaving.value) "Đang lưu…" else "Lưu", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDialog = false }, enabled = !viewModel.isSaving.value) {
                        Text("Hủy", color = Color.Gray)
                    }
                }
            )
        }
        // 🌟 HỘP THOẠI (DIALOG) CHỈNH SỬA ẢNH BÌA CỦA CỬA HÀNG
        if (showCoverDialog) {
            AlertDialog(
                onDismissRequest = { showCoverDialog = false },
                title = { Text(text = "Thay đổi ảnh bìa quán", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(text = "Nhập link URL ảnh mới từ internet để thay đổi giao diện hiển thị của quán.", fontSize = 14.sp, color = Color.Gray)
                        OutlinedTextField(
                            value = editCoverUrl,
                            onValueChange = { editCoverUrl = it },
                            label = { Text("Đường dẫn link ảnh (URL)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E56A0)),
                        onClick = {
                            // Gọi ViewModel đẩy link ảnh mới lên Firebase
                            viewModel.updateCoverImage(
                                restaurantId = restaurantId,
                                newCoverUrl = editCoverUrl,
                                onSuccess = { showCoverDialog = false } // Thành công thì đóng Dialog
                            )
                        }
                    ) {
                        Text("Lưu ảnh", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCoverDialog = false }) {
                        Text("Hủy", color = Color.Gray)
                    }
                }
            )
        }
    }
}
