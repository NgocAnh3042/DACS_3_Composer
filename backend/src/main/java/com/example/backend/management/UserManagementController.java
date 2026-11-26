package com.example.backend.management;

import com.example.backend.api.ApiEnvelope;
import com.example.backend.auth.FirebasePrincipal;
import com.example.backend.management.ManagementDtos.OkResponse;
import com.example.backend.management.ManagementDtos.UserPatch;
import com.example.backend.user.UserProfileDto;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api/users")
public class UserManagementController {
    private final UserManagementService service;
    public UserManagementController(UserManagementService service){this.service=service;}
    @GetMapping public ResponseEntity<ApiEnvelope<List<UserProfileDto>>> list(@AuthenticationPrincipal FirebasePrincipal p){return ResponseEntity.ok(ApiEnvelope.of(service.list(p)));}
    @GetMapping("/{firebaseUid}") public ResponseEntity<ApiEnvelope<UserProfileDto>> get(@AuthenticationPrincipal FirebasePrincipal p,@PathVariable String firebaseUid){return ResponseEntity.ok(ApiEnvelope.of(service.get(p,firebaseUid)));}
    @PatchMapping("/{firebaseUid}") public ResponseEntity<OkResponse> patch(@AuthenticationPrincipal FirebasePrincipal p,@PathVariable String firebaseUid,@Valid @RequestBody UserPatch r){service.patch(p,firebaseUid,r);return ResponseEntity.ok(new OkResponse(true));}
    @DeleteMapping("/{firebaseUid}") public ResponseEntity<OkResponse> delete(@AuthenticationPrincipal FirebasePrincipal p,@PathVariable String firebaseUid){service.delete(p,firebaseUid);return ResponseEntity.ok(new OkResponse(true));}
}
