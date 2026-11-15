package com.example.dacs_3_composer.feature.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.dacs_3_composer.core.model.ChatMessage
import com.example.dacs_3_composer.core.model.MessageType
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    conversationId: String,
    onBackClick: () -> Unit
) {
    val viewModel: ChatViewModel = viewModel(factory = ChatViewModel.Factory)
    
    val messages by viewModel.messages.collectAsState()
    val isUploading by viewModel.isUploading.collectAsState()
    val error by viewModel.error.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val usersInfo by viewModel.usersInfo.collectAsState()
    val myUserRole by viewModel.userRole.collectAsState()

    val context = LocalContext.current
    val scrollState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }
    val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    val currentChat = remember(conversations, conversationId) {
        conversations.find { it.id == conversationId }
    }

    val otherUid = remember(currentChat, currentUid) {
        currentChat?.participants?.firstOrNull { it != currentUid }
    }
    val otherUser = remember(usersInfo, otherUid) {
        otherUid?.let { usersInfo[it] }
    }

    // ✅ SỬA: Sử dụng getDisplayName() để lấy đúng trường fullName
    val displayName = remember(currentChat, otherUser, myUserRole) {
        if (currentChat?.type == "SUPPORT" && myUserRole.lowercase() != "admin") {
            "Quản trị viên"
        } else {
            otherUser?.getDisplayName() ?: "Người dùng"
        }
    }

    val displayRole = remember(currentChat, otherUser, myUserRole) {
        if (currentChat?.type == "SUPPORT" && myUserRole.lowercase() != "admin") {
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

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.sendImageMessage(conversationId, context, it) }
    }

    LaunchedEffect(conversationId) {
        if (conversationId.isNotEmpty() && !conversationId.contains("support_admin")) {
            viewModel.loadMessages(conversationId)
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            scrollState.animateScrollToItem(messages.size - 1)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Surface(shadowElevation = 4.dp, color = Color.White) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box {
                                AsyncImage(
                                    // ✅ SỬA: Ảnh đại diện mặc định rõ ràng hơn
                                    model = otherUser?.avatarUrl.takeIf { !it.isNullOrBlank() } ?: "https://cdn-icons-png.flaticon.com/512/149/149071.png",
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(Color.LightGray),
                                    contentScale = ContentScale.Crop
                                )
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .align(Alignment.BottomEnd)
                                        .background(Color(0xFF4CAF50), CircleShape)
                                        .border(1.5.dp, Color.White, CircleShape)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(displayName, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text(displayRole, fontSize = 12.sp, color = Color(0xFF4CAF50))
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF005BBF))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .background(Color.White)
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                AnimatedVisibility(visible = isUploading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Color(0xFF005BBF))
                }
                BottomChatInput(
                    value = inputText,
                    onValueChange = {
                        inputText = it
                        viewModel.onTyping(conversationId, it.isNotEmpty())
                    },
                    onSendClick = {
                        if (inputText.isNotBlank()) {
                            viewModel.sendMessage(conversationId, inputText)
                            inputText = ""
                        }
                    },
                    onImageClick = { imagePickerLauncher.launch("image/*") }
                )
            }
        },
        containerColor = Color(0xFFF7F8FA)
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (conversationId.contains("support_admin")) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Đang kết nối...", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    state = scrollState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(messages) { message ->
                        MessageBubble(message = message, isCurrentUser = message.senderId == currentUid)
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage, isCurrentUser: Boolean) {
    val configuration = LocalConfiguration.current
    val timeFormatter = remember(configuration) {
        SimpleDateFormat("HH:mm", configuration.locales[0])
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (isCurrentUser) Color(0xFF005BBF) else Color.White,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isCurrentUser) 16.dp else 4.dp,
                bottomEnd = if (isCurrentUser) 4.dp else 16.dp
            ),
            shadowElevation = 1.dp,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (message.type == MessageType.IMAGE && message.imageUrl != null) {
                    AsyncImage(
                        model = message.imageUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Gray.copy(alpha = 0.1f)),
                        contentScale = ContentScale.Crop
                    )
                }
                if (message.content.isNotEmpty()) {
                    Text(
                        text = message.content,
                        color = if (isCurrentUser) Color.White else Color(0xFF1C1E21),
                        fontSize = 15.sp
                    )
                }
            }
        }
        Text(
            text = timeFormatter.format(message.createdAt.toDate()),
            fontSize = 11.sp,
            color = Color(0xFF65676B),
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun BottomChatInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onImageClick: () -> Unit
) {
    Surface(
        color = Color.White,
        shadowElevation = 12.dp, 
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp) 
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onImageClick) {
                Icon(
                    Icons.Default.AddCircle, 
                    contentDescription = "Add", 
                    tint = Color(0xFF005BBF),
                    modifier = Modifier.size(28.dp)
                )
            }
            
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                placeholder = { Text("Aa", color = Color.Gray) },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = Color(0xFF005BBF),
                    unfocusedBorderColor = Color(0xFFCCD0D5),
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    cursorColor = Color(0xFF005BBF)
                ),
                maxLines = 4
            )

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(
                onClick = onSendClick,
                enabled = value.isNotBlank()
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send, 
                    contentDescription = "Send",
                    tint = if (value.isNotBlank()) Color(0xFF005BBF) else Color(0xFFBEC3C9),
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    }
}
