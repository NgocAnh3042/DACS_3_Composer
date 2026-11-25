package com.example.backend.address;

import com.example.backend.address.AddressDtos.AddressView;
import com.example.backend.address.AddressDtos.IdResponse;
import com.example.backend.address.AddressDtos.OkResponse;
import com.example.backend.address.AddressDtos.SaveRequest;
import com.example.backend.api.ApiEnvelope;
import com.example.backend.auth.FirebasePrincipal;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api/addresses")
public class AddressController {
    private final AddressService service;
    public AddressController(AddressService service){this.service=service;}
    @GetMapping public ResponseEntity<ApiEnvelope<List<AddressView>>> list(@AuthenticationPrincipal FirebasePrincipal p){return ResponseEntity.ok(ApiEnvelope.of(service.list(p)));}
    @PostMapping public ResponseEntity<IdResponse> create(@AuthenticationPrincipal FirebasePrincipal p,@RequestBody SaveRequest r){return ResponseEntity.ok(new IdResponse(Long.toString(service.save(p,null,r))));}
    @PutMapping("/{id:\\d+}") public ResponseEntity<IdResponse> put(@AuthenticationPrincipal FirebasePrincipal p,@PathVariable long id,@RequestBody SaveRequest r){return ResponseEntity.ok(new IdResponse(Long.toString(service.save(p,id,r))));}
    @PatchMapping("/{id:\\d+}") public ResponseEntity<IdResponse> patch(@AuthenticationPrincipal FirebasePrincipal p,@PathVariable long id,@RequestBody SaveRequest r){return ResponseEntity.ok(new IdResponse(Long.toString(service.save(p,id,r))));}
    @DeleteMapping("/{id:\\d+}") public ResponseEntity<OkResponse> delete(@AuthenticationPrincipal FirebasePrincipal p,@PathVariable long id){service.delete(p,id);return ResponseEntity.ok(new OkResponse(true));}
}
