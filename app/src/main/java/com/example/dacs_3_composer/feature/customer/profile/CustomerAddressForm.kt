package com.example.dacs_3_composer.feature.customer.profile

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.dacs_3_composer.core.model.UserAddress
import com.example.dacs_3_composer.feature.shared.location.AddressLocationPicker
import com.example.dacs_3_composer.feature.shared.location.AddressPlace

@Composable
fun AddressFormDialog(
    addressToEdit: UserAddress?,
    onDismiss: () -> Unit,
    onSave: (UserAddress) -> Unit
) {
    val context = LocalContext.current
    var selected by remember(addressToEdit?.id) {
        mutableStateOf(
            addressToEdit?.takeIf {
                it.address.isNotBlank() && it.latitude in -90.0..90.0 &&
                    it.longitude in -180.0..180.0 && (it.latitude != 0.0 || it.longitude != 0.0)
            }
                ?.let { AddressPlace(it.address, it.latitude, it.longitude) }
        )
    }
    var name by remember(addressToEdit?.id) { mutableStateOf(addressToEdit?.name.orEmpty()) }
    var phone by remember(addressToEdit?.id) { mutableStateOf(addressToEdit?.phone.orEmpty()) }
    var detail by remember(addressToEdit?.id) {
        mutableStateOf(addressToEdit?.addressDetail.orEmpty().takeUnless { it == addressToEdit?.address }.orEmpty())
    }
    var isDefault by remember(addressToEdit?.id) { mutableStateOf(addressToEdit?.isDefault ?: false) }
    var searchPending by remember(addressToEdit?.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        title = { Text(if (addressToEdit == null) "Thêm địa chỉ giao hàng" else "Sửa địa chỉ", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 540.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    AddressLocationPicker(selected = selected, onSelected = { selected = it }, mapHeight = 180.dp,
                        onSearchPendingChange = { searchPending = it })
                }
                item {
                    OutlinedTextField(value = detail, onValueChange = { detail = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Số nhà, tòa nhà, cổng hoặc ghi chú") })
                }
                item {
                    OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Tên người nhận") }, singleLine = true)
                }
                item {
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Số điện thoại nhận hàng") }, singleLine = true)
                }
                item {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(checked = isDefault, onCheckedChange = { isDefault = it })
                        Text("Đặt làm địa chỉ mặc định")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val location = selected
                if (searchPending) {
                    Toast.makeText(context, "Hãy chọn một gợi ý hoặc chờ vị trí tải xong trước khi lưu.", Toast.LENGTH_SHORT).show()
                } else if (location == null) {
                    Toast.makeText(context, "Hãy chọn GPS hoặc một địa chỉ trên bản đồ.", Toast.LENGTH_SHORT).show()
                } else if (name.isBlank() || phone.isBlank()) {
                    Toast.makeText(context, "Hãy nhập tên và số điện thoại người nhận.", Toast.LENGTH_SHORT).show()
                } else {
                    val fullAddress = if (detail.isBlank()) location.address else "${detail.trim()}, ${location.address}"
                    onSave(UserAddress(
                        id = addressToEdit?.id.orEmpty(), name = name.trim(), phone = phone.trim(),
                        address = fullAddress, addressDetail = "",
                        latitude = location.latitude, longitude = location.longitude, isDefault = isDefault
                    ))
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2159BC))) {
                Text("Lưu địa chỉ")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
    )
}
