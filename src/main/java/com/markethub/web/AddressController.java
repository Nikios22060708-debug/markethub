package com.markethub.web;

import com.markethub.dto.AddressRequest;
import com.markethub.dto.AddressResponse;
import com.markethub.security.AuthUser;
import com.markethub.service.AddressService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/addresses")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping
    public List<AddressResponse> listMine(@AuthenticationPrincipal Jwt jwt) {
        return addressService.listMine(AuthUser.from(jwt));
    }

    @GetMapping("/{id}")
    public AddressResponse get(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return addressService.get(AuthUser.from(jwt), id);
    }

    @PostMapping
    public ResponseEntity<AddressResponse> create(@Valid @RequestBody AddressRequest request,
                                                  @AuthenticationPrincipal Jwt jwt) {
        AddressResponse created = addressService.create(AuthUser.from(jwt), request);
        return ResponseEntity.created(URI.create("/api/addresses/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public AddressResponse update(@PathVariable Long id, @Valid @RequestBody AddressRequest request,
                                  @AuthenticationPrincipal Jwt jwt) {
        return addressService.update(AuthUser.from(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        addressService.delete(AuthUser.from(jwt), id);
        return ResponseEntity.noContent().build();
    }
}
