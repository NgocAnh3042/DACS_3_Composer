package com.example.dacs_3_composer.feature.customer.payment

import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun PaymentScreen(
    orderId: String,
    amount: Double,
    viewModel: PaymentViewModel,
    onPaymentFinished: () -> Unit
) {
    val context = LocalContext.current
    val checkoutUrl by remember { derivedStateOf { viewModel.checkoutUrl } }
    val isSuccess by remember { derivedStateOf { viewModel.isPaymentSuccess } }

    // Tự động khởi tạo thanh toán khi mở màn hình
    LaunchedEffect(orderId) {
        if (!isSuccess && checkoutUrl == null) {
            viewModel.createPayment(orderId, amount, "Thanh toán đơn hàng #$orderId")
        }
    }

    // Tự động mở trình duyệt thanh toán khi nhận được Link từ Backend nội bộ
    LaunchedEffect(checkoutUrl) {
        checkoutUrl?.let { url ->
            try {
                val intent = CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .build()
                intent.launchUrl(context, Uri.parse(url))
            } catch (e: Exception) {
                Log.e("PaymentScreen", "Không thể mở trình duyệt: ${e.message}")
            }
        }
    }

    LaunchedEffect(isSuccess) {
        if (isSuccess) {
            delay(2500) // Đợi chút để người dùng thấy trạng thái thành công
            onPaymentFinished()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            when {
                isSuccess -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF2ECC71),
                        modifier = Modifier.size(100.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Thanh toán thành công!",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1D1B20)
                    )
                    Text(
                        text = "Đang quay lại trang đơn hàng...",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }

                viewModel.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(50.dp),
                        color = Color(0xFF2159BC),
                        strokeWidth = 4.dp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Đang tạo liên kết thanh toán...",
                        fontWeight = FontWeight.Medium,
                        color = Color.DarkGray
                    )
                    Text(
                        text = "Hệ thống đang giao tiếp với PayOS",
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )
                }

                viewModel.errorMessage != null -> {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = viewModel.errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { viewModel.createPayment(orderId, amount, "Thanh toán đơn hàng #$orderId") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2159BC)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Thử lại thanh toán")
                    }
                }

                checkoutUrl != null -> {
                    // Trạng thái chờ người dùng thanh toán trên trình duyệt
                    CircularProgressIndicator(color = Color(0xFF2ECC71))
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Đang chờ thanh toán...",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Vui lòng hoàn tất giao dịch trên trình duyệt",
                        fontSize = 13.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(40.dp))
                    
                    OutlinedButton(
                        onClick = {
                            val intent = CustomTabsIntent.Builder().build()
                            intent.launchUrl(context, Uri.parse(checkoutUrl!!))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Mở lại trang thanh toán")
                    }
                    
                    TextButton(
                        onClick = { viewModel.resetState() },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("Hủy và thử lại")
                    }
                }
            }
        }
    }
}
