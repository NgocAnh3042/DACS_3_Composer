    package com.example.dacs_3_composer.feature.customer.cart.presentation

    import android.widget.Toast
    import androidx.compose.foundation.background
    import androidx.compose.foundation.border
    import androidx.compose.foundation.clickable
    import androidx.compose.foundation.layout.*
    import androidx.compose.foundation.lazy.LazyColumn
    import androidx.compose.foundation.lazy.items
    import androidx.compose.foundation.shape.RoundedCornerShape
    import androidx.compose.material.icons.Icons
    import androidx.compose.material.icons.filled.Add
    import androidx.compose.material.icons.filled.ArrowBack
    import androidx.compose.material.icons.filled.Delete
    import androidx.compose.material.icons.filled.DeleteOutline
    import androidx.compose.material.icons.filled.Edit
    import androidx.compose.material.icons.filled.KeyboardArrowRight
    import androidx.compose.material.icons.filled.LocationOn
    import androidx.compose.material.icons.filled.Person
    import androidx.compose.material.icons.filled.Phone
    import androidx.compose.material.icons.filled.Payment
    import androidx.compose.material.icons.filled.Money
    import androidx.compose.material3.*
    import androidx.compose.runtime.*
    import androidx.lifecycle.compose.collectAsStateWithLifecycle
    import androidx.compose.ui.Alignment
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.draw.clip
    import androidx.compose.ui.graphics.Color
    import androidx.compose.ui.layout.ContentScale
    import androidx.compose.ui.platform.LocalContext
    import androidx.compose.ui.text.font.FontWeight
    import androidx.compose.ui.text.style.TextOverflow
    import androidx.compose.ui.unit.dp
    import androidx.compose.ui.unit.sp
    import coil.compose.AsyncImage
    import com.example.dacs_3_composer.core.model.Promotion
    import com.example.dacs_3_composer.core.model.RestaurantDetail
    import com.example.dacs_3_composer.core.model.UserAddress
    import com.example.dacs_3_composer.core.model.User
    import com.example.dacs_3_composer.feature.customer.profile.AddressFormDialog
    import com.example.dacs_3_composer.feature.shared.location.DeliveryEstimate
    import com.example.dacs_3_composer.feature.shared.location.UserLocationCache
    import com.example.dacs_3_composer.data.remote.FoodHubClient
    import com.google.firebase.auth.FirebaseAuth
    import kotlinx.coroutines.launch

    data class SavedAddress(
        val id: String = "",
        val name: String = "",
        val phone: String = "",
        val address: String = "",
        val addressDetail: String = "",
        val latitude: Double = 0.0,
        val longitude: Double = 0.0,
        val isDefault: Boolean = false
    )

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun CartScreen(
        cartViewModel: CartViewModel,
        onBackClick: () -> Unit,
        onNavigateToPayment: (String, Double) -> Unit
    ) {
        val cartUiState by cartViewModel.uiState.collectAsStateWithLifecycle()
        val context = LocalContext.current
        val screenScope = rememberCoroutineScope()
        val cartItems = cartUiState.cartItems
        val totalDishPrice = cartViewModel.getTotalPrice()

        var currentName by remember { mutableStateOf("Chưa cập nhật tên") }
        var currentPhone by remember { mutableStateOf("Chưa có số điện thoại") }
        var currentAddress by remember { mutableStateOf("Chưa có địa chỉ giao hàng. Vui lòng thêm!") }
        var currentAddressDetail by remember { mutableStateOf("") }
        var currentLat by remember { mutableStateOf(0.0) }
        var currentLng by remember { mutableStateOf(0.0) }
        var currentStore by remember { mutableStateOf<RestaurantDetail?>(null) }

        var savedAddressesList by remember { mutableStateOf<List<SavedAddress>>(emptyList()) }
        var showMapDialog by remember { mutableStateOf(false) }
        var editingAddressIndex by remember { mutableStateOf<Int?>(null) }
        var addressToEditData by remember { mutableStateOf<SavedAddress?>(null) }

        val activeRestaurantId = cartUiState.currentRestaurantId.ifBlank {
            cartItems.firstOrNull()?.dish?.restaurantId.orEmpty()
        }

        LaunchedEffect(activeRestaurantId) {
            if (activeRestaurantId.isNotBlank() && activeRestaurantId != "RES_DEFAULT") {
                runCatching { FoodHubClient.get<RestaurantDetail>("stores", activeRestaurantId) }
                    .onSuccess { currentStore = it }
            }
        }

        LaunchedEffect(Unit) {
            cartViewModel.fetchPromotionsFromFirebase()

            if (FirebaseAuth.getInstance().currentUser != null) runCatching {
                val user = FoodHubClient.get<User>("users", "me")
                currentName = user.getDisplayName().ifBlank { "Chưa cập nhật tên" }
                currentPhone = user.phoneNumber.ifBlank { user.phone.ifBlank { "Chưa có số điện thoại" } }
                savedAddressesList = FoodHubClient.list("addresses")
                (savedAddressesList.firstOrNull { it.isDefault } ?: savedAddressesList.firstOrNull())?.let {
                    currentName=it.name;currentPhone=it.phone;currentAddress=it.address;currentAddressDetail=it.addressDetail;currentLat=it.latitude;currentLng=it.longitude
                }
            }.onFailure { android.util.Log.e("CartScreen", "Không tải được dữ liệu MySQL: ${it.message}") }
        }

        var showAddressBottomSheet by remember { mutableStateOf(false) }
        val addressSheetState = rememberModalBottomSheetState()

        // Tính khoảng cách thực tế từ quán đến địa chỉ nhận hàng và phí ship theo km:
        // 1–3 km: 15.000đ; trên 3 km mỗi 1 km tiếp theo cộng thêm 10.000đ (VD: 4 km = 25.000đ)
        val orderDistanceKm = remember(currentStore, currentLat, currentLng) {
            DeliveryEstimate.distanceKm(currentStore?.latitude, currentStore?.longitude, currentLat, currentLng)
        }
        val orderEtaText = remember(orderDistanceKm, currentStore) {
            DeliveryEstimate.estimateDeliveryMinutes(orderDistanceKm, currentStore?.deliveryTime)
        }
        val shippingFee = remember(orderDistanceKm) {
            DeliveryEstimate.shippingFee(orderDistanceKm)
        }

        // ✅ PHƯƠNG THỨC THANH TOÁN
        var paymentMethod by remember { mutableStateOf("CASH") } // "CASH" hoặc "ONLINE"

        // Danh sách Khuyến mãi và trạng thái chọn mã từ Firebase
        val listPromotionsFromFirebase = cartUiState.availablePromotions
        var selectedPromotion by remember { mutableStateOf<Promotion?>(null) }
        var showVoucherBottomSheet by remember { mutableStateOf(false) }
        val voucherSheetState = rememberModalBottomSheetState()

        // Logic tính toán số tiền khuyến mãi dựa trên cấu trúc bảng dữ liệu mới
        val discount = remember(selectedPromotion, totalDishPrice, shippingFee) {
            if (selectedPromotion != null) {
                if (totalDishPrice < selectedPromotion!!.minOrderValue) {
                    0.0 // Tổng tiền không đạt mức chi tiêu tối thiểu của mã
                } else {
                    when (selectedPromotion!!.type) {
                        "percentage" -> {
                            val tinhGiam = (totalDishPrice * selectedPromotion!!.value) / 100
                            if (selectedPromotion!!.maxDiscount > 0) tinhGiam.coerceAtMost(selectedPromotion!!.maxDiscount) else tinhGiam
                        }
                        "fixed" -> selectedPromotion!!.value
                        "free_shipping" -> shippingFee
                        else -> 0.0
                    }
                }
            } else {
                0.0
            }
        }

        val finalTotal = (totalDishPrice + shippingFee - discount).coerceAtLeast(0.0)

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Xác nhận đặt hàng", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) { Icon(Icons.Default.ArrowBack, contentDescription = null) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            },
            bottomBar = {
                if (cartItems.isNotEmpty()) {
                    Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 8.dp, shadowElevation = 8.dp, color = Color.White) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding()) {
                            Button(
                                onClick = {
                                    if (currentAddress.contains("Chưa có địa chỉ")) {
                                        Toast.makeText(context, "Vui lòng chọn địa chỉ giao hàng trước!", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    if (currentLat == 0.0 && currentLng == 0.0) {
                                        Toast.makeText(context, "Địa chỉ này chưa có vị trí trên bản đồ. Hãy sửa và chọn lại vị trí.", Toast.LENGTH_LONG).show()
                                        return@Button
                                    }
                                    if (selectedPromotion != null && totalDishPrice < selectedPromotion!!.minOrderValue) {
                                        Toast.makeText(context, "Đơn hàng của bạn không còn đủ điều kiện dùng mã này!", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }

                                    cartViewModel.placeOrder(
                                        customerName = currentName,
                                        customerPhone = currentPhone,
                                        customerAddress = if (currentAddressDetail.isNotBlank() && currentAddressDetail != currentAddress) "$currentAddressDetail, $currentAddress" else currentAddress,
                                        customerLat = currentLat,
                                        customerLng = currentLng,
                                        restaurantId = cartUiState.currentRestaurantId.ifBlank { "RES_DEFAULT" },
                                        restaurantName = cartUiState.currentRestaurantName.ifBlank { "Cửa hàng đối tác" },
                                        shippingFee = shippingFee,
                                        totalDishPrice = totalDishPrice,
                                        finalTotal = finalTotal,
                                        appliedPromotionId = selectedPromotion?.id,
                                        appliedPromotionTitle = selectedPromotion?.title ?: "",
                                        promotionDiscount = discount,
                                        paymentMethod = paymentMethod, // ✅ Truyền phương thức thanh toán
                                        onSuccess = { orderId ->
                                            if (paymentMethod == "ONLINE") {
                                                onNavigateToPayment(orderId, finalTotal)
                                            } else {
                                                Toast.makeText(context, "Đặt hàng thành công!", Toast.LENGTH_LONG).show()
                                                cartViewModel.clearCart()
                                                onBackClick()
                                            }
                                        },
                                        onFailure = { error -> Toast.makeText(context, error, Toast.LENGTH_SHORT).show() }
                                    )
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E56A0)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                val buttonText = if (paymentMethod == "ONLINE") "THANH TOÁN TRỰC TUYẾN" else "XÁC NHẬN ĐẶT HÀNG (COD)"
                                Text(buttonText, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding).background(Color(0xFFF8F9FA))) {
                if (cartItems.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Thông tin giao hàng
                        item {
                            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = "Thông tin giao hàng", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                        Row(modifier = Modifier.clickable { showAddressBottomSheet = true }, verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFF1E56A0), modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(text = "Thay đổi / Quản lý", fontSize = 13.sp, color = Color(0xFF1E56A0), fontWeight = FontWeight.Medium)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF1E56A0), modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(text = currentName, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Phone, contentDescription = null, tint = Color(0xFF1E56A0), modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(text = currentPhone, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.DarkGray)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(verticalAlignment = Alignment.Top) {
                                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.Red, modifier = Modifier.size(18.dp).padding(top = 2.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            if (currentAddressDetail.isNotBlank() && currentAddressDetail != currentAddress) {
                                                Text(text = "Chi tiết: $currentAddressDetail", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1E56A0))
                                                Spacer(modifier = Modifier.height(2.dp))
                                            }
                                            Text(text = currentAddress, fontSize = 14.sp, color = Color.DarkGray)
                                        }
                                    }
                                }
                            }
                        }

                        // ✅ KHU VỰC CHỌN PHƯƠNG THỨC THANH TOÁN
                        item {
                            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(text = "Phương thức thanh toán", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                    Spacer(modifier = Modifier.height(12.dp))

                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (paymentMethod == "CASH") Color(0xFFE8F0FE) else Color(0xFFF1F3F4))
                                                .border(1.dp, if (paymentMethod == "CASH") Color(0xFF1E56A0) else Color.Transparent, RoundedCornerShape(8.dp))
                                                .clickable { paymentMethod = "CASH" }
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Money, contentDescription = null, tint = if (paymentMethod == "CASH") Color(0xFF1E56A0) else Color.Gray)
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text("Thanh toán khi nhận hàng (COD)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (paymentMethod == "CASH") Color(0xFF1E56A0) else Color.Black)
                                                Text("Thanh toán bằng tiền mặt khi shipper giao hàng", fontSize = 12.sp, color = Color.Gray)
                                            }
                                            Spacer(modifier = Modifier.weight(1f))
                                            RadioButton(selected = paymentMethod == "CASH", onClick = { paymentMethod = "CASH" })
                                        }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (paymentMethod == "ONLINE") Color(0xFFE8F0FE) else Color(0xFFF1F3F4))
                                                .border(1.dp, if (paymentMethod == "ONLINE") Color(0xFF1E56A0) else Color.Transparent, RoundedCornerShape(8.dp))
                                                .clickable { paymentMethod = "ONLINE" }
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Payment, contentDescription = null, tint = if (paymentMethod == "ONLINE") Color(0xFF1E56A0) else Color.Gray)
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text("Chuyển khoản / Ví điện tử", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (paymentMethod == "ONLINE") Color(0xFF1E56A0) else Color.Black)
                                                Text("Thanh toán an toàn qua PayOS", fontSize = 12.sp, color = Color.Gray)
                                            }
                                            Spacer(modifier = Modifier.weight(1f))
                                            RadioButton(selected = paymentMethod == "ONLINE", onClick = { paymentMethod = "ONLINE" })
                                        }
                                    }
                                }
                            }
                        }

                        // Món ăn đã chọn
                        item { Text(text = "Món ăn đã chọn", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.Gray, modifier = Modifier.padding(start = 4.dp)) }
                        items(cartItems) { item ->
                            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    AsyncImage(model = item.dish.imageUrl, contentDescription = null, modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = item.dish.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(text = "${String.format("%,.0f", item.dish.price)} đ", fontSize = 13.sp, color = Color(0xFF1E56A0), modifier = Modifier.padding(top = 2.dp))
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        OutlinedIconButton(onClick = { cartViewModel.minusFromCart(item.dish) }, modifier = Modifier.size(28.dp), shape = RoundedCornerShape(6.dp)) {
                                            if (item.quantity == 1) Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Red)
                                            else Text("-", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                        }
                                        Text(text = "${item.quantity}", modifier = Modifier.padding(horizontal = 6.dp), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        OutlinedIconButton(onClick = { cartViewModel.addToCart(item.dish) }, modifier = Modifier.size(28.dp), shape = RoundedCornerShape(6.dp)) { Text("+", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Black) }
                                    }
                                }
                            }
                        }

                        // KHU VỰC CHỌN MÃ KHUYẾN MÃI (PROMOTION)
                        item {
                            Card(modifier = Modifier.fillMaxWidth().clickable { showVoucherBottomSheet = true }, colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = "🎟️ ", fontSize = 18.sp)
                                        if (selectedPromotion == null) Text(text = "Thêm mã khuyến mãi", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                        else Text(text = "Áp dụng: ${selectedPromotion!!.code}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                                    }
                                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = Color.Gray)
                                }
                            }
                        }

                        // Chi tiết hóa đơn
                        item {
                            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(text = "Chi tiết hóa đơn", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Tổng giá món", color = Color.DarkGray, fontSize = 14.sp)
                                        Text("${String.format("%,.0f", totalDishPrice)} đ", fontSize = 14.sp)
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (orderDistanceKm != null) {
                                                    "Phí giao hàng (${DeliveryEstimate.formatDistance(orderDistanceKm)} · ~$orderEtaText)"
                                                } else {
                                                    "Phí giao hàng"
                                                },
                                                color = Color.DarkGray,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = "1–3 km: 15.000đ · Mỗi 1 km tiếp theo: +10.000đ",
                                                color = Color.Gray,
                                                fontSize = 11.sp
                                            )
                                        }
                                        Text("+ ${String.format("%,.0f", shippingFee)} đ", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    }
                                    if (selectedPromotion != null && discount > 0) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Khuyến mãi (${selectedPromotion!!.code})", color = Color.DarkGray, fontSize = 14.sp)
                                            Text("- ${String.format("%,.0f", discount)} đ", fontSize = 14.sp, color = Color.Red)
                                        }
                                    }
                                    HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("Tổng thanh toán", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        Text(text = "${String.format("%,.0f", finalTotal)} đ", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E56A0))
                                    }
                                }
                            }
                        }
                    }
                }

                // BottomSheets và Dialogs (Giữ nguyên logic cũ)
                if (showAddressBottomSheet) {
                    ModalBottomSheet(onDismissRequest = { showAddressBottomSheet = false }, sheetState = addressSheetState, containerColor = Color.White) {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "Thay đổi thông tin nhận hàng", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                TextButton(onClick = { editingAddressIndex = null; addressToEditData = null; showMapDialog = true }) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Thêm mới", fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            if (savedAddressesList.isEmpty()) {
                                Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { Text("Bạn chưa lưu địa chỉ nào.", color = Color.Gray) }
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(savedAddressesList.size) { index ->
                                        val info = savedAddressesList[index]
                                        val isSelected = currentAddress == info.address && currentPhone == info.phone && currentName == info.name
                                        Card(
                                            modifier = Modifier.fillMaxWidth().border(1.dp, if (isSelected) Color(0xFF1E56A0) else Color(0xFFEEEEEE), RoundedCornerShape(12.dp)).clickable {
                                                currentName = info.name; currentPhone = info.phone; currentAddress = info.address; currentAddressDetail = info.addressDetail; currentLat = info.latitude; currentLng = info.longitude
                                                showAddressBottomSheet = false
                                            },
                                            colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFFE8F0FE) else Color(0xFFFAFAFA))
                                        ) {
                                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(text = "👤 ${info.name}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                                    Text(text = "📞 ${info.phone}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.DarkGray)
                                                    if (info.addressDetail.isNotBlank() && info.addressDetail != info.address) Text(text = "🏠 Chi tiết: ${info.addressDetail}", fontSize = 13.sp, color = Color(0xFF1E56A0), fontWeight = FontWeight.Medium)
                                                    Text(text = "📍 Định vị: ${info.address}", fontSize = 13.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    IconButton(onClick = { editingAddressIndex = index; addressToEditData = info; showMapDialog = true }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Edit, contentDescription = "Sửa", tint = Color(0xFF1E56A0), modifier = Modifier.size(18.dp)) }
                                                    IconButton(onClick = {
                                                        screenScope.launch {
                                                            runCatching { FoodHubClient.delete("addresses", info.id) }.onSuccess {
                                                                savedAddressesList = savedAddressesList.filterIndexed { i, _ -> i != index }
                                                                if (isSelected) {
                                                                    val next = savedAddressesList.firstOrNull { it.isDefault } ?: savedAddressesList.firstOrNull()
                                                                    currentName = next?.name ?: "Chưa cập nhật tên"
                                                                    currentPhone = next?.phone ?: "Chưa có số điện thoại"
                                                                    currentAddress = next?.address ?: "Chưa có địa chỉ giao hàng. Vui lòng thêm!"
                                                                    currentAddressDetail = next?.addressDetail.orEmpty()
                                                                    currentLat = next?.latitude ?: 0.0
                                                                    currentLng = next?.longitude ?: 0.0
                                                                }
                                                                Toast.makeText(context, "Đã xóa địa chỉ!", Toast.LENGTH_SHORT).show()
                                                            }.onFailure { Toast.makeText(context, "Lỗi xóa địa chỉ: ${it.message}", Toast.LENGTH_SHORT).show() }
                                                        }
                                                    }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.DeleteOutline, contentDescription = "Xóa", tint = Color.Red, modifier = Modifier.size(18.dp)) }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
                }

                if (showMapDialog) {
                    AddressFormDialog(
                        addressToEdit = addressToEditData?.let {
                            UserAddress(id = it.id, name = it.name, phone = it.phone, address = it.address,
                                addressDetail = it.addressDetail, latitude = it.latitude, longitude = it.longitude,
                                isDefault = it.isDefault)
                        },
                        onDismiss = { showMapDialog = false },
                        onSave = { chosen ->
                            val updatedAddress = SavedAddress(id = chosen.id, name = chosen.name, phone = chosen.phone,
                                address = chosen.address, addressDetail = chosen.addressDetail,
                                latitude = chosen.latitude, longitude = chosen.longitude, isDefault = chosen.isDefault)
                            screenScope.launch {
                                val old = editingAddressIndex?.let { savedAddressesList.getOrNull(it) }
                                runCatching {
                                    if (old == null) FoodHubClient.create("addresses", updatedAddress.copy(isDefault = updatedAddress.isDefault || savedAddressesList.isEmpty()))
                                    else FoodHubClient.update("addresses", old.id, updatedAddress)
                                    FoodHubClient.list<SavedAddress>("addresses")
                                }.onSuccess { addresses ->
                                    savedAddressesList = addresses
                                    val selectedAddress = addresses.firstOrNull { it.id == old?.id }
                                        ?: addresses.firstOrNull {
                                            it.address == updatedAddress.address &&
                                                it.name == updatedAddress.name && it.phone == updatedAddress.phone
                                        }
                                    selectedAddress?.let { saved ->
                                        currentName = saved.name; currentPhone = saved.phone
                                        currentAddress = saved.address; currentAddressDetail = saved.addressDetail
                                        currentLat = saved.latitude; currentLng = saved.longitude
                                    }
                                    showMapDialog = false
                                }
                                    .onFailure { Toast.makeText(context, "Lỗi lưu địa chỉ: ${it.message}", Toast.LENGTH_SHORT).show() }
                            }
                        }
                    )
                }

                if (showVoucherBottomSheet) {
                    ModalBottomSheet(onDismissRequest = { showVoucherBottomSheet = false }, sheetState = voucherSheetState, containerColor = Color.White) {
                        LazyColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            item {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "Chọn Mã Khuyến Mãi", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    if (selectedPromotion != null) {
                                        TextButton(onClick = { selectedPromotion = null; showVoucherBottomSheet = false }) { Text("Hủy chọn", color = Color.Red, fontWeight = FontWeight.Bold) }
                                    }
                                }
                            }

                            items(listPromotionsFromFirebase) { promo ->
                                val isSelected = selectedPromotion?.id == promo.id
                                val duDieuKienNhanma = totalDishPrice >= promo.minOrderValue

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(width = 1.dp, color = if (isSelected) Color(0xFF4CAF50) else Color(0xFFEEEEEE), shape = RoundedCornerShape(10.dp))
                                        .clickable(enabled = duDieuKienNhanma) {
                                            selectedPromotion = promo
                                            showVoucherBottomSheet = false
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) Color(0xFFE8F5E9) else if (duDieuKienNhanma) Color(0xFFFAFAFA) else Color(0xFFF2F2F2)
                                    )
                                ) {
                                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(text = promo.code, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (duDieuKienNhanma) Color(0xFF1E56A0) else Color.Gray)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Box(modifier = Modifier.background(if (duDieuKienNhanma) Color(0xFFFFE082) else Color(0xFFE0E0E0), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                                    val textGiam = if (promo.type == "percentage") "Giảm ${promo.value}%" else "Giảm ${String.format("%,.0f", promo.value)}đ"
                                                    Text(text = textGiam, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (duDieuKienNhanma) Color(0xFFE65100) else Color.DarkGray)
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(text = promo.title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                            if (promo.description.isNotBlank()) {
                                                Text(text = promo.description, fontSize = 12.sp, color = Color.Gray)
                                            }

                                            if (!duDieuKienNhanma) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "Đơn hàng tối thiểu phải đạt từ ${String.format("%,.0f", promo.minOrderValue)}đ",
                                                    fontSize = 12.sp, color = Color.Red, fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                        RadioButton(
                                            selected = isSelected,
                                            enabled = duDieuKienNhanma,
                                            onClick = { selectedPromotion = promo; showVoucherBottomSheet = false },
                                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF4CAF50))
                                        )
                                    }
                                }
                            }
                            item { Spacer(modifier = Modifier.height(32.dp)) }
                        }
                    }
                }
            }
        }
    }
