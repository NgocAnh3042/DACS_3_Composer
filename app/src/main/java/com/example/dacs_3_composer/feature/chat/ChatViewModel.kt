package com.example.dacs_3_composer.feature.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.dacs_3_composer.core.model.ChatMessage
import com.example.dacs_3_composer.core.model.Conversation
import com.example.dacs_3_composer.core.model.MessageType
import com.example.dacs_3_composer.core.model.User
import com.example.dacs_3_composer.data.remote.ImageUploadService
import com.example.dacs_3_composer.data.repository.ChatRepository
import com.example.dacs_3_composer.data.repository.UserRepository
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChatViewModel(private val repository: ChatRepository) : ViewModel() {

    private val userRepository = UserRepository()
    
    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _userRole = MutableStateFlow("user")
    val userRole: StateFlow<String> = _userRole.asStateFlow()

    private val _usersInfo = MutableStateFlow<Map<String, User>>(emptyMap())
    val usersInfo: StateFlow<Map<String, User>> = _usersInfo.asStateFlow()

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadConversations()
    }

    private fun loadConversations() {
        viewModelScope.launch {
            try {
                val currentUser = userRepository.getCurrentUser()
                _userRole.value = currentUser.role
                repository.getConversations(currentUser.role).collect { list ->
                    _conversations.value = list
                    fetchUsersInfo(list, currentUser.uid)
                }
            } catch (e: Exception) {
                repository.getConversations("user").collect { list ->
                    _conversations.value = list
                }
            }
        }
    }

    private fun fetchUsersInfo(conversations: List<Conversation>, currentUid: String) {
        viewModelScope.launch {
            val uidsToFetch = conversations.flatMap { it.participants }
                .filter { it != currentUid }
                .distinct()
                .filter { !_usersInfo.value.containsKey(it) }

            uidsToFetch.forEach { uid ->
                try {
                    val user = userRepository.getUserById(uid)
                    _usersInfo.update { it + (uid to user) }
                } catch (e: Exception) { }
            }
        }
    }

    fun loadMessages(conversationId: String) {
        viewModelScope.launch {
            if (conversationId.isNotEmpty()) {
                repository.getMessages(conversationId).collect { msgs ->
                    _messages.value = msgs
                    repository.markAsSeen(conversationId)
                    
                    // Đảm bảo thông tin người dùng trong chat được tải
                    val currentUid = userRepository.getCurrentUser().uid
                    val currentConv = _conversations.value.find { it.id == conversationId }
                    currentConv?.let { fetchUsersInfo(listOf(it), currentUid) }
                }
            }
        }
    }

    fun sendMessage(conversationId: String, content: String, type: MessageType = MessageType.TEXT, imageUrl: String? = null) {
        if (conversationId.isBlank()) return
        viewModelScope.launch {
            val message = ChatMessage(
                content = content,
                type = type,
                imageUrl = imageUrl,
                createdAt = Timestamp.now()
            )
            try {
                repository.sendMessage(conversationId, message)
            } catch (e: Exception) {
                _error.value = "Không thể gửi: ${e.message}"
            }
        }
    }

    fun sendImageMessage(conversationId: String, context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                _isUploading.value = true
                val url = ImageUploadService.uploadImage(context, uri)
                sendMessage(conversationId, "", MessageType.IMAGE, url)
            } catch (e: Exception) {
                _error.value = "Lỗi upload: ${e.message}"
            } finally {
                _isUploading.value = false
            }
        }
    }

    fun onTyping(conversationId: String, isTyping: Boolean) {
        if (conversationId.isNotBlank()) {
            repository.setTypingStatus(conversationId, isTyping)
        }
    }

    fun clearError() { _error.value = null }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ChatViewModel(ChatRepository())
            }
        }
    }
}
