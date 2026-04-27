package com.portfolio.order.domain;

public enum OrderStatus {
    PENDING,    // 주문 생성, 결제 대기
    PAID,       // 결제 완료
    FAILED      // 결제 실패 또는 외부 서비스 장애
}