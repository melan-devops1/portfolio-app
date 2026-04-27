package com.portfolio.payment.dto;

import com.portfolio.payment.domain.Payment;
import com.portfolio.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        Long id,
        Long orderId,
        BigDecimal amount,
        PaymentStatus status,
        Long processingTimeMs,
        Instant createdAt
) {
        public static PaymentResponse from(Payment p) {
                return new PaymentResponse(
                        p.getId(),
                        p.getOrderId(),
                        p.getAmount(),
                        p.getStatus(),
                        p.getProcessingTimeMs(),
                        p.getCreatedAt()
                );
        }
}