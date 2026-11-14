package com.example.dacs_3_composer.feature.restaurant.profile.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.dacs_3_composer.R

@Composable
fun ProfileImages(
    coverUrl: String, // 🎯 ĐỔI THÀNH STRING URL ĐỂ LẤY TỪ COLLECTION RESTAURANTS
    avatarUrl: String,
    onEditCoverClick: () -> Unit, // Khôi phục nút sửa ảnh bìa
    onEditAvatarClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(210.dp)
    ) {
        // Khối Ảnh bìa lấy từ database trực tuyến
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
        ) {
            AsyncImage(
                model = coverUrl.ifBlank { "https://res.cloudinary.com/dhscw17vq/image/upload/v1710000000/sample.jpg" },
                placeholder = painterResource(id = R.drawable.banner1),
                error = painterResource(id = R.drawable.banner1),
                contentDescription = "Ảnh bìa nhà hàng",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Nút bấm thay đổi ảnh bìa
            IconButton(
                onClick = onEditCoverClick,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 8.dp)
                    .size(32.dp)
                    .background(Color(0xFF2159BC), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = "Edit Cover",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Khối Ảnh đại diện (Avatar)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(110.dp)
        ) {
            AsyncImage(
                model = avatarUrl.ifBlank { R.drawable.banner1 },
                placeholder = painterResource(id = R.drawable.banner1),
                error = painterResource(id = R.drawable.banner1),
                contentDescription = "Profile Avatar",
                modifier = Modifier
                    .size(110.dp)
                    .border(4.dp, Color.White, CircleShape)
                    .border(6.dp, Color(0xFF2159BC).copy(alpha = 0.8f), CircleShape)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )

            IconButton(
                onClick = onEditAvatarClick,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(28.dp)
                    .background(Color(0xFF2159BC), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = "Edit Avatar",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}