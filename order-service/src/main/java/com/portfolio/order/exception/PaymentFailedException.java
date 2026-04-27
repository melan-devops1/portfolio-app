package com.portfolio.order.exception;

/**
 * payment-service 호출 실패 또는 결제 거절 시 발생.
 */
public class PaymentFailedException extends RuntimeException {
    public PaymentFailedException(String message) {
        super(message);
    }

    public PaymentFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}