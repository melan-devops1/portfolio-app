# ADR-0003: 구조화 로깅 — logstash-encoder (Spring Boot 3.4 내장 대신)

## Status
Accepted (2026-04-27)

## Context

K8s 환경의 로그 수집 표준은 **JSON 구조화 로그**다:
- Fluent Bit, Fluentd 등의 수집기가 자동 파싱
- Elasticsearch, Loki, CloudWatch Logs Insights에서 필드별 검색 가능
- MDC, trace ID 등의 컨텍스트가 별도 필드로 분리되어 관찰성 향상

Spring Boot 3.4부터는 **내장 Structured Logging** 기능을 제공한다 (`logging.structured.format.console=ecs`).
하지만 검토 결과 본 프로젝트엔 부적합:

1. **Logstash JSON Encoder 표준이 더 광범위**: 업계 EFK 스택에서 가장 보편적인 포맷
2. **MDC 필드가 자동 promotion**: `requestId` 같은 MDC 값이 JSON 필드로 자동 분리
3. **logback-spring.xml profile 분리 패턴**과 더 자연스럽게 정합
4. **logstash-encoder의 ConsoleAppender 설정**이 Spring Boot 3.4 내장보다 옵션 풍부

## Decision

**`net.logstash.logback:logstash-logback-encoder:8.0` 채택**.

### 버전 결정 근거
- 9.0은 **Jackson 3.0 요구**. Spring Boot 3.5.13은 Jackson 2.x 의존이라 호환 불가
- 8.0이 Jackson 2.x와 호환되는 마지막 안정 버전 (PROJECT_CONTEXT.md 라인 76 참조)

### 프로파일별 분리 (logback-spring.xml)
- **local**: 텍스트 형식 (사람이 읽기 쉬운 컬러 콘솔 패턴)
- **prod, k8s**: JSON 구조화 로그 (수집기 친화적)

### MDC 필드 자동 노출
- `requestId` (X-Request-Id 헤더, ADR-0005)
- `service` (spring.application.name)
- `APP_NAME` (환경변수에서 주입)

## Consequences

### 긍정
+ EFK 스택(Phase 4 Epic 8) 도입 시 별도 변환 없이 바로 색인 가능
+ MDC의 requestId가 자동 필드화 → Kibana에서 trace 단위 그룹핑 가능
+ Spring Boot 내장 Structured Logging 대비 logback-spring.xml에서 세밀 제어 가능
+ logstash-encoder는 사실상 업계 표준이라 면접 시 친숙도 높음

### 부정
- Spring Boot 3.4 내장 기능을 굳이 외부 라이브러리로 대체했다는 설명 필요
- logstash-encoder 9.0으로 갈 때 Jackson 3.0 요구 — Spring Boot 메이저 업그레이드와 동시 진행 필요
    - 함정 사항으로 PROJECT_CONTEXT.md에 명시됨

### 면접 답변용 포인트
"Spring Boot 3.4 내장 Structured Logging도 검토했지만, EFK 스택의 사실상 표준인
logstash-encoder를 채택했습니다. MDC 필드가 자동으로 JSON 필드로 promotion되어 분산
추적의 requestId가 Kibana에서 즉시 검색 가능합니다. 단, 9.0이 Jackson 3.0을 요구해
Spring Boot 3.5.x와 호환되는 8.0 버전을 고정했습니다."

## Alternatives Considered

### Spring Boot 3.4 내장 Structured Logging
- 외부 라이브러리 불필요
- 그러나 logstash-encoder 대비 설정 옵션 제한
- MDC promotion 등 세밀 제어 부족
- 거절

### 평문 로그 + Fluent Bit 파서
- Fluent Bit이 정규식으로 평문을 파싱
- 로그 포맷 변경 시마다 파서 정규식 갱신 필요 → 깨지기 쉬움
- 구조화 로그가 운영 부담 적음
- 거절

### Logback 자체 JSON encoder
- 외부 라이브러리 없이 내장 기능만 사용
- 그러나 MDC 필드 자동 promotion 등의 편의 기능 부재
- 거절

## References
- logstash-logback-encoder 공식 GitHub
- Spring Boot Logback 공식 문서
- 호환성: ADR-0006 (의존성 버전 통일 정책에서 8.0 고정)