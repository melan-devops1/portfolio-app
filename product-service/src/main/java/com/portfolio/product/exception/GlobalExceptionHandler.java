package com.portfolio.product.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String ERROR_DOCS_BASE = "https://api.portfolio.com/errors";

    /**
     * 비즈니스 예외 - 상품 미발견
     */
    @ExceptionHandler(ProductNotFoundException.class)
    public ProblemDetail handleProductNotFound(ProductNotFoundException ex) {
        log.warn("Product not found: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Product Not Found");
        problem.setType(URI.create(ERROR_DOCS_BASE + "/product-not-found"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /**
     * 비즈니스 규칙 위반 (재고 부족 등)
     */
    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalState(IllegalStateException ex) {
        log.warn("Business rule violation: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Business Rule Violation");
        problem.setType(URI.create(ERROR_DOCS_BASE + "/business-rule-violation"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /**
     * @Valid 검증 실패 - ResponseEntityExceptionHandler 부모의 메서드를 오버라이드
     * 필드별 오류를 properties에 담아 클라이언트에 명확히 전달
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        List<Map<String, String>> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> Map.of(
                        "field", err.getField(),
                        "rejectedValue", String.valueOf(err.getRejectedValue()),
                        "message", err.getDefaultMessage() != null ? err.getDefaultMessage() : "invalid"
                ))
                .toList();

        log.warn("Validation failed: {}", fieldErrors);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "One or more fields have validation errors"
        );
        problem.setTitle("Validation Failed");
        problem.setType(URI.create(ERROR_DOCS_BASE + "/validation-failed"));
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("fieldErrors", fieldErrors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    /**
     * 모든 예외의 최종 안전망
     * 운영 환경에선 stacktrace를 클라이언트에 노출하지 않음 (보안)
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);   // stacktrace는 서버 로그에만
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error"      // 클라이언트에는 모호하게
        );
        problem.setTitle("Internal Server Error");
        problem.setType(URI.create(ERROR_DOCS_BASE + "/internal-error"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}