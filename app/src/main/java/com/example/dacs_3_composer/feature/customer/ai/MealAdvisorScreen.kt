package com.example.dacs_3_composer.feature.customer.ai

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShoppingBag
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.dacs_3_composer.core.model.Dish
import com.example.dacs_3_composer.data.repository.*
import java.text.NumberFormat
import java.util.Locale

private val Blue = Color(0xFF2159BC)
private val SoftBlue = Color(0xFFEAF2FA)
private val Paper = Color(0xFFF5F7FA)
private val Ink = Color(0xFF182536)
private val Secondary = Color(0xFF465568)
private val SuccessGreen = Color(0xFF1E8E5A)
private val SoftGreen = Color(0xFFE8F7EF)
private val WarnAmber = Color(0xFFB45309)
private val SoftAmber = Color(0xFFFEF3C7)

private fun money(value: Number) = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN")).format(value) + "đ"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealAdvisorScreen(
    onBack: () -> Unit,
    onOpenDish: (String) -> Unit,
    viewModel: MealAdvisorViewModel = viewModel(),
    staff: Boolean = false,
    onSelectDish: ((Dish) -> Unit)? = null,
    onTrackOrder: (String) -> Unit = {},
    onPayOrder: (String, Double) -> Unit = { _, _ -> },
    onManageAddresses: () -> Unit = {}
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
                            Text(
                                text = if (staff) "Hỗ trợ vận hành" else "Tư vấn & đặt món qua trò chuyện",
                                style = MaterialTheme.typography.bodySmall,
                                color = Secondary
                            )
                        }
                    }
                },
                actions = {
                    if (!staff) {
                        IconButton(onClick = { showBudget = true }) { Icon(Icons.Filled.Tune, "Chỉnh ngân sách", tint = Blue) }
                    }
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
                        Bubble(
                            if (staff) "Chào bạn! Mình có thể hỗ trợ vận hành gì hôm nay?"
                            else "Chào bạn! Bạn có thể nhờ mình tìm món, lên giỏ nháp, tối ưu voucher và đặt món trực tiếp ngay tại đây nhé 💙"
                        )
                        if (turns.isEmpty()) {
                            val starterPrompts = if (staff) {
                                listOf("Trợ giúp", "Báo cáo", "Báo sự cố")
                            } else {
                                listOf("Đặt giúp mình 1 phần cơm tấm", "Đặt lại y hệt đơn hôm qua", "Xem thực đơn", "Gợi ý một khẩu phần ăn trưa")
                            }
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!staff) {
                                    AssistChip(
                                        onClick = { viewModel.reorderLastOrder() },
                                        enabled = !busy,
                                        modifier = Modifier.testTag("ai_reorder_last_chip"),
                                        leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null, tint = Blue, modifier = Modifier.size(16.dp)) },
                                        label = { Text("Đặt lại đơn cũ", color = Blue, fontWeight = FontWeight.SemiBold) },
                                        colors = AssistChipDefaults.assistChipColors(containerColor = SoftBlue)
                                    )
                                }
                                starterPrompts.forEach { prompt ->
                                    SuggestionChip(onClick = { viewModel.send(prompt) }, label = { Text(prompt, color = Blue) })
                                }
                            }
                            Text("Mọi giá món, ưu đãi voucher, hẹn giờ và đơn hàng đều được máy chủ kiểm chứng trước khi đặt.", style = MaterialTheme.typography.bodySmall, color = Secondary)
                        }
                    }
                }
                itemsIndexed(turns, key = { index, _ -> index }) { index, turn ->
                    val isLatestTurn = index == turns.lastIndex
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.align(Alignment.End)) { Bubble(turn.question, user = true) }
                        Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val answer = turn.answer
                            when {
                                answer != null -> {
                                    Bubble(answer.message)
                                    val sourceBadge = when (answer.source) {
                                        "ordering_agent" -> "Trợ lý Đặt món FoodHub · Báo giá xác thực"
                                        "groq", "cache" -> "GPT-OSS 120B · Thực đơn FoodHub"
                                        "support" -> "Hỗ trợ FoodHub"
                                        else -> "Thực đơn FoodHub"
                                    }
                                    Text(sourceBadge, style = MaterialTheme.typography.labelSmall, color = Secondary)

                                    answer.recommendations.forEach { rec ->
                                        DishCard(
                                            item = rec,
                                            fee = if (answer.kind == "meal") 0.0 else answer.deliveryFee,
                                            showQuickOrder = !staff,
                                            enabled = !busy,
                                            onOpenDish = onOpenDish,
                                            onSelectDish = onSelectDish,
                                            onQuickOrder = { dish -> viewModel.quickOrderDish(dish.id, dish.name, 1) }
                                        )
                                    }

                                    // Store conflict resolution card (single-restaurant order constraint)
                                    if (isLatestTurn && answer.storeConflict != null) {
                                        StoreConflictCard(
                                            conflict = answer.storeConflict,
                                            enabled = !busy,
                                            onSwitchStore = {
                                                viewModel.replaceStoreWithDish(
                                                    dishId = answer.storeConflict.targetDishId,
                                                    dishName = answer.recommendations.firstOrNull()?.dish?.name ?: "món mới",
                                                    quantity = answer.storeConflict.quantity
                                                )
                                            },
                                            onKeepCurrent = { viewModel.refreshDraftQuote() }
                                        )
                                    }

                                    // Interactive Checkout Summary & Mandatory Confirmation Card on the active turn
                                    if (isLatestTurn && answer.orderQuote != null && answer.createdOrder == null) {
                                        CheckoutSummaryCard(
                                            quote = answer.orderQuote,
                                            draft = answer.orderDraft,
                                            addresses = answer.availableAddresses,
                                            vouchers = answer.availableVouchers,
                                            bestVoucher = answer.bestVoucher,
                                            busy = busy,
                                            onQuantityChange = { lineId, name, qty -> viewModel.updateDraftItemQuantity(lineId, name, qty) },
                                            onSelectAddress = { addr -> viewModel.selectDraftAddress(addr) },
                                            onManageAddresses = onManageAddresses,
                                            onSelectVoucher = { code, auto, label -> viewModel.selectDraftVoucher(code, auto, label) },
                                            onSelectPayment = { method, label -> viewModel.selectDraftPayment(method, label) },
                                            onAcceptUpsell = { upsell -> viewModel.acceptUpsell(upsell) },
                                            onSelectSchedule = { iso, label -> viewModel.selectScheduleSlot(iso, label) },
                                            onEditFocus = { viewModel.setMessage("Cho mình đổi ") },
                                            onRefreshQuote = { viewModel.refreshDraftQuote() },
                                            onCancelDraft = { viewModel.cancelActiveDraft() },
                                            onConfirmOrder = { viewModel.confirmOrder(answer.orderQuote) }
                                        )
                                    }

                                    // Created Order Success / Online Payment Card
                                    if (answer.createdOrder != null) {
                                        OrderCreatedSuccessCard(
                                            order = answer.createdOrder,
                                            onTrackOrder = { onTrackOrder(answer.createdOrder.id) },
                                            onPayNow = { onPayOrder(answer.createdOrder.id, answer.createdOrder.totalVnd.toDouble()) }
                                        )
                                    }

                                    if (answer.hasMore && isLatestTurn) {
                                        OutlinedButton(onClick = { viewModel.send("Xem tiếp menu") }, enabled = !busy) { Text("Xem tiếp menu") }
                                    }
                                    if (isLatestTurn && answer.orderQuote == null && answer.createdOrder == null && !answer.message.startsWith("Đã ghi nhận góp ý")) {
                                        Row {
                                            listOf("Hữu ích", "Chưa giải quyết").forEach { rating ->
                                                TextButton(onClick = { viewModel.send("Đánh giá: $rating") }, enabled = !busy) { Text(rating) }
                                            }
                                        }
                                    }
                                    if (answer.catalogLimited) Text("Đang xét tối đa 80 món phù hợp nhất trong thực đơn.", style = MaterialTheme.typography.bodySmall)
                                }
                                turn.error != null -> {
                                    Bubble(turn.error)
                                    if (isLatestTurn) TextButton(onClick = viewModel::retry) { Text("Gửi lại") }
                                }
                                else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Blue)
                                    Text("Đang kiểm tra & xử lý thông tin…", style = MaterialTheme.typography.bodyMedium)
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
                        placeholder = { Text(if (staff) "Nhắn cho FoodHub AI…" else "Nhắn tìm món hoặc đặt đơn (vd: Đặt 2 ly trà sữa)…", color = Secondary) },
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
            modifier = Modifier.widthIn(max = (maxWidth * if (user) .82f else .94f).coerceAtMost(500.dp)),
            color = if (user) Blue else Color.White,
            contentColor = if (user) Color.White else Ink,
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = if (user) 18.dp else 4.dp, bottomEnd = if (user) 4.dp else 18.dp)
        ) { Text(text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), style = MaterialTheme.typography.bodyLarge) }
    }
}

