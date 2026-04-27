package com.portfolio.order.service;

import com.portfolio.order.dto.PaymentRequest;
import com.portfolio.order.dto.PaymentResponse;
import com.portfolio.order.exception.PaymentFailedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * payment-service 호출 전담 클라이언트.
 */
@Slf4j
@Component
public class PaymentClient {

    private final RestClient restClient;

    public PaymentClient(@Qualifier("paymentServiceRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * 결제 요청.
     *
     * @throws PaymentFailedException 결제 거절 또는 결제 서비스 장애 시
     */
    public PaymentResponse processPayment(PaymentRequest request) {
        log.info("Processing payment: orderId={}, amount={}", request.orderId(), request.amount());
        try {
            PaymentResponse response = restClient.post()
                    .uri("/api/payments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new PaymentFailedException(
                                "Payment service error: status=" + res.getStatusCode());
                    })
                    .body(PaymentResponse.class);

            if (response == null || !"SUCCESS".equals(response.status())) {
                throw new PaymentFailedException("Payment was not successful: " +
                        (response != null ? response.status() : "null response"));
            }
            return response;
        } catch (RestClientException e) {
            throw new PaymentFailedException(
                    "Failed to call payment-service: " + e.getMessage(), e);
        }
    }
}