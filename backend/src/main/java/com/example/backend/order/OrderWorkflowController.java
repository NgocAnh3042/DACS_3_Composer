package com.example.backend.order;

import com.example.backend.api.ApiEnvelope;
import com.example.backend.auth.FirebasePrincipal;
import com.example.backend.order.OrderDtos.CreateRequest;
import com.example.backend.order.OrderDtos.DriverView;
import com.example.backend.order.OrderDtos.OrderView;
import com.example.backend.order.OrderDtos.AvailableOrder;
import com.example.backend.order.OrderDtos.StatusRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class OrderWorkflowController {
    private final OrderWorkflowService service;
    public OrderWorkflowController(OrderWorkflowService service){this.service=service;}

    @GetMapping("/orders") public ResponseEntity<ApiEnvelope<List<OrderView>>> orders(
            @AuthenticationPrincipal FirebasePrincipal principal,@RequestParam(required=false) Integer limit,
            @RequestParam(required=false) Integer offset){return ResponseEntity.ok(ApiEnvelope.of(service.list(principal,null,cap(limit,100,200),Math.max(0,offset==null?0:offset))));}
    @GetMapping("/orders/available") public ResponseEntity<ApiEnvelope<List<AvailableOrder>>> available(
            @AuthenticationPrincipal FirebasePrincipal principal,@RequestParam(required=false) Integer limit,
            @RequestParam(required=false) Integer offset){return ResponseEntity.ok(ApiEnvelope.of(service.available(principal,cap(limit,100,200),Math.max(0,offset==null?0:offset))));}
    @GetMapping("/orders/{id:\\d+}") public ResponseEntity<ApiEnvelope<OrderView>> order(
            @AuthenticationPrincipal FirebasePrincipal principal,@PathVariable long id){List<OrderView> rows=service.list(principal,id,1,0);return ResponseEntity.ok(ApiEnvelope.of(rows.isEmpty()?null:rows.getFirst()));}
    @PostMapping("/orders") public ResponseEntity<OrderWorkflowService.CreateResponse> create(
            @AuthenticationPrincipal FirebasePrincipal principal,@RequestBody CreateRequest body){return ResponseEntity.status(HttpStatus.CREATED).body(service.create(principal,body));}
    @PatchMapping("/orders/{id:\\d+}") public ResponseEntity<OkResponse> update(
            @AuthenticationPrincipal FirebasePrincipal principal,@PathVariable long id,@RequestBody StatusRequest body){service.changeStatus(principal,id,body);return ResponseEntity.ok(new OkResponse(true));}
    @GetMapping("/drivers/me") public ResponseEntity<ApiEnvelope<DriverView>> driver(@AuthenticationPrincipal FirebasePrincipal p){return ResponseEntity.ok(ApiEnvelope.of(service.driver(p)));}
    @PatchMapping("/drivers/me") public ResponseEntity<OkResponse> updateDriver(@AuthenticationPrincipal FirebasePrincipal p,@RequestBody DriverPatch body){service.updateDriver(p,body.isAvailable(),body.vehicleName(),body.vehicleType());return ResponseEntity.ok(new OkResponse(true));}
    @PostMapping("/deliveries/{orderId:\\d+}/accept") public ResponseEntity<OkResponse> accept(@AuthenticationPrincipal FirebasePrincipal p,@PathVariable long orderId){service.accept(p,orderId);return ResponseEntity.ok(new OkResponse(true));}
    private static int cap(Integer v,int fallback,int max){return v==null?fallback:Math.max(1,Math.min(max,v));}
    public record OkResponse(boolean ok){}
    public record DriverPatch(Boolean isAvailable,String vehicleName,String vehicleType){}
}
