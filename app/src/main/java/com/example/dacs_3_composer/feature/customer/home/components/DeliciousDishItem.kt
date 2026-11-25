package com.example.dacs_3_composer.feature.customer.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import coil.compose.AsyncImage
import com.example.dacs_3_composer.core.model.Dish
import com.example.dacs_3_composer.core.image.cloudinaryThumbnail
import com.example.dacs_3_composer.R
import java.util.Locale

//          Vẽ giao diện có colum bao quanh có ảnh tên..vv
@Composable
fun DeliciousDishItem(
    dish: Dish,
    modifier: Modifier = Modifier,
    isRecommended: Boolean = false,
    onClick: () -> Unit
) {
    val imageWidthPx = with(LocalDensity.current) { 280.dp.roundToPx() }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(modifier = Modifier.fillMaxWidth()) {
                AsyncImage(
                    model = if (dish.imageUrl.isBlank()) R.drawable.food_pho_v2 else cloudinaryThumbnail(dish.imageUrl, imageWidthPx),
                    contentDescription = dish.name,
                    placeholder = painterResource(id = R.drawable.food_pho_v2),
                    error = painterResource(id = R.drawable.food_pho_v2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.4f)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
                if (isRecommended) {
                    Text(
                        text = "Gợi ý",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .background(Color(0xD92159BC), RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = dish.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF191C1D),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = dish.description.ifBlank { dish.category },
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF727785),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (dish.price > 0.0) {
                Text(
                    text = String.format(Locale.forLanguageTag("vi-VN"), "%,.0f đ", dish.price),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2159BC),
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}
