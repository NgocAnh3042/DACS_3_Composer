package com.example.dacs_3_composer.feature.admin.customer

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dacs_3_composer.core.model.User
import com.example.dacs_3_composer.data.remote.FoodHubClient
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AdminCustomerViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val _customers = MutableStateFlow<List<User>>(emptyList())
    val customers: StateFlow<List<User>> = _customers
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery
    private val _selectedRoleFilter = MutableStateFlow("all")
    val selectedRoleFilter: StateFlow<String> = _selectedRoleFilter
    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount

    init { viewModelScope.launch { while (true) { runCatching { FoodHubClient.list<User>("users") }.onSuccess { _customers.value=it;_totalCount.value=it.size }.onFailure { Log.e("AdminCustomerVM","Không tải được người dùng MySQL",it) };delay(8000) } } }
    fun onSearchQueryChanged(query: String) { _searchQuery.value=query }
    fun onRoleFilterChanged(role: String) { _selectedRoleFilter.value=role.lowercase() }

    fun addCustomer(name:String,phone:String,email:String,address:String,role:String,vehicleName:String) {
        if(email.isBlank()||phone.isBlank())return
        auth.createUserWithEmailAndPassword(email,phone).addOnSuccessListener { result ->
            val uid=result.user?.uid ?: return@addOnSuccessListener
            viewModelScope.launch { runCatching { FoodHubClient.api.create("users/me",FoodHubClient.gson.toJsonTree(mapOf("uid" to uid,"name" to name,"email" to email,"phone" to phone,"address" to address,"role" to role,"vehicleName" to vehicleName)).asJsonObject) }
                .onFailure { Log.e("AdminCustomerVM","Không thể lưu tài khoản vào MySQL",it) } }
        }.addOnFailureListener { Log.e("AdminCustomerVM","Không thể tạo tài khoản Firebase Auth",it) }
    }
    fun updateCustomer(uid:String,name:String,phone:String,email:String,address:String,role:String,vehicleName:String) {
        if(uid.isBlank())return
        viewModelScope.launch { runCatching { FoodHubClient.update("users",uid,mapOf("name" to name,"phone" to phone,"email" to email,"address" to address,"role" to role,"vehicleName" to vehicleName)) }
            .onFailure { Log.e("AdminCustomerVM","Không thể cập nhật người dùng",it) } }
    }
    fun deleteCustomer(uid:String) { if(uid.isBlank())return;viewModelScope.launch { runCatching { FoodHubClient.delete("users",uid) }.onFailure { Log.e("AdminCustomerVM","Không thể xóa người dùng",it) } } }
    fun toggleLockStatus(uid:String,currentAvailable:Boolean) { if(uid.isBlank())return;viewModelScope.launch { runCatching { FoodHubClient.update("users",uid,mapOf("isAvailable" to !currentAvailable)) }.onFailure { Log.e("AdminCustomerVM","Không thể cập nhật trạng thái",it) } } }
}
