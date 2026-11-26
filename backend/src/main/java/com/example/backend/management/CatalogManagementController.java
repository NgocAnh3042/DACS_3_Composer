package com.example.backend.management;

import com.example.backend.auth.FirebasePrincipal;
import com.example.backend.api.ApiEnvelope;
import com.example.backend.management.ManagementDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api")
public class CatalogManagementController {
    private final CatalogManagementService service;
    public CatalogManagementController(CatalogManagementService service){this.service=service;}
    @GetMapping("/stores/mine") public ResponseEntity<ApiEnvelope<StoreMine>> mine(@AuthenticationPrincipal FirebasePrincipal p){return ResponseEntity.ok(ApiEnvelope.of(service.storeMine(p,"GET",null)));}
    @PostMapping("/stores/mine") public ResponseEntity<ApiEnvelope<StoreMine>> createStore(@AuthenticationPrincipal FirebasePrincipal p,@Valid @RequestBody(required=false) StoreRequest r){return ResponseEntity.ok(ApiEnvelope.of(service.storeMine(p,"POST",r)));}
    @PutMapping("/stores/mine") public ResponseEntity<ApiEnvelope<StoreMine>> putStore(@AuthenticationPrincipal FirebasePrincipal p,@Valid @RequestBody StoreRequest r){return ResponseEntity.ok(ApiEnvelope.of(service.storeMine(p,"PUT",r)));}
    @PatchMapping("/stores/mine") public ResponseEntity<ApiEnvelope<StoreMine>> patchStore(@AuthenticationPrincipal FirebasePrincipal p,@Valid @RequestBody StoreRequest r){return ResponseEntity.ok(ApiEnvelope.of(service.storeMine(p,"PATCH",r)));}
    @PostMapping("/dishes") public ResponseEntity<IdResponse> createDish(@AuthenticationPrincipal FirebasePrincipal p,@Valid @RequestBody DishRequest r){return ResponseEntity.status(HttpStatus.CREATED).body(new IdResponse(service.createDish(p,r)));}
    @PutMapping("/dishes/{id:\\d+}") public ResponseEntity<OkResponse> putDish(@AuthenticationPrincipal FirebasePrincipal p,@PathVariable long id,@Valid @RequestBody DishRequest r){service.updateDish(p,id,r);return ResponseEntity.ok(new OkResponse(true));}
    @PatchMapping("/dishes/{id:\\d+}") public ResponseEntity<OkResponse> patchDish(@AuthenticationPrincipal FirebasePrincipal p,@PathVariable long id,@Valid @RequestBody DishRequest r){service.updateDish(p,id,r);return ResponseEntity.ok(new OkResponse(true));}
    @DeleteMapping("/dishes/{id:\\d+}") public ResponseEntity<OkResponse> deleteDish(@AuthenticationPrincipal FirebasePrincipal p,@PathVariable long id){service.deleteDish(p,id);return ResponseEntity.ok(new OkResponse(true));}
    @PostMapping({"/promotions","/vouchers"}) public ResponseEntity<IdResponse> createPromotion(@AuthenticationPrincipal FirebasePrincipal p,@Valid @RequestBody PromotionRequest r){return ResponseEntity.status(HttpStatus.CREATED).body(new IdResponse(service.createPromotion(p,r)));}
    @PatchMapping({"/promotions/{id:\\d+}","/vouchers/{id:\\d+}"}) public ResponseEntity<OkResponse> patchPromotion(@AuthenticationPrincipal FirebasePrincipal p,@PathVariable long id,@RequestBody(required=false) StatusRequest r){service.patchPromotion(p,id,r);return ResponseEntity.ok(new OkResponse(true));}
    @DeleteMapping({"/promotions/{id:\\d+}","/vouchers/{id:\\d+}"}) public ResponseEntity<OkResponse> deletePromotion(@AuthenticationPrincipal FirebasePrincipal p,@PathVariable long id){service.deletePromotion(p,id);return ResponseEntity.ok(new OkResponse(true));}
}
