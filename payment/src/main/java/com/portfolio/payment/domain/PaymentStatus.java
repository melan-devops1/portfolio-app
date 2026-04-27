package com.portfolio.payment.domain;

public enum PaymentStatus {
    SUCCESS,    // 결제 성공
    DECLINED,   // 결제 거절 (의도적 시뮬레이션 또는 일반 거절)
    FAILED      // 시스템 오류
}