package com.portfolio.product.controller;

import com.portfolio.product.dto.ProductRequest;
import com.portfolio.product.dto.ProductResponse;
import com.portfolio.product.service.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/**
 * Product REST API..
 *
 * <p>HTTP 입출력만 담당하고 비즈니스 로직은 {@link ProductService}에 위임한다 —
 * 컨트롤러는 얇게 유지(thin controller).
 *
 * <p>{@code @Validated}는 클래스 레벨로 붙여서 메서드 파라미터의 제약 조건
 * (예: {@code @RequestParam}에 붙은 {@code @Min}) 검증을 활성화한다.
 * {@code @RequestBody}에 붙은 {@code @Valid}와는 별개의 메커니즘이다.
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Validated
public class ProductController {

    private final ProductService productService;

    /**
     * 전체 상품 목록 조회.
     *
     * @return 200 OK — 상품 배열 (빈 경우 [])
     */
    @GetMapping
    public List<ProductResponse> list() {
        return productService.findAll();
    }

    /**
     * 단일 상품 조회.
     *
     * @return 200 OK — 상품 정보. 존재하지 않으면 404 ProblemDetail
     */
    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable Long id) {
        return productService.findById(id);
    }

    /**
     * 신규 상품 생성.
     * <p>요청 본문이 검증 실패 시 {@link org.springframework.web.bind.MethodArgumentNotValidException}
     * → GlobalExceptionHandler가 400 ProblemDetail로 변환.
     *
     * @return 201 Created + Location 헤더 + 생성된 상품 정보
     */
    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        ProductResponse created = productService.create(request);
        URI location = URI.create("/api/products/" + created.id());
        return ResponseEntity.created(location).body(created);
    }

    /**
     * 상품 삭제.
     *
     * @return 204 No Content (성공). 404 ProblemDetail (존재하지 않음)
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        productService.delete(id);
    }

    /**
     * 재고 차감 (커스텀 액션).
     * <p>RESTful 관점에서 "동사형 엔드포인트"는 지양되지만, 단순 PUT/PATCH로
     * 표현하기 어려운 비즈니스 액션은 sub-resource 경로로 명명하는 것이 일반적이다.
     *
     * @return 200 OK — 차감 후 상품 정보. 재고 부족 시 409 ProblemDetail
     */
    @PostMapping("/{id}/decrease-stock")
    public ProductResponse decreaseStock(
            @PathVariable Long id,
            @RequestParam @Min(value = 1, message = "차감 수량은 1 이상이어야 합니다") int quantity) {
        return productService.decreaseStock(id, quantity);
    }
}