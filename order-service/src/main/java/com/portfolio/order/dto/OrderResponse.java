package com.portfolio.order.dto;

import com.portfolio.order.domain.Order;
import com.portfolio.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderResponse(
        Long id,
        Long productId,
        Integer quantity,
        BigDecimal totalAmount,
        OrderStatus status,
        Long paymentId,
        Instant createdAt
) {
        public static OrderResponse from(Order o) {
                return new OrderResponse(
                        o.getId(),
                        o.getProductId(),
                        o.getQuantity(),
                        o.getTotalAmount(),
                        o.getStatus(),
                        o.getPaymentId(),
                        o.getCreatedAt()
                );
        }
}