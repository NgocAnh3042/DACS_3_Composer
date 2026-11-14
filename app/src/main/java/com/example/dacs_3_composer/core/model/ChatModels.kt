package com.example.dacs_3_composer.core.model

import com.google.firebase.Timestamp

enum class MessageType { TEXT, IMAGE, LOCATION, SYSTEM }

data class ChatMessage(
    val messageId: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val content: String = "",
    val imageUrl: String? = null,
    val type: MessageType = MessageType.TEXT,
    val createdAt: Timestamp = Timestamp.now(),
    val seen: Boolean = false,
    val delivered: Boolean = false,
    val deleted: Boolean = false
)

data class Conversation(
    val id: String = "",
    val participants: List<String> = emptyList(),
    val participantRoles: Map<String, String> = emptyMap(), // uid -> role (USER, RESTAURANT, SHIPPER)
    val orderId: String? = null,
    val type: String = "DIRECT", // DIRECT, GROUP
    val lastMessage: String = "",
    val lastMessageTime: Timestamp = Timestamp.now(),
    val createdAt: Timestamp = Timestamp.now(),
    val unreadCount: Map<String, Int> = emptyMap(),
    val typing: Map<String, Boolean> = emptyMap()
)

data class UserChatStatus(
    val uid: String = "",
    val isOnline: Boolean = false,
    val lastSeen: Timestamp = Timestamp.now(),
    val fcmToken: String = ""
)
