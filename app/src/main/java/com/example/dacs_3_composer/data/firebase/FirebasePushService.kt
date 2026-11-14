package com.example.dacs_3_composer.data.firebase

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.dacs_3_composer.MainActivity
import com.example.dacs_3_composer.R
import com.example.dacs_3_composer.data.remote.FoodHubClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FirebasePushService : FirebaseMessagingService() {
    override fun onNewToken(token: String) { registerToken(token) }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: "FoodHub"
        val body = message.notification?.body ?: message.data["body"].orEmpty()
        showNotification(title, body)
    }

    private fun registerToken(token: String) {
        if (FirebaseAuth.getInstance().currentUser == null) return
        serviceScope.launch {
            val payload = JsonObject().apply { addProperty("token", token); addProperty("platform", "android") }
            runCatching { FoodHubClient.api.create("device-tokens", payload) }
        }
    }

    private fun showNotification(title: String, body: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Thông báo FoodHub", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val intent = Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP }
        val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    companion object {
        private const val CHANNEL_ID = "foodhub_updates"
        private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun syncCurrentToken() {
            if (FirebaseAuth.getInstance().currentUser == null) return
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                serviceScope.launch {
                    val payload = JsonObject().apply { addProperty("token", token); addProperty("platform", "android") }
                    runCatching { FoodHubClient.api.create("device-tokens", payload) }
                }
            }
        }
    }
}
