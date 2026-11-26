package com.example.backend.chat;

import com.example.backend.api.ApiException;
import com.example.backend.auth.FirebasePrincipal;
import com.example.backend.chat.ChatDtos.ConversationItem;
import com.example.backend.chat.ChatDtos.CreateConversationRequest;
import com.example.backend.chat.ChatDtos.MessageCreated;
import com.example.backend.chat.ChatDtos.MessageData;
import com.example.backend.chat.ChatDtos.MessageItem;
import com.example.backend.chat.ChatDtos.MessageRequest;
import com.example.backend.notification.FcmNotificationEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatService {
    private static final int MAX_TEXT_LENGTH=4000,MAX_PAGE=200,DEFAULT_CONVERSATION_PAGE=100,DEFAULT_MESSAGE_PAGE=50;
    private static final int MESSAGE_RATE_LIMIT=20;private static final long RATE_WINDOW_NANOS=10_000_000_000L;
    private final ChatRepository repository;private final ApplicationEventPublisher events;
    private final ConcurrentHashMap<Long,ArrayDeque<Long>> sendWindows=new ConcurrentHashMap<>();
    public ChatService(ChatRepository repository,ApplicationEventPublisher events){this.repository=repository;this.events=events;}

    public List<ConversationItem> list(FirebasePrincipal principal,Integer limit,Integer offset){
        long userId=userId(principal);int page=page(limit,DEFAULT_CONVERSATION_PAGE),start=offset(offset);
        return repository.conversations(userId,principal.firebaseUid(),principal.databaseRole(),page,start);
    }
    @Transactional public String create(FirebasePrincipal principal,CreateConversationRequest request){
        long currentUser=userId(principal);CreateConversationRequest input=request==null?new CreateConversationRequest(null,null,null):request;
        List<String> identifiers=new ArrayList<>(input.participants()==null?List.of():input.participants());
        if(identifiers.stream().anyMatch(value->value==null||value.isBlank()))throw error(HttpStatus.UNPROCESSABLE_ENTITY,"UID người tham gia không hợp lệ");
        identifiers.add(principal.firebaseUid());identifiers=identifiers.stream().map(String::trim).distinct().toList();
        List<Long> participants=repository.resolveParticipants(identifiers);
        if(!participants.contains(currentUser)){List<Long> includingCreator=new ArrayList<>(participants);includingCreator.add(currentUser);participants=includingCreator;}
        participants=participants.stream().distinct().sorted().toList();
        String type=normalizeType(input.type());Long orderId=parseOptionalId(input.orderId());
        repository.lockUsers(participants);
        Long existing=repository.findDuplicate(participants,type,orderId);if(existing!=null)return Long.toString(existing);
        long conversationId=repository.insertConversation(orderId,type,currentUser);repository.addParticipants(conversationId,participants);
        return Long.toString(conversationId);
    }
    public List<MessageItem> messages(FirebasePrincipal principal,long conversationId,Integer limit,Integer offset,Long before,Long after){
        long userId=userId(principal);requireConversationAccess(conversationId,userId,principal.databaseRole());
        if(before!=null&&before<=0||after!=null&&after<=0)throw error(HttpStatus.BAD_REQUEST,"Cursor tin nhắn không hợp lệ");
        return repository.messages(conversationId,principal.firebaseUid(),page(limit,DEFAULT_MESSAGE_PAGE),offset(offset),before,after);
    }
    @Transactional public Object postMessage(FirebasePrincipal principal,long conversationId,MessageRequest request){
        long userId=userId(principal);requireConversationAccess(conversationId,userId,principal.databaseRole());
        MessageRequest input=request==null?new MessageRequest(null,null,null,null):request;
        if("read".equalsIgnoreCase(input.action())){repository.markRead(conversationId,userId);return new ChatDtos.OkResponse(true);}
        String type="IMAGE".equalsIgnoreCase(input.type())?"image":"text";
        String content=input.content();String imageUrl=input.imageUrl();
        if(content!=null&&content.length()>MAX_TEXT_LENGTH)throw error(HttpStatus.UNPROCESSABLE_ENTITY,"Tin nhắn quá dài");
        if(imageUrl!=null&&imageUrl.length()>1500)throw error(HttpStatus.UNPROCESSABLE_ENTITY,"Đường dẫn ảnh quá dài");
        consumeSendQuota(userId);
        long messageId=repository.insertMessage(conversationId,userId,type,content,imageUrl);repository.updateConversationTime(conversationId);
        String last="image".equals(type)?"Đã gửi một ảnh":content==null?"":content;
        List<Long> recipients=repository.otherParticipantIds(conversationId,userId);Long orderId=repository.orderId(conversationId);
        if(!recipients.isEmpty()){
            repository.addChatNotifications(recipients,orderId,last);
            events.publishEvent(new FcmNotificationEvent(recipients,"chat","Tin nhắn mới",last,orderId));
        }
        return new MessageCreated(Long.toString(messageId),new MessageData(Long.toString(messageId),principal.firebaseUid(),content==null?"":content,imageUrl,
                type.toUpperCase(java.util.Locale.ROOT),repository.messageCreatedAt(messageId),false,true,last));
    }
    private void requireConversationAccess(long conversationId,long userId,String role){
        if(!repository.conversationExists(conversationId))throw error(HttpStatus.NOT_FOUND,"Không tìm thấy hội thoại");
        if(!"admin".equals(role)&&!repository.participant(conversationId,userId))throw error(HttpStatus.FORBIDDEN,"Bạn không thuộc cuộc trò chuyện này");
    }
    private void consumeSendQuota(long userId){
        long now=System.nanoTime();ArrayDeque<Long> window=sendWindows.computeIfAbsent(userId,key->new ArrayDeque<>());
        synchronized(window){while(!window.isEmpty()&&now-window.peekFirst()>RATE_WINDOW_NANOS)window.removeFirst();
            if(window.size()>=MESSAGE_RATE_LIMIT)throw error(HttpStatus.TOO_MANY_REQUESTS,"Bạn gửi tin nhắn quá nhanh");window.addLast(now);}
    }
    private long userId(FirebasePrincipal principal){if(principal==null||principal.userId()==null)throw error(HttpStatus.NOT_FOUND,"Không tìm thấy hồ sơ tài khoản");return principal.userId();}
    private static int page(Integer value,int fallback){return value==null?fallback:Math.max(1,Math.min(MAX_PAGE,value));}
    private static int offset(Integer value){return value==null?0:Math.max(0,value);}
    private static String normalizeType(String value){String type=value==null?"customer_store":value.toLowerCase(java.util.Locale.ROOT);return List.of("support","customer_driver","customer_store").contains(type)?type:"customer_store";}
    private static Long parseOptionalId(String value){if(value==null||value.isBlank())return null;try{long id=Long.parseLong(value);if(id<0)throw new NumberFormatException();return id;}catch(NumberFormatException e){throw error(HttpStatus.BAD_REQUEST,"orderId không hợp lệ");}}
    private static ApiException error(HttpStatus status,String message){return new ApiException(status,message);}
}
