package com.portfolio.order.exception;

/**
 * product-service 호출 실패 시 발생.
 * <p>다운스트림 장애를 명확히 구분하기 위해 별도 예외 정의.
 */
public class ProductUnavailableException extends RuntimeException {
    public ProductUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}