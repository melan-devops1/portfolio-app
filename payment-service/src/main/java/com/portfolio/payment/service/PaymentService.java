package com.portfolio.payment.service;

import com.portfolio.payment.domain.Payment;
import com.portfolio.payment.domain.PaymentStatus;
import com.portfolio.payment.dto.PaymentRequest;
import com.portfolio.payment.dto.PaymentResponse;
import com.portfolio.payment.exception.PaymentNotFoundException;
import com.portfolio.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 결제 비즈니스 로직.
 *
 * <p><b>처리 흐름</b>:
 * <ol>
 *   <li>{@link ChaosSimulator}로 의도적 지연 주입 (100~2000ms)</li>
 *   <li>{@link ChaosSimulator}로 확률적 거절 (5%)</li>
 *   <li>거절 안 되면 SUCCESS 상태로 저장</li>
 * </ol>
 *
 * <p>거절은 {@link com.portfolio.payment.exception.PaymentDeclinedException}으로 던지고,
 * GlobalExceptionHandler가 422로 변환. 다만 <b>거절된 결제도 DB엔 DECLINED 상태로 남김</b> —
 * 운영 분석/Kibana 검색용.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final ChaosSimulator chaosSimulator;

    public List<PaymentResponse> findAll() {
        log.debug("Fetching all payments");
        return paymentRepository.findAll().stream()
                .map(PaymentResponse::from)
                .toList();
    }

    public PaymentResponse findById(Long id) {
        log.debug("Fetching payment id={}", id);
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));
        return PaymentResponse.from(payment);
    }

    /**
     * 결제 처리 — 의도적 지연/에러 시뮬레이션 포함.
     */
    @Transactional
    public PaymentResponse process(PaymentRequest request) {
        log.info("Processing payment: orderId={}, amount={}",
                request.orderId(), request.amount());

        long startTime = System.currentTimeMillis();

        // 1) 의도적 지연 주입 (실제 외부 결제 게이트웨이 호출 시뮬레이션)
        long latencyMs = chaosSimulator.injectLatency();

        // 2) 확률적 결제 거절 시뮬레이션
        try {
            chaosSimulator.maybeFailRandomly();
        } catch (Exception e) {
            // 거절된 결제도 DB에 기록 — 운영 분석용
            Payment declined = Payment.builder()
                    .orderId(request.orderId())
                    .amount(request.amount())
                    .status(PaymentStatus.DECLINED)
                    .processingTimeMs(System.currentTimeMillis() - startTime)
                    .build();
            paymentRepository.save(declined);
            log.warn("Payment declined and recorded: orderId={}", request.orderId());
            throw e;   // GlobalExceptionHandler가 422로 변환
        }

        // 3) 정상 처리
        Payment payment = Payment.builder()
                .orderId(request.orderId())
                .amount(request.amount())
                .status(PaymentStatus.SUCCESS)
                .processingTimeMs(System.currentTimeMillis() - startTime)
                .build();

        Payment saved = paymentRepository.save(payment);
        log.info("Payment succeeded: id={}, orderId={}, latencyMs={}",
                saved.getId(), saved.getOrderId(), latencyMs);

        return PaymentResponse.from(saved);
    }
}