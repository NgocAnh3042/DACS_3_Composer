package com.example.backend.user;

import com.example.backend.api.ApiEnvelope;
import com.example.backend.auth.FirebasePrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me")
public class UserProfileController {
    private final UserProfileService service;

    public UserProfileController(UserProfileService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<ApiEnvelope<UserProfileDto>> get(@AuthenticationPrincipal FirebasePrincipal principal) {
        return ResponseEntity.ok(ApiEnvelope.of(service.get(principal.firebaseUid())));
    }

    @PostMapping
    public ResponseEntity<ApiEnvelope<UserProfileDto>> post(
            @AuthenticationPrincipal FirebasePrincipal principal,
            @RequestBody(required = false) UserProfileRequest request) {
        return ResponseEntity.ok(ApiEnvelope.of(service.synchronize(principal, request)));
    }

    @PutMapping
    public ResponseEntity<ApiEnvelope<UserProfileDto>> put(
            @AuthenticationPrincipal FirebasePrincipal principal,
            @RequestBody(required = false) UserProfileRequest request) {
        return ResponseEntity.ok(ApiEnvelope.of(service.synchronize(principal, request)));
    }

    @PatchMapping
    public ResponseEntity<ApiEnvelope<UserProfileDto>> patch(
            @AuthenticationPrincipal FirebasePrincipal principal,
            @RequestBody(required = false) UserProfileRequest request) {
        return ResponseEntity.ok(ApiEnvelope.of(service.synchronize(principal, request)));
    }
}
