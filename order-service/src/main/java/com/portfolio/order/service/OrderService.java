package com.portfolio.order.service;

import com.portfolio.order.domain.Order;
import com.portfolio.order.domain.OrderStatus;
import com.portfolio.order.dto.OrderRequest;
import com.portfolio.order.dto.OrderResponse;
import com.portfolio.order.dto.PaymentRequest;
import com.portfolio.order.dto.PaymentResponse;
import com.portfolio.order.dto.ProductResponse;
import com.portfolio.order.exception.OrderNotFoundException;
import com.portfolio.order.exception.PaymentFailedException;
import com.portfolio.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 주문 비즈니스 로직.
 *
 * <p><b>주문 생성 워크플로우</b>:
 * <ol>
 *   <li>product-service에서 상품 정보 조회 (가격 확인)</li>
 *   <li>총 금액 계산 후 주문을 PENDING 상태로 저장</li>
 *   <li>payment-service에 결제 요청</li>
 *   <li>성공 시 PAID, 실패 시 FAILED로 상태 업데이트</li>
 * </ol>
 *
 * <p><b>중요</b>: 외부 서비스 호출은 트랜잭션 밖에서 하는 것이 원칙이지만,
 * 포트폴리오 단계에서는 단순화를 위해 한 트랜잭션 안에서 처리.
 * 운영 환경에서는 Saga 패턴이나 Outbox 패턴 적용 필요 (ADR로 기록).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductClient productClient;
    private final PaymentClient paymentClient;

    public List<OrderResponse> findAll() {
        log.debug("Fetching all orders");
        return orderRepository.findAll().stream()
                .map(OrderResponse::from)
                .toList();
    }

    public OrderResponse findById(Long id) {
        log.debug("Fetching order id={}", id);
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
        return OrderResponse.from(order);
    }

    /**
     * 주문 생성 — product 조회 + payment 처리까지 통합 워크플로우.
     */
    @Transactional
    public OrderResponse create(OrderRequest request) {
        log.info("Creating order: productId={}, quantity={}",
                request.productId(), request.quantity());

        // 1) product-service에서 상품 정보 조회
        ProductResponse product = productClient.getProduct(request.productId());
        log.info("Product fetched: id={}, name={}, price={}",
                product.id(), product.name(), product.price());

        // 2) 총 금액 계산
        BigDecimal totalAmount = product.price()
                .multiply(BigDecimal.valueOf(request.quantity()));

        // 3) 주문을 PENDING으로 저장
        Order order = Order.builder()
                .productId(product.id())
                .quantity(request.quantity())
                .totalAmount(totalAmount)
                .status(OrderStatus.PENDING)
                .build();
        Order saved = orderRepository.save(order);
        log.info("Order created (PENDING): id={}", saved.getId());

        // 4) payment-service에 결제 요청
        try {
            PaymentResponse payment = paymentClient.processPayment(
                    new PaymentRequest(saved.getId(), totalAmount)
            );
            saved.markAsPaid(payment.id());
            log.info("Order paid: orderId={}, paymentId={}", saved.getId(), payment.id());
        } catch (PaymentFailedException e) {
            // 결제 실패 시 주문 상태만 FAILED로 업데이트하고 예외는 다시 던짐
            saved.markAsFailed();
            log.warn("Payment failed for order: id={}, reason={}", saved.getId(), e.getMessage());
            throw e;   // GlobalExceptionHandler가 422로 변환
        }

        return OrderResponse.from(saved);
    }

    @Transactional
    public void delete(Long id) {
        log.info("Deleting order id={}", id);
        if (!orderRepository.existsById(id)) {
            throw new OrderNotFoundException(id);
        }
        orderRepository.deleteById(id);
    }
}