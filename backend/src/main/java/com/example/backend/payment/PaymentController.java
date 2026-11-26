package com.example.backend.payment;

import com.example.backend.api.ApiEnvelope;
import com.example.backend.auth.FirebasePrincipal;
import com.example.backend.payment.PaymentDtos.CreateLinkRequest;
import com.example.backend.payment.PaymentDtos.CreateLinkResponse;
import com.example.backend.payment.PaymentDtos.PaymentItem;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api")
public class PaymentController {
    private final PaymentService service;
    public PaymentController(PaymentService service){this.service=service;}
    @PostMapping("/payments/create-link")
    public ResponseEntity<CreateLinkResponse> createLink(@AuthenticationPrincipal FirebasePrincipal principal,
            @RequestBody(required=false) CreateLinkRequest request){return ResponseEntity.ok(service.createLink(principal,request));}
    @GetMapping("/payments")
    public ResponseEntity<ApiEnvelope<List<PaymentItem>>> list(@AuthenticationPrincipal FirebasePrincipal principal){return ResponseEntity.ok(ApiEnvelope.of(service.list(principal)));}
    @PostMapping("/payos/webhook")
    public ResponseEntity<Object> webhook(@RequestBody(required=false) Map<String,Object> request){return ResponseEntity.ok(service.webhook(request));}
}
