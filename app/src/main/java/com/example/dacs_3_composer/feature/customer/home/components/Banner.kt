package com.example.dacs_3_composer.feature.customer.home.components

import coil.compose.AsyncImage
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dacs_3_composer.core.model.Promotion
import java.text.NumberFormat
import java.util.Locale

@Composable
fun Banner(promotions: List<Promotion>) {
    if (promotions.isEmpty()) return
    val pager = rememberPagerState { promotions.size }
    val money = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalPager(state = pager, key = { promotions[it].id }, pageSpacing = 12.dp) { index ->
            val offer = promotions[index]
            val colors = when (offer.code) {
                "FHBANHMI10" -> listOf(Color(0xFFE65A1C), Color(0xFFAA300E))
                "FHCOM15" -> listOf(Color(0xFF397E57), Color(0xFF174935))
                "FHTRASUA12" -> listOf(Color(0xFF8A549E), Color(0xFF4F2864))
                "FHBUN20" -> listOf(Color(0xFFD64A36), Color(0xFF8F2724))
                else -> listOf(Color(0xFF376FD2), Color(0xFF173D83))
            }
            val benefit = when (offer.type) {
                "free_shipping" -> "MIỄN PHÍ\nGIAO HÀNG"
                "fixed" -> "GIẢM ${money.format(offer.value / 1000)}K"
                else -> "GIẢM ${money.format(offer.value)}%"
            }
            BoxWithConstraints(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(colors)).heightIn(min = 240.dp)
            ) {
                if (offer.imageUrl.isNotBlank()) AsyncImage(
                    model = offer.imageUrl, contentDescription = null, contentScale = ContentScale.Fit,
                    modifier = Modifier.size(maxWidth * .66f).align(Alignment.BottomEnd)
                        .offset(x = 32.dp, y = 30.dp).rotate(12f)
                )
                Column(Modifier.fillMaxWidth(.72f).padding(start = 22.dp, top = 22.dp, bottom = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = .18f), contentColor = Color.White) {
                        Text(offer.title.ifBlank { "Ưu đãi FoodHub" }, Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium)
                    }
                    Text(benefit, color = Color.White, fontSize = 30.sp, lineHeight = 33.sp,
                        fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
                    Column(Modifier.fillMaxWidth(.85f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        if (offer.minOrderValue > 0) Text("Đơn từ ${money.format(offer.minOrderValue)}đ",
                            color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        if (offer.maxDiscount > 0) Text("Giảm tối đa ${money.format(offer.maxDiscount)}đ",
                            color = Color.White.copy(alpha = .88f), style = MaterialTheme.typography.bodySmall)
                    }
                    Surface(shape = RoundedCornerShape(50), color = Color.White, contentColor = colors.last()) {
                        Text(offer.code, Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (promotions.size > 1) Text("Ưu đãi ${pager.currentPage + 1}/${promotions.size}",
            modifier = Modifier.align(Alignment.End), style = MaterialTheme.typography.labelSmall)
    }
}
