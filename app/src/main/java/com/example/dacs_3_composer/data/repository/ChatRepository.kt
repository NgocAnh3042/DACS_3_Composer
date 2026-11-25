package com.example.dacs_3_composer.data.repository

import com.example.dacs_3_composer.core.model.ChatMessage
import com.example.dacs_3_composer.core.model.Conversation
import com.example.dacs_3_composer.data.remote.FoodHubClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.JsonObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class ChatRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private val currentUid get() = auth.currentUser?.uid ?: ""

    suspend fun createConversation(partnerId: String, partnerRole: String, orderId: String? = null): String {
        require(currentUid.isNotBlank() && partnerId.isNotBlank()) { "Thiếu tài khoản tham gia chat" }
        val type = if (partnerRole.equals("shipper", true) || partnerRole.equals("driver", true)) "customer_driver" else "customer_store"
        val payload = JsonObject().apply {
            addProperty("type", type)
            if (!orderId.isNullOrBlank()) addProperty("orderId", orderId)
            add("participants", com.google.gson.JsonArray().apply { add(currentUid); add(partnerId) })
        }
        val response=FoodHubClient.api.create("conversations",payload)
        if(!response.isSuccessful)error(response.errorBody()?.string() ?: "Không tạo được cuộc trò chuyện")
        return response.body()?.get("id")?.asString ?: error("API không trả conversation ID")
    }

    fun getConversations(myRole: String = "user"): Flow<List<Conversation>> = flow {
        if (currentUid.isBlank()) { emit(emptyList()); return@flow }
        var lastSuccessful: List<Conversation>? = null
        while (true) {
            val current = runCatching { FoodHubClient.list<Conversation>("conversations") }.getOrNull()
            if (current != null) {
                emit(current)
                val unchanged = current == lastSuccessful
                lastSuccessful = current
                delay(if (unchanged) 10_000 else 3_000)
            } else {
                lastSuccessful?.let { emit(it) }
                delay(if (lastSuccessful == null) 3_000 else 10_000)
            }
        }
    }

    fun getMessages(conversationId: String, limit: Long = 50): Flow<List<ChatMessage>> = flow {
        if (conversationId.isBlank()) { emit(emptyList()); return@flow }
        var lastSuccessful: List<ChatMessage>? = null
        while (true) {
            val current = runCatching { FoodHubClient.list<ChatMessage>("conversations/$conversationId/messages", mapOf("limit" to limit.toString())) }.getOrNull()
            if (current != null) {
                emit(current)
                val unchanged = current == lastSuccessful
                lastSuccessful = current
                delay(if (unchanged) 8_000 else 2_000)
            } else {
                lastSuccessful?.let { emit(it) }
                delay(if (lastSuccessful == null) 2_000 else 8_000)
            }
        }
    }

    suspend fun sendMessage(convId: String, message: ChatMessage) {
        require(currentUid.isNotBlank()) { "Chưa đăng nhập" }
        val payload = message.copy(messageId = "", senderId = currentUid, delivered = true)
        val response = FoodHubClient.api.create("conversations/$convId/messages", FoodHubClient.gson.toJsonTree(payload).asJsonObject)
        if (!response.isSuccessful) error(response.errorBody()?.string() ?: "Không gửi được tin nhắn")
    }

    suspend fun markAsSeen(convId: String) {
        if (currentUid.isBlank() || convId.isBlank()) return
        val payload = JsonObject().apply { addProperty("action", "read") }
        val response = FoodHubClient.api.create("conversations/$convId/messages", payload)
        if (!response.isSuccessful) error(response.errorBody()?.string() ?: "Không cập nhật trạng thái đã đọc")
    }

    fun setTypingStatus(convId: String, isTyping: Boolean) {
        if (currentUid.isBlank() || convId.isBlank()) return
        FirebaseDatabase.getInstance().getReference("chat").child(convId).child("typing").child(currentUid).setValue(isTyping)
    }

    suspend fun contactSupport(myRole: String): String {
        val existing = FoodHubClient.list<Conversation>("conversations").firstOrNull {
            it.type.equals("SUPPORT", ignoreCase = true) && currentUid in it.participants
        }
        if (existing != null) return existing.id
        val payload = JsonObject().apply {
            addProperty("type", "support")
            add("participants", com.google.gson.JsonArray().apply { add(currentUid) })
            addProperty("requesterRole", myRole.lowercase())
        }
        val response = FoodHubClient.api.create("conversations", payload)
        if (!response.isSuccessful) error(response.errorBody()?.string() ?: "Không tạo được cuộc trò chuyện")
        return response.body()?.get("id")?.asString ?: error("API không trả conversation ID")
    }
}