@Composable
private fun DishCard(
    item: MealRecommendation,
    fee: Double,
    showQuickOrder: Boolean,
    enabled: Boolean,
    onOpenDish: (String) -> Unit,
    onSelectDish: ((Dish) -> Unit)? = null,
    onQuickOrder: (Dish) -> Unit = {}
) {
    Surface(
        onClick = {
            onSelectDish?.invoke(item.dish)
            onOpenDish(item.dish.id)
        },
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        contentColor = Ink,
        border = BorderStroke(1.dp, Color(0xFFE6ECF5))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (item.dish.imageUrl.isNotBlank()) {
                AsyncImage(
                    model = item.dish.imageUrl,
                    contentDescription = null,
                    modifier = Modifier.size(62.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            Column(Modifier.weight(1f)) {
                Text(item.dish.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(item.restaurantName, style = MaterialTheme.typography.bodySmall, color = Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(money(item.dish.price), color = Blue, fontWeight = FontWeight.Bold)
                if (fee > 0) Text("${money(item.estimatedTotal)} gồm phí giao", style = MaterialTheme.typography.bodySmall, color = Secondary)
            }
            if (showQuickOrder && item.dish.id.isNotBlank()) {
                FilledTonalButton(
                    onClick = { onQuickOrder(item.dish) },
                    enabled = enabled,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = SoftBlue, contentColor = Blue)
                ) {
                    Icon(Icons.Filled.ShoppingBag, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Chọn đặt", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            } else {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Xem chi tiết món ăn", tint = Blue)
            }
        }
    }
}

@Composable
private fun StoreConflictCard(
    conflict: AiStoreConflict,
    enabled: Boolean,
    onSwitchStore: () -> Unit,
    onKeepCurrent: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SoftAmber,
        border = BorderStroke(1.dp, WarnAmber.copy(alpha = 0.35f))
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Đổi sang nhà hàng ${conflict.targetStoreName}?",
                fontWeight = FontWeight.Bold,
                color = WarnAmber
            )
            Text(
                text = "Mỗi đơn trên FoodHub đặt từ 1 quán. Bạn muốn làm mới đơn nháp để đặt tại ${conflict.targetStoreName} hay giữ đơn của ${conflict.currentStoreName}?",
                style = MaterialTheme.typography.bodySmall,
                color = Ink
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onKeepCurrent, enabled = enabled, modifier = Modifier.weight(1f)) {
                    Text("Giữ quán cũ")
                }
                Button(
                    onClick = onSwitchStore,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Blue)
                ) {
                    Text("Đổi sang quán mới")
                }
            }
        }
    }
}

