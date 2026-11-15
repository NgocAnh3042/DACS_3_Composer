package com.example.dacs_3_composer.feature.admin.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.dacs_3_composer.core.model.Promotion
import com.google.firebase.Timestamp
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPromotionScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AdminPromotionViewModel
) {
    val context = LocalContext.current
    val promotions by viewModel.promotions.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var selectedFilter by remember { mutableStateOf("Tất cả") }
    var showAddDialog by remember { mutableStateOf(false) }

    // Lắng nghe thông báo từ ViewModel chính xác qua SharedFlow
    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val filteredPromotions = when (selectedFilter) {
        "Đang chạy" -> promotions.filter { it.status == "active" }
        "Hết hạn" -> promotions.filter { it.status == "expired" }
        else -> promotions
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Quản lý Khuyến mãi", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D1B20)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF1D1B20))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color(0xFFF8F9FA)
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
            ) {
                // Tiêu đề phân hệ
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Chiến dịch ưu đãi", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D1B20))
                            Text("Thiết lập và theo dõi các chương trình khuyến mãi", fontSize = 13.sp, color = Color.Gray)
                        }
                        Button(
                            onClick = { showAddDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2159BC)),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.AddCircle, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Tạo mới", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

//                // 1. Flash Sale Live Metrics Card
//                item {
//                    FeaturedPromotionCard()
//                }

                // 2. Banner Event Card
                item {
                    PromotionBannerCard()
                }

                // 3. Thanh lọc trạng thái Voucher
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Tất cả", "Đang chạy", "Hết hạn").forEach { filter ->
                            FilterChip(
                                label = filter,
                                isSelected = selectedFilter == filter,
                                onClick = { selectedFilter = filter }
                            )
                        }
                    }
                }

                // 4. Danh sách hiển thị chính thức
                if (isLoading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), Alignment.Center) {
                            CircularProgressIndicator(color = Color(0xFF2159BC))
                        }
                    }
                } else if (filteredPromotions.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) {
                            Text("Không tìm thấy mã giảm giá nào phù hợp", color = Color.Gray, fontSize = 14.sp)
                        }
                    }
                } else {
                    items(filteredPromotions, key = { it.id }) { promotion ->
                        VoucherItemCard(
                            promotion = promotion,
                            onDelete = { viewModel.deletePromotion(promotion.id) },
                            onToggle = { viewModel.toggleStatus(promotion.id, promotion.status) }
                        )
                    }
                }
            }

            // Dialog Form thêm mới Voucher độc lập
            if (showAddDialog) {
                AddPromotionDialog(
                    onDismiss = { showAddDialog = false },
                    onConfirm = { newPromotion ->
                        viewModel.addPromotion(newPromotion)
                        showAddDialog = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPromotionDialog(
    onDismiss: () -> Unit,
    onConfirm: (Promotion) -> Unit
) {
    var code by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("percentage") } // percentage, fixed, free_shipping
    var value by remember { mutableStateOf("") }
    var maxDiscount by remember { mutableStateOf("") }
    var minOrderValue by remember { mutableStateOf("") }
    var usageLimit by remember { mutableStateOf("") }
    var daysCount by remember { mutableStateOf("7") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tạo mã ưu đãi mới", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2159BC)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase() },
                    label = { Text("Mã Code (ví dụ: ANTRUADOI)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Tên hiển thị chiến dịch") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                Text("Loại hình khuyến mãi:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val types = listOf("percentage" to "Phần trăm (%)", "fixed" to "Số tiền cố định", "free_shipping" to "Free Ship")
                    types.forEach { (t, label) ->
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { type = t },
                            color = if (type == t) Color(0xFF2159BC) else Color(0xFFF1F3F4),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = label,
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                textAlign = TextAlign.Center,
                                color = if (type == t) Color.White else Color(0xFF5F6368),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                if (type != "free_shipping") {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { if (it.all { c -> c.isDigit() }) value = it },
                        label = { Text(if (type == "percentage") "Giá trị giảm (%)" else "Số tiền giảm (đ)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }

                if (type == "percentage") {
                    OutlinedTextField(
                        value = maxDiscount,
                        onValueChange = { if (it.all { c -> c.isDigit() }) maxDiscount = it },
                        label = { Text("Giảm kịch trần tối đa (đ)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = minOrderValue,
                    onValueChange = { if (it.all { c -> c.isDigit() }) minOrderValue = it },
                    label = { Text("Giá trị đơn tối thiểu cần đạt (đ)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                OutlinedTextField(
                    value = usageLimit,
                    onValueChange = { if (it.all { c -> c.isDigit() }) usageLimit = it },
                    label = { Text("Tổng số lượng phát hành (0 = vô hạn)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                OutlinedTextField(
                    value = daysCount,
                    onValueChange = { if (it.all { c -> c.isDigit() }) daysCount = it },
                    label = { Text("Thời hạn sử dụng kể từ bây giờ (Ngày)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (code.isNotBlank() && title.isNotBlank()) {
                        val calendar = Calendar.getInstance()
                        val startDate = Timestamp(calendar.time)
                        calendar.add(Calendar.DAY_OF_YEAR, daysCount.toIntOrNull() ?: 7)
                        val endDate = Timestamp(calendar.time)

                        val newPromotion = Promotion(
                            code = code.trim(),
                            title = title.trim(),
                            type = type,
                            value = value.toDoubleOrNull() ?: 0.0,
                            maxDiscount = maxDiscount.toDoubleOrNull() ?: 0.0,
                            minOrderValue = minOrderValue.toDoubleOrNull() ?: 0.0,
                            usageCount = 0L,
                            usageLimit = usageLimit.toLongOrNull() ?: 0L,
                            startDate = startDate,
                            endDate = endDate,
                            status = "active",
                            description = title.trim()
                        )
                        onConfirm(newPromotion)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2159BC)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Kích hoạt")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy bỏ", color = Color.Gray)
            }
        }
    )
}

@Composable
fun FeaturedPromotionCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F0FE)),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(color = Color(0xFF2159BC), shape = CircleShape) {
                    Text("ĐANG DIỄN RA", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                    Text(" 14:00 - 16:00 Hôm nay", fontSize = 12.sp, color = Color.Gray)
                }
            }

            Spacer(Modifier.height(10.dp))
            Text("Flash Sale: Giờ vàng giá sốc", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2159BC))
            Text("Áp dụng tự động cho các nhóm món ăn bán chạy nhất", fontSize = 13.sp, color = Color.Gray)

            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatSmallCard("Lượt đặt", "428", Color(0xFF40E56C), Modifier.weight(1f))
                StatSmallCard("Doanh thu", "12.5M", Color(0xFFFFB950), Modifier.weight(1f))
                StatSmallCard("Tỉ lệ chốt", "84%", Color(0xFF2159BC), Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun PromotionBannerCard() {
    Card(
        modifier = Modifier.height(130.dp).fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Box {
            AsyncImage(
                model = "https://images.unsplash.com/photo-1504674900247-0877df9cc836?q=80&w=1000",
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.7f)))))
            Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                Text("BANNER HỆ THỐNG", color = Color(0xFFFFB950), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("Mừng đại lễ - Giảm trọn thực đơn 20% toàn sàn", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun FilterChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        color = if (isSelected) Color(0xFF2159BC) else Color(0xFFE8EAED),
        shape = CircleShape
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.White else Color(0xFF727785),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun StatSmallCard(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = Color.White, shape = RoundedCornerShape(10.dp)) {
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 10.sp, color = Color.Gray)
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = valueColor)
        }
    }
}

@Composable
fun VoucherItemCard(promotion: Promotion, onDelete: () -> Unit, onToggle: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    val expiryDate = promotion.endDate?.toDate()?.let { dateFormat.format(it) } ?: "Vô hạn"

    val progressValue = if (promotion.usageLimit > 0) {
        (promotion.usageCount.toFloat() / promotion.usageLimit).coerceIn(0f, 1f)
    } else 0f

    val usageLimitDisplay = if (promotion.usageLimit == 0L) "∞" else promotion.usageLimit.toString()

    val currencyFormatter = remember { NumberFormat.getCurrencyInstance(Locale("vi", "VN")) }
    fun formatMoney(amount: Double): String = currencyFormatter.format(amount).replace("₫", "đ")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).background(Color(0xFFE8F0FE), RoundedCornerShape(10.dp)), Alignment.Center) {
                Icon(Icons.Default.ConfirmationNumber, null, tint = Color(0xFF2159BC), modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(promotion.code, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2159BC))
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color = if (promotion.status == "active") Color(0xFFE6F4EA) else Color(0xFFFCE8E6),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = if (promotion.status == "active") "Đang chạy" else "Tạm dừng",
                            color = if (promotion.status == "active") Color(0xFF137333) else Color(0xFFC5221F),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = when(promotion.type) {
                        "percentage" -> "Giảm ${promotion.value.toInt()}% • Tối đa ${formatMoney(promotion.maxDiscount)}"
                        "fixed" -> "Giảm ${formatMoney(promotion.value)} • Đơn từ ${formatMoney(promotion.minOrderValue)}"
                        "free_shipping" -> "Free Ship • Đơn từ ${formatMoney(promotion.minOrderValue)}"
                        else -> promotion.title.ifEmpty { "Ưu đãi chiến dịch" }
                    },
                    fontSize = 13.sp,
                    color = Color(0xFF5F6368)
                )

                Spacer(Modifier.height(8.dp))
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Đã dùng: ${promotion.usageCount}/$usageLimitDisplay", fontSize = 10.sp, color = Color.Gray)
                        Text("Hạn: $expiryDate", fontSize = 10.sp, color = Color.Gray)
                    }
                    Spacer(Modifier.height(4.dp))
                    // Sửa đổi cú pháp progress chuẩn tương thích tốt với SDK cũ & mới
                    LinearProgressIndicator(
                        progress = progressValue,
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                        color = if (progressValue > 0.85f) Color(0xFFFFB950) else Color(0xFF2159BC),
                        trackColor = Color(0xFFF1F3F4)
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                IconButton(onClick = onToggle, modifier = Modifier.size(36.dp)) {
                    Icon(if (promotion.status == "active") Icons.Default.Pause else Icons.Default.PlayArrow, null, Modifier.size(18.dp), tint = Color(0xFF5F6368))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, null, Modifier.size(18.dp), tint = Color(0xFFC5221F))
                }
            }
        }
    }
}