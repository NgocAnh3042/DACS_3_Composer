package com.example.backend.notification;

import com.example.backend.api.ApiEnvelope;
import com.example.backend.auth.FirebasePrincipal;
import com.example.backend.notification.NotificationDtos.DeviceTokenRequest;
import com.example.backend.notification.NotificationDtos.Item;
import com.example.backend.notification.NotificationDtos.OkResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api")
public class NotificationController {
    private final NotificationService service;
    public NotificationController(NotificationService service){this.service=service;}
    @PostMapping("/device-tokens") public ResponseEntity<OkResponse> register(
            @AuthenticationPrincipal FirebasePrincipal principal,@RequestBody(required=false) DeviceTokenRequest request){
        service.upsert(principal,request);return ResponseEntity.ok(new OkResponse(true));
    }
    @GetMapping("/notifications") public ResponseEntity<ApiEnvelope<List<Item>>> list(@AuthenticationPrincipal FirebasePrincipal principal){
        return ResponseEntity.ok(ApiEnvelope.of(service.list(principal)));
    }
    @PatchMapping("/notifications/read-all") public ResponseEntity<OkResponse> readAll(@AuthenticationPrincipal FirebasePrincipal principal){
        service.markAllRead(principal);return ResponseEntity.ok(new OkResponse(true));
    }
    @PatchMapping("/notifications/{id:\\d+}") public ResponseEntity<OkResponse> readOne(
            @AuthenticationPrincipal FirebasePrincipal principal,@PathVariable long id){
        service.markRead(principal,id);return ResponseEntity.ok(new OkResponse(true));
    }
}