@Composable
private fun CheckoutSummaryCard(
    quote: AiOrderQuote,
    draft: AiOrderDraft?,
    addresses: List<AiAddressOption>,
    vouchers: List<AiVoucherOption>,
    bestVoucher: AiVoucherOption?,
    busy: Boolean,
    onQuantityChange: (String, String, Int) -> Unit,
    onSelectAddress: (AiAddressOption) -> Unit,
    onManageAddresses: () -> Unit,
    onSelectVoucher: (String?, Boolean, String) -> Unit,
    onSelectPayment: (String, String) -> Unit,
    onAcceptUpsell: (AiUpsellSuggestion) -> Unit = {},
    onSelectSchedule: (String?, String) -> Unit = { _, _ -> },
    onEditFocus: () -> Unit,
    onRefreshQuote: () -> Unit,
    onCancelDraft: () -> Unit,
    onConfirmOrder: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("ai_checkout_summary_card"),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.5.dp, Blue.copy(alpha = 0.3f)),
        shadowElevation = 2.dp
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // 1. Header
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (!quote.reorderedFromOrderId.isNullOrBlank()) "Đặt lại đơn cũ · Giá hiện hành" else "Tóm tắt đơn đặt món",
                        style = MaterialTheme.typography.labelMedium,
                        color = Blue,
                        fontWeight = FontWeight.Bold
                    )
                    Text(quote.restaurantName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Ink)
                }
                Surface(color = SoftBlue, shape = RoundedCornerShape(10.dp)) {
                    Text(
                        text = if (quote.canConfirmNow) "Sẵn sàng xác nhận · v${quote.draftVersion}" else "Chờ bổ sung thông tin",
                        style = MaterialTheme.typography.labelSmall,
                        color = Blue,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider(color = Color(0xFFEDF1F7))

            // 2. Items with +/- Quantity Controls
            quote.items.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(item.name, fontWeight = FontWeight.SemiBold, color = Ink)
                        val optNames = item.selectedOptions.joinToString(", ") { it.name }
                        if (optNames.isNotEmpty()) {
                            Text("Tùy chọn: $optNames", style = MaterialTheme.typography.bodySmall, color = Secondary)
                        }
                        if (item.note.isNotBlank()) {
                            Text("Ghi chú: ${item.note}", style = MaterialTheme.typography.bodySmall, color = Blue)
                        }
                        Text("${money(item.unitPriceVnd)} / phần", style = MaterialTheme.typography.bodySmall, color = Secondary)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        IconButton(
                            onClick = { onQuantityChange(item.lineId, item.name, item.quantity - 1) },
                            enabled = !busy,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(Icons.Filled.Remove, contentDescription = "Giảm số lượng", tint = Blue, modifier = Modifier.size(16.dp))
                        }
                        Text("${item.quantity}", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
                        IconButton(
                            onClick = { onQuantityChange(item.lineId, item.name, item.quantity + 1) },
                            enabled = !busy,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Tăng số lượng", tint = Blue, modifier = Modifier.size(16.dp))
                        }
                    }
                    Text(money(item.lineTotalVnd), fontWeight = FontWeight.Bold, color = Ink)
                }
            }

            HorizontalDivider(color = Color(0xFFEDF1F7))

            // 3. Delivery Address Selection
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Blue, modifier = Modifier.size(16.dp))
                    Text("Địa chỉ giao hàng", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
                if (quote.address != null) {
                    Text(
                        text = "${quote.address.receiverName} (${quote.address.receiverPhone}) · ${quote.address.addressLine}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Secondary
                    )
                } else {
                    Text("Chưa có địa chỉ nhận hàng hợp lệ.", style = MaterialTheme.typography.bodySmall, color = WarnAmber)
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    addresses.forEach { addr ->
                        val selected = quote.address?.id == addr.id
                        FilterChip(
                            selected = selected,
                            onClick = { if (!selected) onSelectAddress(addr) },
                            enabled = !busy,
                            label = { Text(addr.label, maxLines = 1) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Blue,
                                selectedLabelColor = Color.White,
                                containerColor = Paper
                            ),
                            border = null
                        )
                    }
                    AssistChip(
                        onClick = onManageAddresses,
                        enabled = !busy,
                        label = { Text(if (addresses.isEmpty()) "+ Thêm địa chỉ nhận" else "Quản lý địa chỉ", color = Blue) }
                    )
                }
            }

            // 4. Voucher Optimization Chips
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Ưu đãi & Voucher", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                if (vouchers.isEmpty()) {
                    Text("Hiện chưa có voucher phù hợp cho giá trị đơn này.", style = MaterialTheme.typography.bodySmall, color = Secondary)
                } else {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        vouchers.forEach { v ->
                            val isApplied = quote.appliedVoucher?.code == v.code
                            val isBest = bestVoucher?.code == v.code
                            FilterChip(
                                selected = isApplied,
                                onClick = {
                                    onSelectVoucher(v.code, false, "Áp dụng mã ${v.code} (-${money(v.actualDiscountVnd)})")
                                },
                                enabled = !busy,
                                label = {
                                    Text(
                                        text = (if (isBest) "Tốt nhất: " else "") + "${v.code} (-${money(v.actualDiscountVnd)})",
                                        fontWeight = if (isApplied) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Blue,
                                    selectedLabelColor = Color.White,
                                    containerColor = SoftBlue
                                ),
                                border = null
                            )
                        }
                        val noneSelected = draft?.voucherCode == "NONE" || quote.appliedVoucher == null
                        FilterChip(
                            selected = noneSelected,
                            onClick = { onSelectVoucher("NONE", false, "Giữ lại voucher, không dùng mã cho đơn này") },
                            enabled = !busy,
                            label = { Text("Không dùng mã") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Ink,
                                selectedLabelColor = Color.White,
                                containerColor = Paper
                            ),
                            border = null
                        )
                    }
                }
            }

            // 4b. Consent-Based Smart Voucher Upselling Card (NEVER auto-added without user consent)
            quote.upsellSuggestion?.let { upsell ->
                Surface(
                    modifier = Modifier.fillMaxWidth().testTag("ai_upsell_suggestion_card"),
                    shape = RoundedCornerShape(14.dp),
                    color = if (upsell.isStrictlyCheaper) SoftGreen else SoftBlue,
                    border = BorderStroke(1.dp, (if (upsell.isStrictlyCheaper) SuccessGreen else Blue).copy(alpha = 0.35f))
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = upsell.headline.ifBlank { "Gợi ý thêm ${upsell.dishName} để mở mã ${upsell.targetVoucherCode}" },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (upsell.isStrictlyCheaper) SuccessGreen else Blue
                        )
                        Text(
                            text = upsell.reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = Ink
                        )
                        FilledTonalButton(
                            onClick = { onAcceptUpsell(upsell) },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth().testTag("ai_accept_upsell_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (upsell.isStrictlyCheaper) SuccessGreen else Blue,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Thêm ${upsell.dishName} & Áp mã ${upsell.targetVoucherCode}",
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // 5. Payment Method Selection (COD vs PayOS)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Phương thức thanh toán", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val isCod = quote.paymentMethod != "PAYOS"
                    FilterChip(
                        selected = isCod,
                        onClick = { if (!isCod) onSelectPayment("cash", "Tiền mặt khi nhận hàng (COD)") },
                        enabled = !busy,
                        label = { Text("Tiền mặt (COD)") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Blue, selectedLabelColor = Color.White, containerColor = Paper),
                        border = null
                    )
                    FilterChip(
                        selected = !isCod,
                        onClick = { if (isCod) onSelectPayment("payos", "Chuyển khoản QR (PayOS)") },
                        enabled = !busy,
                        label = { Text("Chuyển khoản QR (PayOS)") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Blue, selectedLabelColor = Color.White, containerColor = Paper),
                        border = null
                    )
                }
            }

            // 6. Scheduled Delivery & ETA Display
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = Blue, modifier = Modifier.size(16.dp))
                    Text(
                        text = "Thời gian nhận hàng" + (quote.storeOpenHours?.let { " · Mở cửa $it" } ?: ""),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val isNow = quote.scheduledFor.isNullOrBlank()
                    FilterChip(
                        selected = isNow,
                        onClick = { if (!isNow) onSelectSchedule(null, "Giao ngay") },
                        enabled = !busy,
                        label = { Text("Giao ngay") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Blue, selectedLabelColor = Color.White, containerColor = Paper),
                        border = null
                    )
                    quote.availableScheduleSlots.forEach { slot ->
                        val isSlotSelected = quote.scheduledFor == slot.iso
                        FilterChip(
                            selected = isSlotSelected,
                            onClick = { if (!isSlotSelected) onSelectSchedule(slot.iso, slot.label) },
                            enabled = !busy,
                            label = { Text(slot.label, maxLines = 1) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Blue, selectedLabelColor = Color.White, containerColor = SoftBlue),
                            border = null
                        )
                    }
                }
                Surface(color = Paper, shape = RoundedCornerShape(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val eta = quote.estimatedDeliveryMinutes
                        Text(
                            text = when {
                                !quote.scheduledForDisplay.isNullOrBlank() ->
                                    "Lịch hẹn giao hàng: ${quote.scheduledForDisplay} (nhà hàng chuẩn bị theo giờ hẹn)"
                                eta != null && eta.available && !eta.displayText.isNullOrBlank() ->
                                    "Thời gian giao dự kiến: ${eta.displayText} (ước tính theo khoảng cách ${eta.distanceKm} km)"
                                else ->
                                    eta?.note ?: "Chưa đủ tọa độ để ước tính thời gian giao chính xác."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (!quote.scheduledForDisplay.isNullOrBlank()) Blue else Secondary,
                            fontWeight = if (!quote.scheduledForDisplay.isNullOrBlank()) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }

            HorizontalDivider(color = Color(0xFFEDF1F7))

            // 7. Server-Calculated Price Summary
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PriceLine("Tạm tính tiền món", money(quote.subtotal))
                PriceLine("Phí vận chuyển", money(quote.deliveryFee))
                if (quote.discount > 0) {
                    PriceLine(
                        label = "Ưu đãi voucher" + (quote.appliedVoucher?.code?.let { " ($it)" } ?: ""),
                        value = "-${money(quote.discount)}",
                        highlight = SuccessGreen
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Tổng thanh toán", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Ink)
                    Text(money(quote.total), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Blue)
                }
            }

            // 8. Mandatory Confirmation Controls
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onEditFocus,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Chỉnh sửa đơn", maxLines = 1)
                }
                OutlinedButton(
                    onClick = onCancelDraft,
                    enabled = !busy,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC53030))
                ) {
                    Text("Hủy")
                }
                IconButton(onClick = onRefreshQuote, enabled = !busy) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Làm mới báo giá", tint = Blue)
                }
            }

            Button(
                onClick = onConfirmOrder,
                enabled = !busy && quote.canConfirmNow && !quote.quoteId.isNullOrBlank() && !quote.confirmationToken.isNullOrBlank(),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("ai_confirm_order_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Blue, contentColor = Color.White)
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Đang xác nhận tạo đơn…", fontWeight = FontWeight.Bold)
                } else {
                    Text(
                        text = if (quote.canConfirmNow) "Xác nhận đặt hàng — ${money(quote.total)}" else "Chọn địa chỉ giao để xác nhận đơn",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun PriceLine(label: String, value: String, highlight: Color = Secondary) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Secondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = highlight)
    }
}

