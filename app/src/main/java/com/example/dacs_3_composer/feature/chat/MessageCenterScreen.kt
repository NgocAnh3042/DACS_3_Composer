package com.example.dacs_3_composer.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.dacs_3_composer.core.model.Conversation
import com.example.dacs_3_composer.core.model.User
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageCenterScreen(
    onConversationClick: (String) -> Unit,
    onBackClick: () -> Unit
) {
    val viewModel: ChatViewModel = viewModel(factory = ChatViewModel.Factory)
    val allConversations by viewModel.conversations.collectAsState()
    val usersInfo by viewModel.usersInfo.collectAsState()
    val userRole by viewModel.userRole.collectAsState()
    val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("Tất cả") }

    val filters = remember(userRole) {
        val list = mutableListOf("Tất cả")
        val role = userRole.lowercase()
        if (role != "user") list.add("Khách hàng")
        if (role != "restaurant") list.add("Nhà hàng")
        if (role != "shipper") list.add("Shipper")
        if (role != "admin") list.add("Hỗ trợ")
        list.add("Chưa đọc")
        list
    }

    val filteredConversations = remember(allConversations, selectedFilter, searchQuery, currentUid) {
        allConversations.filter { conv ->
            val matchesFilter = when (selectedFilter) {
                "Shipper" -> conv.participantRoles.any { it.key != currentUid && it.value.lowercase() == "shipper" }
                "Khách hàng" -> conv.participantRoles.any { it.key != currentUid && it.value.lowercase() == "user" }
                "Nhà hàng" -> conv.participantRoles.any { it.key != currentUid && it.value.lowercase() == "restaurant" }
                "Hỗ trợ" -> conv.type == "SUPPORT"
                "Chưa đọc" -> (conv.unreadCount[currentUid] ?: 0) > 0
                else -> true
            }
            val matchesSearch = conv.lastMessage.contains(searchQuery, ignoreCase = true)
            matchesFilter && matchesSearch
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = Color.White,
                shadowElevation = 8.dp,
                modifier = Modifier.clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            ) {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Tin nhắn", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF005BBF))
                            Box(Modifier.width(24.dp).height(3.dp).background(Color(0xFF005BBF), CircleShape))
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color(0xFF005BBF))
                        }
                    },
                    actions = {
                        IconButton(onClick = {}) { Icon(Icons.Default.MoreVert, "More", tint = Color(0xFF005BBF)) }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                )
            }
        },
        containerColor = Color(0xFFF7F9FF)
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                placeholder = { Text("Tìm tin nhắn...", color = Color.Gray) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Color(0xFF005BBF)) },
                shape = CircleShape,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = Color.White,
                    focusedContainerColor = Color.White,
                    unfocusedBorderColor = Color(0xFFE5E7EB),
                    focusedBorderColor = Color(0xFF005BBF)
                ),
                singleLine = true
            )

            LazyRow(modifier = Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filters) { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter, fontWeight = FontWeight.SemiBold) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF005BBF), selectedLabelColor = Color.White),
                        shape = CircleShape
                    )
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (filteredConversations.isEmpty()) {
                    item { Box(Modifier.fillMaxWidth().padding(top = 100.dp), contentAlignment = Alignment.Center) { Text("Không có cuộc trò chuyện nào", color = Color.Gray) } }
                } else {
                    items(filteredConversations) { conversation ->
                        val otherUid = conversation.participants.firstOrNull { it != currentUid }
                        ChatItem(
                            conversation = conversation,
                            currentUid = currentUid,
                            userRole = userRole,
                            otherUser = usersInfo[otherUid],
                            onClick = { onConversationClick(conversation.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatItem(
    conversation: Conversation,
    currentUid: String,
    userRole: String,
    otherUser: User?,
    onClick: () -> Unit
) {
    val locale = LocalConfiguration.current.locales[0]
    val timeFormatter = remember(locale) { SimpleDateFormat("HH:mm", locale) }
    val unreadCount = conversation.unreadCount[currentUid] ?: 0

    // ✅ SỬA: Lấy tên hiển thị đúng từ trường fullName hoặc name
    val displayName = remember(conversation, otherUser, userRole) {
        if (conversation.type == "SUPPORT" && userRole.lowercase() != "admin") {
            "Quản trị viên"
        } else {
            otherUser?.getDisplayName() ?: "Người dùng"
        }
    }

    val displayRole = remember(conversation, otherUser, userRole) {
        if (conversation.type == "SUPPORT" && userRole.lowercase() != "admin") {
            "Hỗ trợ viên"
        } else {
            val role = otherUser?.role ?: "Đối tác"
            when(role.lowercase()) {
                "user" -> "Khách hàng"
                "restaurant" -> "Nhà hàng"
                "shipper" -> "Shipper"
                "admin" -> "Quản trị viên"
                else -> role
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                AsyncImage(
                    model = otherUser?.avatarUrl.takeIf { !it.isNullOrBlank() } ?: "https://cdn-icons-png.flaticon.com/512/149/149071.png",
                    contentDescription = null,
                    modifier = Modifier.size(58.dp).clip(CircleShape).border(1.dp, Color(0xFF005BBF).copy(alpha = 0.1f), CircleShape),
                    contentScale = ContentScale.Crop
                )
                Box(Modifier.size(14.dp).background(Color(0xFF00C853), CircleShape).border(2.dp, Color.White, CircleShape).align(Alignment.BottomEnd))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(displayName, fontWeight = if (unreadCount > 0) FontWeight.ExtraBold else FontWeight.Bold, fontSize = 16.sp)
                    Text(timeFormatter.format(conversation.lastMessageTime.toDate()), fontSize = 12.sp, color = if (unreadCount > 0) Color(0xFF005BBF) else Color.Gray)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(conversation.lastMessage.ifEmpty { "Bắt đầu cuộc trò chuyện" }, fontSize = 14.sp, color = if (unreadCount > 0) Color.Black else Color.DarkGray, fontWeight = if (unreadCount > 0) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (unreadCount > 0) {
                        Surface(color = Color(0xFF005BBF), shape = CircleShape) { Text(unreadCount.toString(), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    } else {
                        Surface(color = Color(0xFFE3F2FD), shape = RoundedCornerShape(8.dp), modifier = Modifier.padding(start = 4.dp)) { Text(displayRole, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF005BBF)) }
                    }
                }
            }
        }
    }
}
