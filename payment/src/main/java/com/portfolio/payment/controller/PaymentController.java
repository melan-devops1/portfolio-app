package com.portfolio.payment.controller;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 분산 추적용 Request ID 필터.
 * <p>order-service에서 전파해주는 X-Request-Id를 받아 MDC에 저장.
 * <p>product/order와 동일한 패턴.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PaymentController extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";
    private static final int MAX_LENGTH = 128;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String requestId = resolveRequestId(request.getHeader(REQUEST_ID_HEADER));

        MDC.put(MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private String resolveRequestId(String incoming) {
        if (incoming == null || incoming.isBlank()) {
            return UUID.randomUUID().toString();
        }
        if (incoming.length() > MAX_LENGTH) {
            return UUID.randomUUID().toString();
        }
        if (incoming.chars().anyMatch(c -> c < 0x20)) {
            return UUID.randomUUID().toString();
        }
        return incoming;
    }
}