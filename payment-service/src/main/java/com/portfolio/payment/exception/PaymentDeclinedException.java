package com.portfolio.payment.exception;

/**
 * 결제 거절 예외.
 * <p>의도적 chaos 시뮬레이션에서 발생하는 거절을 표현한다.
 * 클라이언트에는 422 Unprocessable Entity로 응답.
 */
public class PaymentDeclinedException extends RuntimeException {
    public PaymentDeclinedException(String reason) {
        super(reason);
    }
}