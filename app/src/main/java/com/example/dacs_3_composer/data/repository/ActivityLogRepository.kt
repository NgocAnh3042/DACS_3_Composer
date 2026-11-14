package com.example.dacs_3_composer.data.repository

import com.example.dacs_3_composer.core.model.ActivityLog
import com.example.dacs_3_composer.data.remote.FoodHubClient
import com.google.gson.JsonObject
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ActivityLogRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun logActivity(
        restaurantId: String,
        type: String,
        title: String,
        description: String,
        details: String? = null,
        imageUrl: String? = null,
        extraInfo: Map<String, String>? = null
    ) {
        if (restaurantId.isBlank()) return

        val payload = JsonObject().apply {
            addProperty("actorUid", FirebaseAuth.getInstance().currentUser?.uid ?: restaurantId)
            addProperty("type", type)
            addProperty("title", title)
            addProperty("description", description)
            addProperty("details", details)
            addProperty("imageUrl", imageUrl)
        }
        scope.launch {
            runCatching { FoodHubClient.api.create("activity_logs", payload) }
        }
    }
}
