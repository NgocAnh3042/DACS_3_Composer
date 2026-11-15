package com.example.dacs_3_composer.feature.admin.customer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dacs_3_composer.core.model.User
import com.example.dacs_3_composer.feature.admin.customer.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminCustomerScreen(
    modifier: Modifier = Modifier,
    viewModel: AdminCustomerViewModel = viewModel()
) {
    val customers by viewModel.customers.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedRoleFilter by viewModel.selectedRoleFilter.collectAsState()
    val totalCount by viewModel.totalCount.collectAsState()

    var showDialog by remember { mutableStateOf(false) }
    var selectedUserForEdit by remember { mutableStateOf<User?>(null) }

    var inputName by remember { mutableStateOf("") }
    var inputPhone by remember { mutableStateOf("") }
    var inputEmail by remember { mutableStateOf("") }
    var inputAddress by remember { mutableStateOf("") }
    var inputVehicleName by remember { mutableStateOf("") }

    var inputRole by remember { mutableStateOf("user") }
    var expandedDropdown by remember { mutableStateOf(false) }
    val roleOptions = listOf("admin", "restaurant", "user", "shipper")

    // Danh sách các Tab bộ lọc hiển thị trên TopBar
    val filterTabs = listOf("Tất cả", "Admin", "Restaurant", "User", "Shipper")

    // 🔥 XỬ LÝ LỌC KÉP: Lọc theo vai trò trước, sau đó lọc theo nội dung Tìm Kiếm
    val filteredCustomers = customers.filter { customer ->
        val matchesRole = (selectedRoleFilter == "all") || (customer.role.lowercase() == selectedRoleFilter)
        val matchesSearch = customer.name.contains(searchQuery, ignoreCase = true) || customer.phone.contains(searchQuery)
        matchesRole && matchesSearch
    }

    Scaffold(
        topBar = {
            Column {
                // 1. Phần TopBar tiêu đề gốc kèm nút thêm mới
                CustomerTopBar(
                    onAddUserClick = {
                        selectedUserForEdit = null
                        inputName = ""
                        inputPhone = ""
                        inputEmail = ""
                        inputAddress = ""
                        inputVehicleName = ""
                        inputRole = "user"
                        showDialog = true
                    }
                )

                // 2. THANH TAB LỌC THEO VAI TRÒ (Mới cập nhật trực quan)
                ScrollableTabRow(
                    selectedTabIndex = filterTabs.indexOfFirst {
                        if (selectedRoleFilter == "all") it == "Tất cả" else it.lowercase() == selectedRoleFilter
                    }.coerceAtLeast(0),
                    containerColor = Color.White,
                    contentColor = Color(0xFF0052CC),
                    edgePadding = 16.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    filterTabs.forEach { tabName ->
                        val isSelected = if (selectedRoleFilter == "all") tabName == "Tất cả" else tabName.lowercase() == selectedRoleFilter
                        Tab(
                            selected = isSelected,
                            onClick = {
                                if (tabName == "Tất cả") {
                                    viewModel.onRoleFilterChanged("all")
                                } else {
                                    viewModel.onRoleFilterChanged(tabName)
                                }
                            },
                            text = {
                                Text(
                                    text = tabName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (isSelected) Color(0xFF0052CC) else Color(0xFF727785)
                                )
                            }
                        )
                    }
                }
            }
        },
        containerColor = Color(0xFFF8F9FA)
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // Ô tìm kiếm hoạt động Realtime
            item {
                CustomerSearchBar(
                    query = searchQuery,
                    onQueryChange = { viewModel.onSearchQueryChanged(it) }
                )
            }

            // Hàng thống kê cập nhật dữ liệu đếm linh hoạt theo bộ lọc hiện tại
            item {
                CustomerStatsRow(
                    totalCustomers = "$totalCount người",
                    newThisMonth = "${filteredCustomers.size} kết quả"
                )
            }

            // Danh sách kết quả hiển thị sau khi lọc thành công
            items(filteredCustomers, key = { it.uid }) { customer ->
                CustomerCardItem(
                    name = customer.name.ifBlank { "Chưa đặt tên" },
                    phone = customer.phone.ifBlank { "Không có SĐT" },
                    email = customer.email.ifBlank { "Chưa có Email" },
                    role = customer.role.ifBlank { "user" },
                    avatarUrl = customer.avatarUrl,
                    address = customer.address,
                    vehicleName = customer.vehicleName,
                    isAvailable = customer.isAvailable,
                    onEditClick = {
                        selectedUserForEdit = customer
                        inputName = customer.name
                        inputPhone = customer.phone
                        inputEmail = customer.email
                        inputAddress = customer.address
                        inputVehicleName = customer.vehicleName
                        inputRole = customer.role.ifBlank { "user" }
                        showDialog = true
                    },
                    onDeleteClick = {
                        viewModel.deleteCustomer(customer.uid)
                    },
                    onLockToggle = {
                        viewModel.toggleLockStatus(customer.uid, customer.isAvailable)
                    }
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

        // Hộp thoại Thêm/Sửa thông tin tài khoản
        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = {
                    Text(
                        text = if (selectedUserForEdit == null) "Thêm Tài Khoản Mới" else "Sửa Thông Tin",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = inputName,
                            onValueChange = { inputName = it },
                            label = { Text("Họ và tên") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = inputPhone,
                            onValueChange = { inputPhone = it },
                            label = { Text("Số điện thoại") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = inputEmail,
                            onValueChange = { inputEmail = it },
                            label = { Text("Địa chỉ Email") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = inputAddress,
                            onValueChange = { inputAddress = it },
                            label = { Text("Địa chỉ cư trú") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        ExposedDropdownMenuBox(
                            expanded = expandedDropdown,
                            onExpandedChange = { expandedDropdown = !expandedDropdown },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = inputRole.uppercase(),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Vai trò (Role)") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDropdown) },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = expandedDropdown,
                                onDismissRequest = { expandedDropdown = false }
                            ) {
                                roleOptions.forEach { role ->
                                    DropdownMenuItem(
                                        text = { Text(role.uppercase()) },
                                        onClick = {
                                            inputRole = role
                                            expandedDropdown = false
                                        },
                                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                    )
                                }
                            }
                        }

                        if (inputRole == "shipper") {
                            OutlinedTextField(
                                value = inputVehicleName,
                                onValueChange = { inputVehicleName = it },
                                label = { Text("Tên phương tiện di chuyển") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0052CC)),
                        onClick = {
                            if (inputName.isNotBlank() && inputPhone.isNotBlank() && inputEmail.isNotBlank()) {
                                val currentUser = selectedUserForEdit
                                if (currentUser == null) {
                                    viewModel.addCustomer(
                                        name = inputName,
                                        phone = inputPhone,
                                        email = inputEmail,
                                        address = inputAddress,
                                        role = inputRole,
                                        vehicleName = if (inputRole == "shipper") inputVehicleName else ""
                                    )
                                } else {
                                    viewModel.updateCustomer(
                                        uid = currentUser.uid,
                                        name = inputName,
                                        phone = inputPhone,
                                        email = inputEmail,
                                        address = inputAddress,
                                        role = inputRole,
                                        vehicleName = if (inputRole == "shipper") inputVehicleName else ""
                                    )
                                }
                                showDialog = false
                            }
                        }
                    ) {
                        Text("Lưu Lại")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDialog = false }) {
                        Text("Hủy", color = Color.Gray)
                    }
                }
            )
        }
    }
}