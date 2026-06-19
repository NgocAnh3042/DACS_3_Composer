package com.example.dacs_3_composer.ui.user.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Search
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
                Row {
                    Text("Xin chào, ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        userName,
                        color = Color(0xFF2159BC),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (userAddress.isNotBlank()) "📍 $userAddress" else "📍 Chưa cập nhật địa chỉ",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 🎯 THÊM: Icon Chat phong cách Restaurant
            IconButton(onClick = onChatClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = "Tin nhắn",
                    tint = Color(0xFF191C1D)
                )
            }

            AsyncImage(
                model = userImageUrl.ifBlank { R.drawable.ic_avatar_default },
                placeholder = painterResource(id = R.drawable.ic_avatar_default),
                error = painterResource(id = R.drawable.ic_avatar_default),
                contentDescription = "Avatar người dùng",
                modifier = Modifier.size(50.dp).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

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
            shape = RoundedCornerShape(25.dp),
            singleLine = true,
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
