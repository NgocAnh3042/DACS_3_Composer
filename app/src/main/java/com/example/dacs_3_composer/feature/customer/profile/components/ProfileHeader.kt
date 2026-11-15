package com.example.dacs_3_composer.feature.customer.profile.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
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
import coil.compose.AsyncImage
import com.example.dacs_3_composer.R

@Composable
fun ProfileHeader(
    name: String,
    email: String,
    avatarState: Any?, // 🌟 SỬA TẠI ĐÂY: Nhận kiểu Any? (Chấp nhận cả Uri tệp điện thoại hoặc String URL)
    onEditAvatarClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(128.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            AsyncImage(
                model = avatarState ?: R.drawable.ic_avatar_default,
                contentDescription = "Ảnh đại diện của $name",
                placeholder = painterResource(id = R.drawable.ic_avatar_default),
                error = painterResource(id = R.drawable.ic_avatar_default),
                modifier = Modifier
                    .size(112.dp)
                    .clip(CircleShape)
                    .border(3.dp, Color.White, CircleShape),
                contentScale = ContentScale.Crop
            )

            IconButton(
                onClick = onEditAvatarClick,
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFF2159BC), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Sửa ảnh",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF191C1D),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = email,
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFF727785),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        }
}
