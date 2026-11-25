package com.example.backend.user;

import com.example.backend.api.ApiEnvelope;
import com.example.backend.auth.FirebasePrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Compatibility route used by the current Android client during profile bootstrap. */
@RestController
@RequestMapping("/api/users")
public class UserRegistrationAliasController {
    private final UserProfileService service;
    public UserRegistrationAliasController(UserProfileService service){this.service=service;}
    @PostMapping public ResponseEntity<ApiEnvelope<UserProfileDto>> create(
            @AuthenticationPrincipal FirebasePrincipal principal,
            @RequestBody(required=false) UserProfileRequest request){
        return ResponseEntity.ok(ApiEnvelope.of(service.synchronize(principal,request)));
    }
}
