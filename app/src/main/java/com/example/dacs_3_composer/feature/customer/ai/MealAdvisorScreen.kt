package com.example.dacs_3_composer.feature.customer.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.dacs_3_composer.data.repository.MealRecommendation
import java.text.NumberFormat
import java.util.Locale

private val Blue = Color(0xFF2159BC)
private val Paper = Color(0xFFF5F7FA)
private val Ink = Color(0xFF182536)
private val Secondary = Color(0xFF465568)
private fun money(value: Number) = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN")).format(value) + "đ"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealAdvisorScreen(
    onBack: () -> Unit,
    onOpenRestaurant: (String) -> Unit,
    viewModel: MealAdvisorViewModel = viewModel(),
    staff: Boolean = false
) {
    val message by viewModel.message.collectAsStateWithLifecycle()
    val budget by viewModel.budget.collectAsStateWithLifecycle()
    val delivery by viewModel.includeDelivery.collectAsStateWithLifecycle()
    val turns by viewModel.turns.collectAsStateWithLifecycle()
    val last = turns.lastOrNull()
    val busy = last != null && last.answer == null && last.error == null
    val list = rememberLazyListState()
    var showBudget by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(turns.size, busy) {
        if (turns.isNotEmpty()) list.animateScrollToItem(turns.size)
    }
    Scaffold(
        containerColor = Paper,
        contentColor = Ink,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") } },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(38.dp).background(Blue, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Forum, null, tint = Color.White, modifier = Modifier.size(21.dp))
                        }
                        Column {
                            Text("FoodHub AI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Tư vấn món và hỗ trợ đơn", style = MaterialTheme.typography.bodySmall, color = Secondary)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showBudget = true }) { Icon(Icons.Filled.Tune, "Chỉnh ngân sách", tint = Blue) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White, titleContentColor = Ink, navigationIconContentColor = Ink)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f).testTag("meal_advisor_content"),
                state = list,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Bubble("Chào bạn! Mình có thể giúp gì cho bạn hôm nay?")
                        if (turns.isEmpty()) {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                (if (staff) listOf("Trợ giúp", "Báo cáo", "Báo sự cố") else listOf("Đơn vừa đặt", "Xem thực đơn", "Gợi ý một khẩu phần ăn trưa")).forEach { prompt ->
                                    SuggestionChip(onClick = { viewModel.send(prompt) }, label = { Text(prompt, color = Blue) })
                                }
                            }
                            Text("Tin nhắn có thể được gửi đến Groq để tư vấn.", style = MaterialTheme.typography.bodySmall, color = Secondary)
                        }
                    }
                }
                itemsIndexed(turns, key = { index, _ -> index }) { index, turn ->
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.align(Alignment.End)) { Bubble(turn.question, user = true) }
                        Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val answer = turn.answer
                            when {
                                answer != null -> {
                                    Bubble(answer.message)
                                    Text(if (answer.source == "groq" || answer.source == "cache") "GPT-OSS 120B · Thực đơn FoodHub" else if (answer.source == "support") "Hỗ trợ FoodHub" else "Thực đơn FoodHub",
                                        style = MaterialTheme.typography.labelSmall, color = Secondary)
                                    answer.recommendations.forEach { DishCard(it, if (answer.kind == "meal") 0.0 else answer.deliveryFee, onOpenRestaurant) }
                                    if (answer.hasMore && index == turns.lastIndex) {
                                        OutlinedButton(onClick = { viewModel.send("Xem tiếp menu") }, enabled = !busy) { Text("Xem tiếp menu") }
                                    }
                                    if (index == turns.lastIndex && !answer.message.startsWith("Đã ghi nhận góp ý")) {
                                        Row {
                                            listOf("Hữu ích", "Chưa giải quyết").forEach { rating ->
                                                TextButton(onClick = { viewModel.send("Đánh giá: $rating") }, enabled = !busy) { Text(rating) }
                                            }
                                        }
                                    }
                                    if (answer.catalogLimited) Text("Đang xét tối đa 80 món trong thực đơn.", style = MaterialTheme.typography.bodySmall)
                                }
                                turn.error != null -> {
                                    Bubble(turn.error)
                                    if (index == turns.lastIndex) TextButton(onClick = viewModel::retry) { Text("Gửi lại") }
                                }
                                else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Blue)
                                    Text("Đang kiểm tra thông tin…", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 12.dp, vertical = 8.dp)) {
                budget.toLongOrNull()?.let { amount ->
                    TextButton(onClick = { showBudget = true }) {
                        Text("Tối đa ${money(amount)} · ${if (delivery) "gồm phí giao" else "tiền món"}")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = message, onValueChange = viewModel::setMessage,
                        placeholder = { Text("Nhắn cho FoodHub AI…", color = Secondary) },
                        modifier = Modifier.weight(1f).testTag("meal_chat_input"), maxLines = 3,
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Ink, unfocusedTextColor = Ink,
                            unfocusedContainerColor = Paper, focusedContainerColor = Paper,
                            unfocusedBorderColor = Color.Transparent, focusedBorderColor = Blue)
                    )
                    FilledIconButton(onClick = { viewModel.send() }, enabled = message.isNotBlank() && !busy,
                        modifier = Modifier.size(48.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Blue, contentColor = Color.White)) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Gửi tin nhắn")
                    }
                }
            }
        }
    }
    if (showBudget) BudgetDialog(budget, delivery, onClose = { showBudget = false }) { value, include ->
        viewModel.setBudget(value, include)
        showBudget = false
    }
}

