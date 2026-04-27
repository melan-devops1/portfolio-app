package com.portfolio.order.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentResponse(
        Long id,
        Long orderId,
        BigDecimal amount,
        String status     // "SUCCESS" or "FAILED"
) {}