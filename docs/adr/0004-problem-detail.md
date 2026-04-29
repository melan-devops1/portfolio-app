# ADR-0004: RFC 7807 ProblemDetail 표준 에러 응답

## Status
Accepted (2026-04-27)

## Context

REST API의 에러 응답 포맷을 일관되게 정의해야 한다. 흔한 안티패턴:

{ "error": "Product not found" }                  // case 1: ad-hoc
{ "code": 404, "message": "..." }                 // case 2: 자체 표준
{ "status": "fail", "errors": [...] }             // case 3: 또 다른 자체 표준

문제:
- 클라이언트가 서비스마다 다른 응답 구조를 다뤄야 함
- 디버깅 시 "어떤 필드를 봐야 하나?" 매번 확인
- 스프링 생태계에서 ResponseEntityExceptionHandler가 던지는 default 포맷과도 충돌

## Decision

**RFC 7807 ProblemDetail 표준 채택**.

Spring Framework 6.0부터 표준 지원하는 `org.springframework.http.ProblemDetail`을 사용한다.

### 응답 포맷 (표준)
```json
{
  "type": "https://example.com/probs/product-not-found",
  "title": "Product Not Found",
  "status": 404,
  "detail": "Product with id 123 not found",
  "instance": "/api/products/123"
}
```

### GlobalExceptionHandler 패턴
```java
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ProductNotFoundException.class)
  public ProblemDetail handleProductNotFound(ProductNotFoundException ex) {
    var problem = ProblemDetail.forStatusAndDetail(
        HttpStatus.NOT_FOUND, ex.getMessage());
    problem.setType(URI.create("urn:problem-type:product-not-found"));
    problem.setTitle("Product Not Found");
    return problem;
  }
}
```

### Map 기반 응답 금지
프로젝트 응답 작성 규칙 #7에 박제:
> "ProblemDetail 외 응답 금지. 새 컨트롤러 만들 때 옛날 방식(Map<String,Object>) 절대 금지."

## Consequences

### 긍정
+ IETF 표준 → 클라이언트 라이브러리들이 표준 인식
+ Spring Framework 내장 → 별도 의존성 불필요
+ 모든 마이크로서비스(product/order/payment) 일관 적용
+ 면접에서 "에러 응답 표준화"를 표준 기반으로 답변 가능

### 부정
- 옛 코드(Spring 5.x 시절) 패턴에 익숙한 개발자에게 학습 곡선
    - 본 프로젝트는 새 작성이라 무관

### 면접 답변용 포인트
"에러 응답 포맷은 RFC 7807 ProblemDetail을 채택했습니다. Spring Framework 6.0+ 내장이라
별도 라이브러리 없이도 표준 준수가 가능합니다. 모든 마이크로서비스가 동일 포맷을 쓰면
프론트엔드/모바일 클라이언트의 에러 처리 코드가 단순해지고, OpenAPI 스펙에도 표준 schema로
표현 가능합니다."

## Alternatives Considered

### 자체 정의 에러 포맷
- (예: `{code, message, errors[]}`)
- 표준 부재 → 서비스/팀마다 갈라짐
- 거절

### Spring 기본 에러 응답 (`/error` 엔드포인트)
- Whitelabel Error Page로 자동 처리
- 프로덕션에선 정보 노출 위험
- 표준 포맷 아님
- 거절

## References
- RFC 7807: Problem Details for HTTP APIs
- Spring Framework ProblemDetail 공식 문서