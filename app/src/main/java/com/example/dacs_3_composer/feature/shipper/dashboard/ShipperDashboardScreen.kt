package com.example.dacs_3_composer.feature.shipper.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dacs_3_composer.feature.shipper.dashboard.components.*
import com.example.dacs_3_composer.feature.shipper.profile.ShipperProfileViewModel
import com.example.dacs_3_composer.data.remote.FoodHubClient
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ShipperDashboardScreen(
    onOrderClick: (String) -> Unit,
    onNavigateToChat: () -> Unit, // 🎯 THÊM: Tham số điều hướng chat
    onNavigateToEarnings: () -> Unit = {},
    shipperViewModel: ShipperViewModel = viewModel(),
    profileViewModel: ShipperProfileViewModel = viewModel()
) {
    val isReadyToWork by shipperViewModel.isReadyToWork.collectAsState()
    val availableOrders by shipperViewModel.availableOrders.collectAsState()
    val activeDeliveryOrder by shipperViewModel.activeDeliveryOrder.collectAsState()

    val todayIncome by shipperViewModel.todayIncomeStr.collectAsState()
    val completedCount by shipperViewModel.completedOrdersCountStr.collectAsState()

    val user by profileViewModel.user.collectAsState()
    val screenScope = rememberCoroutineScope()
    val context = LocalContext.current
    var touchingMap by remember { mutableStateOf(false) }

    val locationPermissionState = rememberPermissionState(
        android.Manifest.permission.ACCESS_FINE_LOCATION
    )

    LaunchedEffect(shipperViewModel.currentShipperId) {
        if (shipperViewModel.currentShipperId.isNotBlank()) {
            shipperViewModel.startAllListeners()
        }
    }

    LaunchedEffect(locationPermissionState.status.isGranted) {
        profileViewModel.loadProfile()
        if (locationPermissionState.status.isGranted) {
            shipperViewModel.fetchCurrentLocationOnce(context)
        } else {
            locationPermissionState.launchPermissionRequest()
        }
    }

    LaunchedEffect(activeDeliveryOrder, locationPermissionState.status.isGranted) {
        val order = activeDeliveryOrder
        if (order != null && locationPermissionState.status.isGranted) {
            shipperViewModel.startLocationUpdates(context, order.id)
        } else {
            shipperViewModel.stopLocationUpdates()
        }
    }

    Scaffold(
        topBar = { 
            ShipperDashboardTopBar(
                user = user,
                onChatClick = onNavigateToChat // 🎯 TRUYỀN: Sự kiện click Chat
            ) 
        },
        containerColor = Color(0xFFF5F8FC)
    ) { innerPadding ->
        LazyColumn(
            userScrollEnabled = !touchingMap,
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Column(Modifier.padding(top = 10.dp)) {
                    Text("Tổng quan", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color(0xFF172333))
                    Text("Theo dõi ngày giao hàng của bạn", fontSize = 14.sp, color = Color(0xFF66758A))
                }
            }

            item {
                WorkStatusToggleCard(
                    isReady = isReadyToWork,
                    onStatusChange = { shipperViewModel.toggleWorkStatus(it) }
                )
            }

            item {
                IncomeCard(todayIncome = todayIncome, growthText = "Xem chi tiết theo tuần và tháng", onClick = onNavigateToEarnings)
            }

            item {
                StatsMiniCardRow(completedCount = completedCount, activeCount = if (activeDeliveryOrder != null) "1" else "0")
            }

            if (activeDeliveryOrder == null) {
                item { HeatMapBanner(onMapTouchChange = { touchingMap = it }) }
            }

            if (activeDeliveryOrder != null) {
                item {
                    Text(text = "Đơn hàng đang xử lý", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                }
                item {
                    ActiveDeliveryDetailCard(
                        onMapTouchChange = { touchingMap = it },
                        order = activeDeliveryOrder!!,
                        onUpdateStatusClick = { targetStatus ->
                            try {
                                screenScope.launch { runCatching { FoodHubClient.update("orders", activeDeliveryOrder!!.id, mapOf("status" to targetStatus)) } }
                            } catch (e: Exception) { e.printStackTrace() }
                        },
                        onCardClick = { onOrderClick(activeDeliveryOrder!!.id) }
                    )
                }
            } else {
                item {
                    Text(text = "Đơn hàng phù hợp hiện có", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                }

                if (!isReadyToWork) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                            Text(text = "Hãy bật trạng thái sẵn sàng để nhận đơn.", color = Color.Gray, fontSize = 14.sp)
                        }
                    }
                } else {
                    if (availableOrders.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                                Text(text = "Đang quét đơn hàng mới từ các nhà hàng...", color = Color(0xFF2159BC), fontSize = 14.sp)
                            }
                        }
                    } else {
                        items(availableOrders, key = { it.id }) { order ->
                            DashboardAvailableOrderCard(
                                order = order,
                                onCardClick = { onOrderClick(order.id) },
                                onAcceptClick = { shipperViewModel.acceptOrder(order.id) }
                            )
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(4.dp)) }
            }
        }
    }
}
