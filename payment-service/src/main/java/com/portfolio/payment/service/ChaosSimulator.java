package com.portfolio.payment.service;

import com.portfolio.payment.config.ChaosProperties;
import com.portfolio.payment.exception.PaymentDeclinedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Chaos 주입 책임 클래스.
 *
 * <p>Alertmanager의 다음 알림 규칙을 트리거하기 위한 운영 시연 장치:
 * <ul>
 *   <li><b>에러율 임계치</b>: 5분간 에러율 5% 초과 시 알림 → {@code errorRatePercent=5}로 정확히 매칭</li>
 *   <li><b>P99 지연 임계치</b>: 응답시간 P99 > 2초 시 알림 → {@code maxDelayMs=2000}으로 정확히 매칭</li>
 * </ul>
 *
 * <p><b>운영 환경 비활성화</b>: {@code chaos.enabled=false} 환경변수로 즉시 끌 수 있음.
 * 운영 배포 시엔 반드시 false로.
 *
 * <p><b>왜 별도 클래스로 분리했나</b>:
 * <ul>
 *   <li>단일 책임 원칙 — 비즈니스 로직과 카오스 주입 분리</li>
 *   <li>테스트 용이성 — 단위 테스트에서 mock으로 비활성화 가능</li>
 *   <li>운영 환경에서 통째로 비활성화 가능 (조건부 빈)</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChaosSimulator {

    private final ChaosProperties props;

    /**
     * 의도적 처리 지연 주입.
     *
     * @return 실제로 잠든 시간 (밀리초). 메트릭/응답 메타데이터로 활용
     */
    public long injectLatency() {
        if (!props.enabled()) {
            return 0L;
        }

        long delay = ThreadLocalRandom.current()
                .nextLong(props.minDelayMs(), props.maxDelayMs() + 1);

        log.debug("Chaos: injecting latency {}ms", delay);

        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            // Interrupt 신호 보존 — JVM 표준 패턴
            Thread.currentThread().interrupt();
            log.warn("Latency injection interrupted");
        }
        return delay;
    }

    /**
     * 확률적으로 결제 거절 발생.
     *
     * @throws PaymentDeclinedException errorRatePercent 확률로 발생
     */
    public void maybeFailRandomly() {
        if (!props.enabled()) {
            return;
        }

        int roll = ThreadLocalRandom.current().nextInt(100);
        if (roll < props.errorRatePercent()) {
            log.warn("Chaos: simulating payment decline (roll={}, threshold={})",
                    roll, props.errorRatePercent());
            throw new PaymentDeclinedException(
                    "Payment declined by gateway (simulated for ops demo)");
        }
    }
}