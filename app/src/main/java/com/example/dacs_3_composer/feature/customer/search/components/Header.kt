package com.example.dacs_3_composer.feature.customer.search.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchHeader(
    location: String,
    brandName: String,
    onSearchClick: () -> Unit,
    onMessageClick: () -> Unit = {}, // 🎯 Thêm callback nhắn tin
    modifier: Modifier = Modifier
) {
    TopAppBar(
        windowInsets = WindowInsets(0.dp),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.White
        ),
        title = {
            Row(
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Vị trí",
                        tint = Color(0xFF2159BC),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = location,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        maxLines = 1
                    )
                }

                Text(
                    text = brandName,
                    color = Color(0xFF2159BC),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Row {
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Default.Search, null, tint = Color(0xFF2159BC), modifier = Modifier.size(22.dp))
                    }
                    IconButton(onClick = onMessageClick) {
                        Icon(Icons.AutoMirrored.Filled.Chat, null, tint = Color(0xFF2159BC), modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    )
}