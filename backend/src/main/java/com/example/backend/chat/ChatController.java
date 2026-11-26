package com.example.backend.chat;

import com.example.backend.api.ApiEnvelope;
import com.example.backend.auth.FirebasePrincipal;
import com.example.backend.chat.ChatDtos.ConversationItem;
import com.example.backend.chat.ChatDtos.CreateConversationRequest;
import com.example.backend.chat.ChatDtos.MessageItem;
import com.example.backend.chat.ChatDtos.MessageRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api/conversations")
public class ChatController {
    private final ChatService service;
    public ChatController(ChatService service){this.service=service;}
    @GetMapping public ResponseEntity<ApiEnvelope<List<ConversationItem>>> list(@AuthenticationPrincipal FirebasePrincipal principal,
            @RequestParam(required=false) Integer limit,@RequestParam(required=false) Integer offset){return ResponseEntity.ok(ApiEnvelope.of(service.list(principal,limit,offset)));}
    @PostMapping public ResponseEntity<ChatDtos.IdResponse> create(@AuthenticationPrincipal FirebasePrincipal principal,
            @RequestBody(required=false) CreateConversationRequest request){return ResponseEntity.status(HttpStatus.CREATED).body(new ChatDtos.IdResponse(service.create(principal,request)));}
    @GetMapping("/{id:\\d+}/messages") public ResponseEntity<ApiEnvelope<List<MessageItem>>> messages(@AuthenticationPrincipal FirebasePrincipal principal,
            @PathVariable long id,@RequestParam(required=false) Integer limit,@RequestParam(required=false) Integer offset,
            @RequestParam(required=false) Long before,@RequestParam(required=false) Long after){
        return ResponseEntity.ok(ApiEnvelope.of(service.messages(principal,id,limit,offset,before,after)));
    }
    @PostMapping("/{id:\\d+}/messages") public ResponseEntity<?> post(@AuthenticationPrincipal FirebasePrincipal principal,
            @PathVariable long id,@RequestBody(required=false) MessageRequest request){
        Object response=service.postMessage(principal,id,request);return response instanceof ChatDtos.OkResponse?ResponseEntity.ok(response):ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
