package com.example.backend.activity;

import com.example.backend.activity.ActivityLogDtos.CreateRequest;
import com.example.backend.activity.ActivityLogDtos.IdResponse;
import com.example.backend.activity.ActivityLogDtos.Item;
import com.example.backend.api.ApiEnvelope;
import com.example.backend.auth.FirebasePrincipal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api/activity_logs")
public class ActivityLogController {
    private final ActivityLogService service;
    public ActivityLogController(ActivityLogService service){this.service=service;}
    @GetMapping public ResponseEntity<ApiEnvelope<List<Item>>> list(@AuthenticationPrincipal FirebasePrincipal principal){
        return ResponseEntity.ok(ApiEnvelope.of(service.list(principal)));
    }
    @PostMapping public ResponseEntity<IdResponse> create(@AuthenticationPrincipal FirebasePrincipal principal,
            @RequestBody(required=false) CreateRequest request){
        return ResponseEntity.status(HttpStatus.CREATED).body(new IdResponse(Long.toString(service.create(principal,request))));
    }
}
