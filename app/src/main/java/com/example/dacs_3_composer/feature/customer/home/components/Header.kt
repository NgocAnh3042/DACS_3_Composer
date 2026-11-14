package com.example.dacs_3_composer.feature.customer.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.dacs_3_composer.R

@Composable
fun Header(
    userName: String,
    userAddress: String,
    userImageUrl: String,
    onSearchAction: (String) -> Unit,
    onChatClick: () -> Unit // 🎯 THÊM: Sự kiện khi bấm vào icon Chat
) {
    var searchQuery by remember { mutableStateOf("") }

    Column(modifier = Modifier.padding(0.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (userName.isNotBlank()) "Xin chào, $userName" else "Chào mừng bạn",
                    color = Color(0xFF191C1D),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFF2159BC),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (userAddress.isNotBlank()) userAddress else "Chưa cập nhật địa chỉ",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF727785),
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 🎯 THÊM: Icon Chat phong cách Restaurant
            IconButton(
                onClick = onChatClick,
                modifier = Modifier.size(48.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFE8F0FE)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = "Tin nhắn hỗ trợ",
                        tint = Color(0xFF2159BC),
                        modifier = Modifier.size(21.dp)
                    )
                }
            }

            AsyncImage(
                model = userImageUrl.ifBlank { R.drawable.ic_avatar_default },
                placeholder = painterResource(id = R.drawable.ic_avatar_default),
                error = painterResource(id = R.drawable.ic_avatar_default),
                contentDescription = "Avatar người dùng",
                modifier = Modifier.size(48.dp).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Tìm kiếm món ăn, nhà hàng...") },
            leadingIcon = {
                IconButton(onClick = { if(searchQuery.isNotBlank()) onSearchAction(searchQuery) }) {
                    Icon(Icons.Default.Search, contentDescription = null)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF2159BC),
                unfocusedBorderColor = Color(0xFFE1E6EF),
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    if (searchQuery.isNotBlank()) {
                        onSearchAction(searchQuery)
                    }
                }
            )
        )
    }
}
