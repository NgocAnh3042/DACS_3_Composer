package com.example.backend.chat;

import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonInclude;

public final class ChatDtos {
    private ChatDtos(){}
    public record CreateConversationRequest(List<String> participants,String type,String orderId){}
    public record MessageRequest(String action,String type,String content,String imageUrl){}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ConversationItem(String id,List<String> participants,Map<String,String> participantRoles,
            String orderId,String type,String lastMessage,String lastMessageTime,String createdAt,
            Map<String,Integer> unreadCount,Map<String,Object> typing){}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record MessageItem(String messageId,String senderId,String receiverId,String content,String imageUrl,
            String type,String createdAt,boolean seen,boolean delivered,boolean deleted){}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record MessageData(String messageId,String senderId,String content,String imageUrl,String type,
            String createdAt,boolean seen,boolean delivered,String lastMessage){}
    public record MessageCreated(String id,MessageData data){}
    public record IdResponse(String id){}
    public record OkResponse(boolean ok){}
}
