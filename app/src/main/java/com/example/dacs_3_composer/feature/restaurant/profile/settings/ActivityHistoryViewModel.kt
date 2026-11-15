package com.example.dacs_3_composer.feature.restaurant.profile.settings

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.dacs_3_composer.core.model.ActivityLog
import com.example.dacs_3_composer.data.remote.FoodHubClient
import com.google.firebase.auth.FirebaseAuth
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ActivityHistoryViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val TAG = "ActivityHistoryVM"

    private val restaurantId: String
        get() = auth.currentUser?.uid ?: ""

    var activityLogs by mutableStateOf<List<ActivityLog>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    init {
        fetchActivityLogs()
    }

    private fun fetchActivityLogs() {
        if (restaurantId.isBlank()) return

        isLoading = true
        viewModelScope.launch { while (true) {
            runCatching { FoodHubClient.list<ActivityLog>("activity_logs") }
                .onSuccess { activityLogs = it }
                .onFailure { Log.e(TAG, "Error loading activity logs", it) }
            isLoading = false
            delay(10_000)
        } }
    }
}
