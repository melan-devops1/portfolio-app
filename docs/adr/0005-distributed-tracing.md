# ADR-0005: RestClient + Request-Id 인터셉터로 분산 추적 기반 구축

## Status
Accepted (2026-04-27)

## Context

마이크로서비스 환경에서 **하나의 클라이언트 요청이 여러 서비스를 거치는 흐름을 추적**할 수
있어야 한다. 본 프로젝트의 호출 체인:
```
Client → order-service → product-service
                       → payment-service
```

이 흐름의 각 hop에서 **같은 request id가 로그에 찍혀야** 추적 가능. 미구현 시:
- 프로덕션 장애 시 "어디서 깨졌나?" 추적 불가
- Kibana/Loki에서 각 서비스 로그를 따로 봐야 함 — 시간순 정렬도 어려움

Phase 4 Epic 9에서 Jaeger/OpenTelemetry로 본격 분산 추적을 도입할 예정이지만,
**그 전에도 로그 기반의 가벼운 추적은 필수**.

## Decision

**MDC + X-Request-Id 헤더 + RestClient 인터셉터 조합**.

### 1) Inbound — RequestIdFilter
모든 HTTP 요청 진입 시:
- `X-Request-Id` 헤더가 있으면 → 그 값을 MDC에 저장
- 없으면 → UUID 생성하여 MDC에 저장 + 응답 헤더에 추가

```java
@Component
public class RequestIdFilter extends OncePerRequestFilter {
  protected void doFilterInternal(...) {
    String requestId = request.getHeader("X-Request-Id");
    if (requestId == null || requestId.isBlank()) {
      requestId = UUID.randomUUID().toString();
    }
    MDC.put("requestId", requestId);
    response.setHeader("X-Request-Id", requestId);
    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.clear();
    }
  }
}
```

### 2) Outbound — RestClient 인터셉터
서비스가 다른 서비스를 호출할 때 자동으로 MDC의 requestId를 헤더에 전파:

```java
@Bean
RestClient restClient() {
  return RestClient.builder()
    .requestInterceptor((request, body, execution) -> {
      String requestId = MDC.get("requestId");
      if (requestId != null) {
        request.getHeaders().add("X-Request-Id", requestId);
      }
      return execution.execute(request, body);
    })
    .build();
}
```

### 3) 로그 자동 노출
ADR-0003의 logstash-encoder가 MDC를 자동 promotion → JSON 로그에 `requestId` 필드로 노출.

## Consequences

### 긍정
+ 한 요청의 모든 서비스 로그를 `requestId`로 묶어서 검색 가능 (Kibana/Loki에서 1초)
+ Jaeger/OpenTelemetry 없이도 가벼운 추적 동작 — Phase 4 진입 전까지 충분
+ 클라이언트가 명시적으로 X-Request-Id 헤더를 보내면 그 값 그대로 사용 — 외부 시스템 추적 가능
+ Phase 4의 OpenTelemetry 도입 시 trace_id로 매끄럽게 전환 가능 (header propagation 패턴 동일)

### 부정
- Sampling, Span 계층, latency 측정 등 본격 분산 추적 기능 없음 → Phase 4에서 OpenTelemetry로 보강
- MDC는 Thread-local 기반 → 비동기/리액티브 코드에서 컨텍스트 전파 주의
    - 본 프로젝트는 동기 RestClient만 사용해 무관

### 면접 답변용 포인트
"분산 추적은 Phase 4에서 Jaeger/OpenTelemetry로 본격 도입 예정이지만, 그 전에도 로그 기반의
가벼운 추적이 필요해 RequestIdFilter + RestClient 인터셉터로 X-Request-Id 헤더 자동 전파를
구현했습니다. logstash-encoder의 MDC promotion과 결합해 Kibana에서 한 요청의 모든
서비스 로그를 1초에 그룹핑할 수 있습니다."

## Alternatives Considered

### Spring Cloud Sleuth (Brave 기반)
- 자동 trace_id, span_id 생성 + Zipkin 연동
- 그러나 Spring Boot 3.x에서 deprecated, **Micrometer Tracing**으로 대체됨
- Phase 4의 OpenTelemetry 도입과 정합성 위해 우선 가벼운 자체 구현 채택
- 거절 (현 단계)

### Micrometer Tracing + OpenTelemetry
- 현행 표준
- Phase 4의 본격 분산 추적 도입 시 채택 예정
- 본 ADR은 그 전 단계의 가벼운 추적을 다룸

### RestTemplate
- ClientHttpRequestInterceptor 패턴 동일 적용 가능
- 그러나 Spring 5.0+에서 RestClient/WebClient가 권장되며 RestTemplate은 maintenance mode
- 거절

## References
- Spring Framework RestClient 공식 문서
- SLF4J MDC 공식 문서
- ADR-0003 (logstash-encoder의 MDC 자동 promotion)
- Phase 4 Epic 9에서 OpenTelemetry로 보강 예정