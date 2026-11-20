package com.example.dacs_3_composer.feature.customer.home.components

import coil.compose.AsyncImage
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.dacs_3_composer.core.model.Promotion
import java.text.NumberFormat
import java.util.Locale

@Composable
fun Banner(promotions: List<Promotion>) {
    if (promotions.isEmpty()) return
    val pager = rememberPagerState { promotions.size }
    val money = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HorizontalPager(state = pager, key = { promotions[it].id }, pageSpacing = 8.dp) { index ->
            val offer = promotions[index]
            val background = when (index % 3) {
                0 -> Color(0xFF143B67)
                1 -> Color(0xFF25554C)
                else -> Color(0xFF72402D)
            }
            val benefit = when (offer.type) {
                "free_shipping" -> "Miễn phí giao hàng"
                "fixed" -> "Giảm ${money.format(offer.value)} đ"
                else -> "Giảm ${money.format(offer.value)}%"
            }
            Surface(shape = RoundedCornerShape(20.dp), color = background, contentColor = Color.White) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.LocalOffer, null, Modifier.size(18.dp))
                            Text(offer.title.ifBlank { "Ưu đãi FoodHub" }, style = MaterialTheme.typography.labelLarge)
                        }
                        Text(benefit, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        if (offer.minOrderValue > 0 || offer.maxDiscount > 0) {
                            Text(listOfNotNull(
                                if (offer.minOrderValue > 0) "Đơn từ ${money.format(offer.minOrderValue)} đ" else null,
                                if (offer.maxDiscount > 0) "tối đa ${money.format(offer.maxDiscount)} đ" else null
                            ).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                        }
                        Surface(shape = RoundedCornerShape(6.dp), color = Color.White.copy(alpha = .16f), contentColor = Color.White) {
                            Text(offer.code, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    if (offer.imageUrl.isNotBlank()) {
                        AsyncImage(
                            model = offer.imageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth(0.30f).aspectRatio(.84f).clip(RoundedCornerShape(14.dp))
                        )
                    }
                }
            }
        }
        if (promotions.size > 1) Text(
            "Ưu đãi ${pager.currentPage + 1}/${promotions.size}",
            modifier = Modifier.align(Alignment.End), style = MaterialTheme.typography.labelSmall
        )
    }
}
