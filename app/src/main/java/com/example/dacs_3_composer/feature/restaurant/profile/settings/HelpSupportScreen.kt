package com.example.dacs_3_composer.feature.restaurant.profile.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpSupportScreen(
    onBackClick: () -> Unit,
    onChatSupportClick: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Trợ giúp & Hỗ trợ",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF181C20)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF005BBF)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { /* Search functionality */ }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color(0xFF005BBF)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.White.copy(alpha = 0.7f)
                ),
                modifier = Modifier.clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            )
        },
        containerColor = Color(0xFFF7F9FF)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // 🚀 Hero Banner Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF005BBF))
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data("https://lh3.googleusercontent.com/aida-public/AB6AXuB2yMbYWzVIDWALh8bpQae4J9mdLHkI-OcNqpBXWqv9cmgtDQcU1vzkj1-gKdXccGB6PsxzSBH6U-E9WSZ32nccfyfb1Np76zPscRg1C-nkYJtuAmjWtjDaeLxpe863Jk92P83sa8CQPqcIoYuz6TAcd8J9j2KO--oCW-uO8OJFrJjIDuDmjO1dfmfCRbMrDJ8Fcbk8UEXWyASliHKpoZUyH0_gsbuOfIF4IyGPi_DH6L24OwcbCDqvXrL6B3Y86z1ztpZ-m6t4e0vk")
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.25f
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(
                        "Chào bạn, chúng tôi có thể giúp gì?",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        lineHeight = 32.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Tìm kiếm nhanh câu trả lời hoặc liên hệ với đội ngũ CSKH.",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 📂 Help Categories (Accordion Style)
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                var expandedIndex by remember { mutableIntStateOf(-1) }

                // 1. FAQ Item
                HelpCategoryItem(
                    icon = Icons.Default.Quiz,
                    title = "FAQ",
                    subtitle = "Câu hỏi thường gặp",
                    isExpanded = expandedIndex == 0,
                    onClick = { expandedIndex = if (expandedIndex == 0) -1 else 0 }
                ) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        FAQListItem("Cách thay đổi mật khẩu?")
                        FAQListItem("Lỗi đồng bộ đơn hàng với POS")
                        FAQListItem("Hướng dẫn quản lý nhân viên")
                    }
                }

                // 2. Support Center Item
                HelpCategoryItem(
                    icon = Icons.Default.Hub,
                    title = "Trung tâm hỗ trợ",
                    subtitle = "Tài liệu và hướng dẫn sử dụng",
                    isExpanded = expandedIndex == 1,
                    onClick = { expandedIndex = if (expandedIndex == 1) -1 else 1 }
                ) {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        Text(
                            "Tra cứu toàn bộ tài liệu vận hành nhà hàng, từ thiết lập thực đơn đến báo cáo doanh thu chuyên sâu.",
                            fontSize = 14.sp,
                            color = Color(0xFF414754)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { /* Open docs */ },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD8E2FF), contentColor = Color(0xFF001A41)),
                            shape = RoundedCornerShape(28.dp)
                        ) {
                            Text("Xem tài liệu ngay", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // 3. Contact CSKH Item
                HelpCategoryItem(
                    icon = Icons.Default.SupportAgent,
                    title = "Liên hệ CSKH",
                    subtitle = "Kết nối với hỗ trợ viên 24/7",
                    isExpanded = expandedIndex == 2,
                    onClick = { expandedIndex = if (expandedIndex == 2) -1 else 2 }
                ) {
                    Row(
                        modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ContactOptionCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Call,
                            label = "Gọi tổng đài",
                            onClick = { /* Call logic */ }
                        )
                        ContactOptionCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.AutoMirrored.Filled.Chat,
                            label = "Chat trực tiếp",
                            onClick = onChatSupportClick
                        )
                    }
                }

                // 4. Report Bug Item
                HelpCategoryItem(
                    icon = Icons.Default.BugReport,
                    title = "Báo lỗi ứng dụng",
                    subtitle = "Giúp chúng tôi cải thiện hệ thống",
                    isExpanded = expandedIndex == 3,
                    onClick = { expandedIndex = if (expandedIndex == 3) -1 else 3 }
                ) {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        OutlinedTextField(
                            value = "",
                            onValueChange = {},
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Mô tả ngắn về lỗi bạn gặp phải...") },
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = Color(0xFFF1F4FA),
                                focusedContainerColor = Color(0xFFF1F4FA),
                                unfocusedBorderColor = Color.Transparent,
                                focusedBorderColor = Color(0xFF005BBF)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            minLines = 3
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { /* Send report */ },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(28.dp)
                        ) {
                            Text("Gửi báo cáo", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // 5. Privacy Policy Item
                HelpCategoryItem(
                    icon = Icons.Default.VerifiedUser,
                    title = "Chính sách bảo mật",
                    subtitle = "Cam kết bảo mật dữ liệu của bạn",
                    isExpanded = expandedIndex == 4,
                    onClick = { expandedIndex = if (expandedIndex == 4) -1 else 4 }
                ) {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        Text(
                            "Chúng tôi cam kết bảo vệ thông tin cá nhân và dữ liệu kinh doanh của nhà hàng. Thông tin được mã hóa theo tiêu chuẩn AES-256.",
                            fontSize = 14.sp,
                            color = Color(0xFF414754),
                            lineHeight = 20.sp
                        )
                        TextButton(
                            onClick = { /* Open policy */ },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Đọc bản đầy đủ", color = Color(0xFF005BBF), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 💬 Feedback Footer Section
            Spacer(modifier = Modifier.height(32.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFB7EAFF).copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Vẫn cần giúp đỡ?",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF001F28)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Gửi phản hồi trực tiếp cho ban quản trị ứng dụng.",
                        fontSize = 14.sp,
                        color = Color(0xFF005266),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.alpha(0.8f)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { /* Feedback dialog */ },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF005BBF)),
                        shape = RoundedCornerShape(28.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                    ) {
                        Text("Gửi phản hồi", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
fun HelpCategoryItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isExpanded: Boolean,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val rotation by animateFloatAsState(if (isExpanded) 180f else 0f, label = "rotation")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB).copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isExpanded) 4.dp else 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFF005BBF).copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = Color(0xFF005BBF), modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF181C20))
                    Text(subtitle, fontSize = 12.sp, color = Color(0xFF727785))
                }
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.rotate(rotation),
                    tint = Color(0xFF727785)
                )
            }
            AnimatedVisibility(visible = isExpanded) {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFF1F3F4))
                    content()
                }
            }
        }
    }
}

@Composable
fun FAQListItem(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { /* Detail */ },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, fontSize = 14.sp, color = Color(0xFF414754), modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp))
        Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF727785))
    }
}

@Composable
fun ContactOptionCard(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = Color(0xFFF1F4FA),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = Color(0xFF005BBF))
            Spacer(modifier = Modifier.height(8.dp))
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF181C20))
        }
    }
}
