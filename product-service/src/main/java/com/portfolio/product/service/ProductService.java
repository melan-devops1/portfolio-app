package com.portfolio.product.service;

import com.portfolio.product.domain.Product;
import com.portfolio.product.dto.ProductRequest;
import com.portfolio.product.dto.ProductResponse;
import com.portfolio.product.exception.ProductNotFoundException;
import com.portfolio.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Product 비즈니스 로직 계층.
 *
 * <p>Controller로부터 호출되며, Repository를 통해 영속화/조회한다.
 * 모든 public 메서드는 트랜잭션 경계 — 클래스 레벨 {@code @Transactional(readOnly = true)}로
 * 기본은 읽기 전용 트랜잭션, 쓰기 메서드만 명시적으로 {@code @Transactional} 오버라이드한다.
 *
 * <p>{@code readOnly = true}는 단순한 힌트가 아니라 JPA 세션 flush 모드를 MANUAL로 바꿔
 * 더티 체킹/플러시 비용을 제거하므로 조회 성능 개선 효과가 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

    /**
     * 모든 상품 조회.
     */
    public List<ProductResponse> findAll() {
        log.debug("Fetching all products");
        return productRepository.findAll().stream()
                .map(ProductResponse::from)
                .toList();
    }

    /**
     * 단일 상품 조회. 존재하지 않으면 {@link ProductNotFoundException} 발생 → 404 응답.
     */
    public ProductResponse findById(Long id) {
        log.debug("Fetching product id={}", id);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        return ProductResponse.from(product);
    }

    /**
     * 신규 상품 생성.
     */
    @Transactional
    public ProductResponse create(ProductRequest request) {
        log.info("Creating product: name={}, price={}, stock={}",
                request.name(), request.price(), request.stock());

        Product product = Product.builder()
                .name(request.name())
                .price(request.price())
                .stock(request.stock())
                .build();

        Product saved = productRepository.save(product);
        log.info("Product created: id={}", saved.getId());
        return ProductResponse.from(saved);
    }

    /**
     * 상품 삭제. 존재하지 않으면 404.
     */
    @Transactional
    public void delete(Long id) {
        log.info("Deleting product id={}", id);
        if (!productRepository.existsById(id)) {
            throw new ProductNotFoundException(id);
        }
        productRepository.deleteById(id);
        log.info("Product deleted: id={}", id);
    }

    /**
     * 재고 차감. 부족 시 도메인 검증 로직({@link Product#decreaseStock(int)})에서
     * {@link IllegalStateException} 발생 → GlobalExceptionHandler에서 409 Conflict 응답.
     *
     * <p>JPA dirty checking으로 별도 save() 호출 없이 트랜잭션 종료 시 자동 UPDATE.
     */
    @Transactional
    public ProductResponse decreaseStock(Long id, int quantity) {
        log.info("Decreasing stock: productId={}, quantity={}", id, quantity);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        product.decreaseStock(quantity);   // 도메인 메서드가 검증 + 상태 변경
        // save() 호출 불필요 — dirty checking이 알아서 UPDATE

        log.info("Stock decreased: productId={}, remaining={}", id, product.getStock());
        return ProductResponse.from(product);
    }
}