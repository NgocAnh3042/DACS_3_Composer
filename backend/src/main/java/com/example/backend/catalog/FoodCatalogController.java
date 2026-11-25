package com.example.backend.catalog;

import com.example.backend.api.ApiEnvelope;
import com.example.backend.api.CatalogHttpResponse;
import com.example.backend.auth.FirebasePrincipal;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class FoodCatalogController {
    private final FoodCatalogService service;
    private final CatalogHttpResponse cachedResponse;

    public FoodCatalogController(FoodCatalogService service, CatalogHttpResponse cachedResponse) {
        this.service = service;
        this.cachedResponse = cachedResponse;
    }

    @GetMapping("/categories")
    public ResponseEntity<?> categories(
            @RequestParam(required = false) String restaurantId,
            @RequestParam(required = false) String limit,
            @RequestParam(required = false) String offset,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        return cachedResponse.cached(ApiEnvelope.of(service.categories(restaurantId, limit, offset)), ifNoneMatch);
    }

    @GetMapping("/stores")
    public ResponseEntity<?> stores(
            @RequestParam(required = false) String lat,
            @RequestParam(required = false) String lng,
            @RequestParam(required = false) String limit,
            @RequestParam(required = false) String offset,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        return cachedResponse.cached(ApiEnvelope.of(service.stores(lat, lng, limit, offset)), ifNoneMatch);
    }

    @GetMapping("/stores/{id:\\d+}")
    public ResponseEntity<?> store(
            @PathVariable String id,
            @RequestParam(required = false) String lat,
            @RequestParam(required = false) String lng,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        return cachedResponse.cached(ApiEnvelope.of(service.store(id, lat, lng)), ifNoneMatch);
    }

    @GetMapping("/dishes")
    public ResponseEntity<?> dishes(
            @RequestParam(required = false) String restaurantId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false, defaultValue = "0") String includeUnavailable,
            @RequestParam(required = false) String limit,
            @RequestParam(required = false) String offset,
            @AuthenticationPrincipal FirebasePrincipal principal,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        List<DishListDto> dishes = service.dishes(restaurantId, category, includeUnavailable,
                limit, offset, principal);
        return cachedResponse.cached(ApiEnvelope.of(dishes), ifNoneMatch);
    }

    @GetMapping("/dishes/{id}")
    public ResponseEntity<?> dish(
            @PathVariable String id,
            @AuthenticationPrincipal FirebasePrincipal principal,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        return cachedResponse.cached(ApiEnvelope.of(service.dish(id, principal)), ifNoneMatch);
    }

    @GetMapping({"/promotions", "/vouchers"})
    public ResponseEntity<ApiEnvelope<List<PromotionDto>>> promotions(
            @AuthenticationPrincipal FirebasePrincipal principal) {
        return ResponseEntity.ok(ApiEnvelope.of(service.promotions(principal)));
    }
}
