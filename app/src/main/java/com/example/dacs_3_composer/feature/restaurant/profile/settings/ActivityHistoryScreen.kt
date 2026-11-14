package com.example.dacs_3_composer.feature.restaurant.profile.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.dacs_3_composer.core.model.ActivityLog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityHistoryScreen(
    onBackClick: () -> Unit,
    viewModel: ActivityHistoryViewModel = viewModel()
) {
    val logs = viewModel.activityLogs
    val isLoading = viewModel.isLoading

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Lịch sử hoạt động",
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
                            tint = Color(0xFF1A73E8)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { /* Filter logic */ }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filter", tint = Color(0xFF1A73E8))
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
        if (isLoading && logs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF1A73E8))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 24.dp, bottom = 100.dp)
            ) {
                val groupedLogs = logs.groupBy { it.date }

                groupedLogs.forEach { (date, dailyLogs) ->
                    item {
                        Text(
                            text = date.uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF727785),
                            letterSpacing = 1.2.sp,
                            modifier = Modifier.padding(start = 8.dp, bottom = 16.dp)
                        )
                    }

                    itemsIndexed(dailyLogs) { index, log ->
                        val isLastInList = index == dailyLogs.size - 1 && groupedLogs.keys.last() == date
                        
                        TimelineItem(
                            log = log,
                            isLast = isLastInList
                        )
                        
                        if (!isLastInList) {
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                    
                    item { Spacer(modifier = Modifier.height(32.dp)) }
                }
                
                if (logs.isEmpty() && !isLoading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(top = 100.dp), contentAlignment = Alignment.Center) {
                            Text("Chưa có lịch sử hoạt động", color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TimelineItem(
    log: ActivityLog,
    isLast: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.width(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val (icon, bgColor, iconColor) = when (log.type) {
                "login" -> Triple(Icons.AutoMirrored.Filled.Login, Color(0xFFD8E2FF), Color(0xFF001A41))
                "menu" -> Triple(Icons.Default.RestaurantMenu, Color(0xFFADC7FF), Color(0xFF004493))
                "profile" -> Triple(Icons.Default.ManageAccounts, Color(0xFFE5E8EE), Color(0xFF414754))
                "order" -> Triple(Icons.AutoMirrored.Filled.ReceiptLong, Color(0xFF008939).copy(alpha = 0.1f), Color(0xFF008939))
                "security" -> Triple(Icons.Default.Security, Color(0xFFFFDAD6), Color(0xFFBA1A1A))
                else -> Triple(Icons.Default.Info, Color(0xFFEBEBF4), Color(0xFF727785))
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(bgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFF1A73E8), Color(0xFFDFE3E8))
                            )
                        )
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = log.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (log.type == "security") Color(0xFFBA1A1A) else Color(0xFF181C20),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = log.time,
                        fontSize = 11.sp,
                        color = Color(0xFF727785)
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = log.description,
                    fontSize = 14.sp,
                    color = Color(0xFF414754)
                )

                log.imageUrl?.let { url ->
                    if (url.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(url)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Activity image",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                log.details?.let { details ->
                    if (details.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = details,
                            fontSize = 12.sp,
                            color = Color(0xFF727785)
                        )
                    }
                }
                
                if (log.type == "order" && log.extraInfo?.containsKey("customer") == true) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF1F4FA), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .border(BorderStroke(1.dp, Color(0xFFDFE3E8)), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF727785))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Khách: ${log.extraInfo["customer"]}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF181C20)
                                )
                                Text(
                                    text = "Giá trị: ${log.extraInfo["value"]}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF727785)
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF008939), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}
