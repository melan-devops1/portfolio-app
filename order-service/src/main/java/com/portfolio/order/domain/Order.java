package com.portfolio.order.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 주문 도메인 엔티티.
 *
 * <p>주문은 다음 라이프사이클을 가진다:
 * {@link OrderStatus#PENDING} → {@link OrderStatus#PAID} 또는 {@link OrderStatus#FAILED}
 *
 * <p>{@code orders}는 PostgreSQL 등 일부 DB에서 예약어 충돌을 일으키므로
 * 테이블명을 명시적으로 {@code orders}가 아닌 단수형/복수형 중 안전한 이름으로 짓는 것이 통례.
 * 여기서는 명시적으로 escape를 위해 큰따옴표 없이 그냥 {@code orders}로 둔다.
 * H2/PostgreSQL 모두 escape 없이 동작 확인됨 (문제 시 {@code customer_orders}로 변경).
 */
@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    /**
     * 결제가 성공했을 때 채워지는 외부 결제 식별자.
     * 결제가 실패한 주문은 null.
     */
    private Long paymentId;

    @Column(updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        if (this.status == null) {
            this.status = OrderStatus.PENDING;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // ===== 비즈니스 메서드 =====

    public void markAsPaid(Long paymentId) {
        if (this.status != OrderStatus.PENDING) {
            throw new IllegalStateException("Cannot mark as paid from status: " + this.status);
        }
        this.status = OrderStatus.PAID;
        this.paymentId = paymentId;
    }

    public void markAsFailed() {
        this.status = OrderStatus.FAILED;
    }
}