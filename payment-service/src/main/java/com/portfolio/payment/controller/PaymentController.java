package com.portfolio.payment.controller;

import com.portfolio.payment.dto.PaymentRequest;
import com.portfolio.payment.dto.PaymentResponse;
import com.portfolio.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Validated
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping
    public List<PaymentResponse> list() {
        return paymentService.findAll();
    }

    @GetMapping("/{id}")
    public PaymentResponse get(@PathVariable Long id) {
        return paymentService.findById(id);
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> process(@Valid @RequestBody PaymentRequest request) {
        PaymentResponse processed = paymentService.process(request);
        URI location = URI.create("/api/payments/" + processed.id());
        return ResponseEntity.status(HttpStatus.CREATED).location(location).body(processed);
    }
}