package com.example.dacs_3_composer.feature.customer.payment

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dacs_3_composer.core.model.Payment
import com.example.dacs_3_composer.data.remote.FoodHubClient
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentHistoryScreen(onBackClick: () -> Unit) {
    var payments by remember { mutableStateOf<List<Payment>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val localeVN = remember { Locale("vi", "VN") }

    LaunchedEffect(Unit) {
        while (true) {
            runCatching { FoodHubClient.list<Payment>("payments") }
                .onSuccess { payments = it }
            isLoading = false
            delay(10_000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Lịch sử thanh toán",
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = Color(0xFF191C1D)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) { 
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại") 
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF8F9FA))
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).background(Color(0xFFF8F9FA))) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color(0xFF2159BC))
            } else if (payments.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.ReceiptLong,
                        contentDescription = null,
                        tint = Color(0xFF2159BC),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Chưa có giao dịch nào", fontWeight = FontWeight.SemiBold, color = Color(0xFF191C1D))
                    Text("Các khoản thanh toán sẽ xuất hiện tại đây.", color = Color(0xFF727785), fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(payments) { payment ->
                        PaymentItemCard(payment, localeVN)
                    }
                }
            }
        }
    }
}

@Composable
fun PaymentItemCard(payment: Payment, locale: Locale) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Mã đơn: #${payment.orderId.takeLast(6).uppercase(locale)}", fontWeight = FontWeight.Bold)
                val statusText = if (payment.paymentStatus == "SUCCESS") "Thành công" else "Đang xử lý"
                val statusColor = if (payment.paymentStatus == "SUCCESS") Color(0xFF2ECC71) else Color(0xFFE67E22)
                
                Surface(color = statusColor.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp)) {
                    Text(
                        text = statusText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Số tiền: ${String.format(locale, "%,.0f", payment.amount)}đ", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2159BC))
            Text(
                text = SimpleDateFormat("dd/MM/yyyy HH:mm", locale).format(Date(payment.createdAt)),
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
    }
}
