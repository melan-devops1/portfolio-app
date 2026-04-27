package com.portfolio.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Chaos 시뮬레이션 설정.
 * <p>application.yml의 {@code chaos.*} 프로퍼티와 매핑.
 *
 * <p><b>면접 답변용</b>: 운영 시연 장치를 코드 상수가 아닌 외부 설정으로 빼서
 * 환경변수로 동적 제어 가능. 시연 시나리오에 따라 에러율 0%~50% 자유롭게 조정 가능.
 *
 * @param enabled 카오스 시뮬레이션 전체 on/off
 * @param errorRatePercent 0~100 사이. 결제 거절 발생 확률
 * @param minDelayMs 최소 처리 지연 (밀리초)
 * @param maxDelayMs 최대 처리 지연 (밀리초)
 */
@ConfigurationProperties(prefix = "chaos")
public record ChaosProperties(
        boolean enabled,
        int errorRatePercent,
        long minDelayMs,
        long maxDelayMs
) {
    public ChaosProperties {
        if (errorRatePercent < 0 || errorRatePercent > 100) {
            throw new IllegalArgumentException("errorRatePercent must be 0~100");
        }
        if (minDelayMs < 0 || maxDelayMs < minDelayMs) {
            throw new IllegalArgumentException("Invalid delay range");
        }
    }
}