package com.portfolio.order.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * product-service의 GET /api/products/{id} 응답을 매핑하는 DTO.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} — product-service에 새 필드가 추가돼도
 * 우리 서비스가 깨지지 않도록. 마이크로서비스 간 호환성을 위한 표준 패턴.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductResponse(
        Long id,
        String name,
        BigDecimal price,
        Integer stock,
        Instant createdAt
) {}