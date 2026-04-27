package com.portfolio.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PaymentRequest(
        @NotNull(message = "주문 ID는 필수입니다")
        Long orderId,

        @NotNull(message = "결제 금액은 필수입니다")
        @DecimalMin(value = "0.0", inclusive = false, message = "결제 금액은 0보다 커야 합니다")
        BigDecimal amount
) {}