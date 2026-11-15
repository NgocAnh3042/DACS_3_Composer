package com.example.dacs_3_composer.feature.restaurant.profile.settings

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dacs_3_composer.core.model.RestaurantDetail
import com.example.dacs_3_composer.data.remote.FoodHubClient
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class NotificationSettingsViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val TAG = "NotificationSettingsVM"

    private val restaurantId: String
        get() = auth.currentUser?.uid ?: ""

    var restaurantDetail by mutableStateOf<RestaurantDetail?>(null)
        private set
    
    var isLoading by mutableStateOf(false)
        private set

    init {
        fetchSettings()
    }

    private fun fetchSettings() {
        if (restaurantId.isBlank()) return
        
        viewModelScope.launch { runCatching { FoodHubClient.get<RestaurantDetail>("stores", "mine") }.onSuccess { restaurantDetail = it }.onFailure { Log.e(TAG,"Error loading settings",it) } }
    }

    fun updateNotificationSetting(field: String, value: Boolean) {
        if (restaurantId.isBlank()) return
        
        viewModelScope.launch {
            try {
                val current = restaurantDetail ?: FoodHubClient.get("stores", "mine")
                val payload = FoodHubClient.gson.toJsonTree(current).asJsonObject.apply { addProperty(field, value) }
                val response = FoodHubClient.api.update("stores", "mine", payload)
                if (!response.isSuccessful) error(response.errorBody()?.string() ?: "Không lưu được cài đặt")
                restaurantDetail = FoodHubClient.gson.fromJson(payload, RestaurantDetail::class.java)
                Log.d(TAG, "Updated $field to $value")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating $field", e)
            }
        }
    }
}
