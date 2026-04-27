package com.portfolio.order.service;

import com.portfolio.order.dto.ProductResponse;
import com.portfolio.order.exception.ProductUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * product-service의 단일 호출 책임 클래스.
 *
 * <p>설계 결정:
 * <ul>
 *   <li>OrderService가 RestClient를 직접 호출하면 비즈니스 로직과 통신 코드가 섞임 → 별도 클라이언트 클래스로 분리</li>
 *   <li>4xx/5xx 응답은 모두 {@link ProductUnavailableException}으로 wrapping → OrderService는 비즈니스 흐름에 집중</li>
 *   <li>RestClient는 {@link com.portfolio.order.config.RestClientConfig}에서 빈으로 등록된 것을 주입받음</li>
 * </ul>
 */
@Slf4j
@Component
public class ProductClient {

    private final RestClient restClient;

    public ProductClient(@Qualifier("productServiceRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * 상품 ID로 단일 상품 조회.
     *
     * @throws ProductUnavailableException product-service가 응답 불가하거나 4xx/5xx 반환 시
     */
    public ProductResponse getProduct(Long productId) {
        log.debug("Fetching product from product-service: id={}", productId);
        try {
            return restClient.get()
                    .uri("/api/products/{id}", productId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new ProductUnavailableException(
                                "Product not available: id=" + productId + ", status=" + res.getStatusCode(),
                                null
                        );
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new ProductUnavailableException(
                                "Product service error: status=" + res.getStatusCode(),
                                null
                        );
                    })
                    .body(ProductResponse.class);
        } catch (RestClientException e) {
            // 네트워크 장애, 타임아웃 등
            throw new ProductUnavailableException(
                    "Failed to call product-service: " + e.getMessage(), e);
        }
    }
}