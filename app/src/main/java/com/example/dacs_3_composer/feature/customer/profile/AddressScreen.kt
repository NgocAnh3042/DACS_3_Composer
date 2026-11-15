package com.example.dacs_3_composer.feature.customer.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
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
import com.example.dacs_3_composer.core.model.UserAddress

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddressViewModel = viewModel()
) {
    val context = LocalContext.current
    val addresses by viewModel.addressList.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var editingAddress by remember { mutableStateOf<UserAddress?>(null) }
    LaunchedEffect(viewModel.error) {
        viewModel.error?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFF8F9FA),
                    titleContentColor = Color(0xFF191C1D)
                ),
                title = { Text("Địa chỉ đã lưu", fontWeight = FontWeight.Bold, fontSize = 22.sp) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingAddress = null
                    showDialog = true
                },
                containerColor = Color(0xFF2159BC),
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(modifier = Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Thêm mới", fontWeight = FontWeight.Medium)
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFFF8F9FA))
                .padding(paddingValues)
        ) {
            if (viewModel.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color(0xFF2159BC))
            } else if (addresses.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color(0xFFE8F0FE), RoundedCornerShape(22.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color(0xFF2159BC),
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Chưa có địa chỉ giao hàng", fontWeight = FontWeight.SemiBold, color = Color(0xFF191C1D))
                    Text(
                        "Thêm địa chỉ để đặt món và nhận hàng thuận tiện hơn.",
                        color = Color(0xFF727785),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(addresses) { address ->
                        AddressItemCard(
                            address = address,
                            onEditClick = {
                                editingAddress = address
                                showDialog = true
                            },
                            onDeleteClick = { viewModel.deleteAddress(address) }
                        )
                    }
                }
            }
        }

        if (showDialog) {
            AddressFormDialog(
                addressToEdit = editingAddress,
                onDismiss = { showDialog = false },
                onSave = { updatedAddress ->
                    if (editingAddress == null) {
                        viewModel.addAddress(updatedAddress.copy(isDefault = updatedAddress.isDefault || addresses.isEmpty())) { showDialog = false }
                    } else {
                        viewModel.updateAddress(editingAddress!!, updatedAddress) { showDialog = false }
                    }
                }
            )
        }
    }
}

@Composable
fun AddressItemCard(
    address: UserAddress,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFFE8F0FE), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF2159BC))
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (address.name.isNotBlank()) address.name else "Địa chỉ cá nhân",
                    fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black
                )
                if (address.isDefault) {
                    Text("Mặc định", color = Color(0xFF2159BC), style = MaterialTheme.typography.labelSmall)
                }
                Spacer(modifier = Modifier.height(4.dp))
                if (address.phone.isNotBlank()) {
                    Text(text = "SĐT: ${address.phone}", fontSize = 14.sp, color = Color(0xFF555555))
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Text(text = address.address, fontSize = 13.sp, color = Color.Gray, lineHeight = 18.sp)
                if (address.addressDetail.isNotBlank() && address.addressDetail != address.address) {
                    Text(
                        text = "Chi tiết: ${address.addressDetail}",
                        fontSize = 12.sp,
                        color = Color(0xFF2159BC),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(onClick = onEditClick, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Sửa địa chỉ",
                        tint = Color(0xFF2159BC)
                    )
                }
                IconButton(onClick = onDeleteClick, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Xóa địa chỉ",
                        tint = Color(0xFFC0392B)
                    )
                }
            }
        }
    }
}
