package com.portfolio.order.config;

import com.portfolio.order.common.RequestIdFilter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 외부 서비스 호출용 {@link RestClient} 빈 설정.
 *
 * <p>각 다운스트림 서비스(product-service, payment-service)별로 독립된 RestClient를 빈으로 등록한다.
 * 이유:
 * <ul>
 *   <li>서비스마다 baseUrl, 타임아웃이 다를 수 있음</li>
 *   <li>장애 추적 시 어떤 클라이언트에서 문제가 발생했는지 명확히 구분</li>
 *   <li>나중에 Resilience4j로 서킷브레이커를 다른 정책으로 적용 가능</li>
 * </ul>
 *
 * <p>모든 RestClient는 다음 인터셉터를 공통 적용:
 * <ul>
 *   <li>X-Request-Id 자동 전파 — MDC의 requestId를 다운스트림 호출 헤더에 주입</li>
 *   <li>요청/응답 로깅 — 호출 추적 용이</li>
 * </ul>
 */
@Slf4j
@Configuration
public class RestClientConfig {

    @Value("${external.product-service.url}")
    private String productServiceUrl;

    @Value("${external.product-service.timeout-ms}")
    private int productServiceTimeoutMs;

    @Value("${external.payment-service.url}")
    private String paymentServiceUrl;

    @Value("${external.payment-service.timeout-ms}")
    private int paymentServiceTimeoutMs;

    /**
     * product-service 호출 전용 RestClient.
     */
    @Bean
    public RestClient productServiceRestClient() {
        return buildRestClient(productServiceUrl, productServiceTimeoutMs, "product-service");
    }

    /**
     * payment-service 호출 전용 RestClient.
     */
    @Bean
    public RestClient paymentServiceRestClient() {
        return buildRestClient(paymentServiceUrl, paymentServiceTimeoutMs, "payment-service");
    }

    private RestClient buildRestClient(String baseUrl, int timeoutMs, String targetName) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .requestInterceptor(requestIdPropagationInterceptor())
                .requestInterceptor(loggingInterceptor(targetName))
                .build();
    }

    /**
     * MDC에 있는 requestId를 다운스트림 호출에 자동 전파.
     * <p>이게 분산 트레이싱의 출발점 — 한 요청이 여러 서비스를 거쳐도 동일한 ID로 묶임.
     */
    private ClientHttpRequestInterceptor requestIdPropagationInterceptor() {
        return (request, body, execution) -> {
            String requestId = MDC.get(RequestIdFilter.MDC_KEY);
            if (requestId != null) {
                request.getHeaders().add(RequestIdFilter.REQUEST_ID_HEADER, requestId);
            }
            return execution.execute(request, body);
        };
    }

    /**
     * 외부 호출 전후 로깅 — 응답 시간/상태 코드 추적용.
     */
    private ClientHttpRequestInterceptor loggingInterceptor(String targetName) {
        return (request, body, execution) -> {
            long start = System.currentTimeMillis();
            log.info("→ {} call: {} {}", targetName, request.getMethod(), request.getURI());
            try {
                var response = execution.execute(request, body);
                long elapsed = System.currentTimeMillis() - start;
                log.info("← {} response: status={} elapsed={}ms",
                        targetName, response.getStatusCode().value(), elapsed);
                return response;
            } catch (Exception e) {
                long elapsed = System.currentTimeMillis() - start;
                log.error("✗ {} failed after {}ms: {}", targetName, elapsed, e.getMessage());
                throw e;
            }
        };
    }
}