@Composable
private fun OrderCreatedSuccessCard(
    order: AiCreatedOrder,
    onTrackOrder: () -> Unit,
    onPayNow: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("ai_order_created_card"),
        shape = RoundedCornerShape(20.dp),
        color = SoftGreen,
        border = BorderStroke(1.5.dp, SuccessGreen.copy(alpha = 0.4f))
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(28.dp))
                Column {
                    Text(
                        text = if (order.requiresOnlinePayment) "Đã tạo đơn · Chờ thanh toán QR" else "Đặt hàng thành công!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SuccessGreen
                    )
                    Text("Mã đơn: #${order.code}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Ink)
                }
            }
            Text(
                text = "${order.restaurantName} · Tổng thanh toán: ${money(order.totalVnd)} (${order.paymentMethod})",
                style = MaterialTheme.typography.bodySmall,
                color = Ink
            )
            if (order.deliveryAddress.isNotBlank()) {
                Text("Giao tới: ${order.deliveryAddress}", style = MaterialTheme.typography.bodySmall, color = Secondary)
            }
            if (!order.scheduledForDisplay.isNullOrBlank()) {
                Text("Lịch hẹn giao: ${order.scheduledForDisplay}", style = MaterialTheme.typography.bodySmall, color = Blue, fontWeight = FontWeight.SemiBold)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (order.requiresOnlinePayment) {
                    Button(
                        onClick = onPayNow,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Blue)
                    ) {
                        Text("Thanh toán QR PayOS", fontWeight = FontWeight.Bold)
                    }
                }
                OutlinedButton(
                    onClick = onTrackOrder,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Theo dõi đơn", color = SuccessGreen, fontWeight = FontWeight.Bold)
                }
            }
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
