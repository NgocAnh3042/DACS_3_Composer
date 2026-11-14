package com.example.dacs_3_composer.feature.admin.profile.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.example.dacs_3_composer.R

@Composable
fun ProfileHeader(
    adminName: String,
    avatarUrl: String,
    onEditAvatarClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.nha_hang),
            contentDescription = "Cover Pizza",
            modifier = Modifier.fillMaxWidth().height(180.dp),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier.align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(110.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                // Đọc ảnh linh hoạt: Ưu tiên link Cloudinary, rỗng thì dùng drawable local
                if (avatarUrl.isNotBlank()) {
                    SubcomposeAsyncImage(
                        model = avatarUrl,
                        loading = { CircularProgressIndicator(modifier = Modifier.padding(32.dp)) },
                        error = { Image(painter = painterResource(id = R.drawable.ic_avatar_default), contentDescription = null) },
                        contentDescription = "Admin Avatar",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .border(4.dp, Color.White, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.ic_avatar_default),
                        contentDescription = "Admin Avatar Default",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .border(4.dp, Color.White, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }

                IconButton(
                    onClick = onEditAvatarClick,
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color(0xFF0052CC), CircleShape)
                        .border(2.dp, Color.White, CircleShape)
                ) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Avatar", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = adminName, // Tự động hiển thị tên thật hoặc chữ "Admin"
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF002266)
            )
        }
    }
}