@Composable
private fun Bubble(text: String, user: Boolean = false) {
    BoxWithConstraints {
        Surface(
            modifier = Modifier.widthIn(max = (maxWidth * if (user) .82f else .92f).coerceAtMost(480.dp)),
            color = if (user) Blue else Color.White,
            contentColor = if (user) Color.White else Ink,
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = if (user) 18.dp else 4.dp, bottomEnd = if (user) 4.dp else 18.dp)
        ) { Text(text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), style = MaterialTheme.typography.bodyLarge) }
    }
}

@Composable
private fun DishCard(item: MealRecommendation, fee: Double, onOpen: (String) -> Unit) {
    Surface(onClick = { onOpen(item.dish.restaurantId) }, shape = RoundedCornerShape(14.dp), color = Color.White, contentColor = Ink) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (item.dish.imageUrl.isNotBlank()) AsyncImage(model = item.dish.imageUrl, contentDescription = null,
                modifier = Modifier.size(60.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f)) {
                Text(item.dish.name, fontWeight = FontWeight.SemiBold)
                Text(item.restaurantName, style = MaterialTheme.typography.bodySmall, color = Secondary)
                Text(money(item.dish.price), color = Blue, fontWeight = FontWeight.Bold)
                if (fee > 0) Text("${money(item.estimatedTotal)} gồm phí giao", style = MaterialTheme.typography.bodySmall, color = Secondary)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Xem thực đơn của quán", tint = Blue)
        }
    }
}

@Composable
private fun BudgetDialog(current: String, includesDelivery: Boolean, onClose: () -> Unit, onSave: (String, Boolean) -> Unit) {
    var amount by rememberSaveable { mutableStateOf(current) }
    var include by rememberSaveable { mutableStateOf(includesDelivery) }
    val valid = amount.isEmpty() || amount.toLongOrNull() in 1_000L..10_000_000L
    AlertDialog(onDismissRequest = onClose, title = { Text("Ngân sách mỗi món") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = amount, onValueChange = { amount = it.filter(Char::isDigit).take(8) }, singleLine = true,
                label = { Text("Số tiền tối đa (đ)") }, isError = !valid,
                supportingText = { Text(if (valid) "Để trống nếu bạn muốn xem mọi mức giá." else "Nhập từ 1.000đ đến 10.000.000đ.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tính cả phí giao", Modifier.weight(1f))
                Switch(checked = include, onCheckedChange = { include = it })
            }
        }
    }, confirmButton = { TextButton(onClick = { onSave(amount, include) }, enabled = valid) { Text("Áp dụng") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Đóng") } })
}
