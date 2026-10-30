package com.markethub.web;

import com.markethub.dto.PageResponse;
import com.markethub.dto.PaymentRequest;
import com.markethub.dto.PaymentResponse;
import com.markethub.security.AuthUser;
import com.markethub.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Платёж создаётся (оплата), читается и «обновляется» возвратом.
 * Удаление платежей не предусмотрено — это финансовая история; платёж удаляется вместе с заказом.
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public PageResponse<PaymentResponse> list(@PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return paymentService.list(pageable);
    }

    @GetMapping("/{id}")
    public PaymentResponse get(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return paymentService.get(AuthUser.from(jwt), id);
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> pay(@Valid @RequestBody PaymentRequest request,
                                               @AuthenticationPrincipal Jwt jwt) {
        PaymentResponse created = paymentService.pay(AuthUser.from(jwt), request);
        return ResponseEntity.created(URI.create("/api/payments/" + created.id())).body(created);
    }

    @PostMapping("/{id}/refund")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public PaymentResponse refund(@PathVariable Long id) {
        return paymentService.refund(id);
    }
}
