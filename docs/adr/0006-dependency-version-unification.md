# ADR-0006: 의존성 버전 통일 정책

## Status
Accepted (2026-04-27)

## Context

3개 마이크로서비스(product/order/payment)는 **모노레포** 구조로 portfolio-app 한 레포에서
관리한다. 모노레포의 흔한 사고:

1. **버전 드리프트**: 서비스마다 Spring Boot 3.5.13, 3.5.14, 3.6.0이 섞임
2. **의존성 충돌**: logstash-encoder 8.0과 9.0이 서비스마다 달라 빌드/런타임 차이
3. **CVE 패치 누락**: 한 서비스만 패치되고 다른 서비스는 옛 버전 유지

또한 Phase 1에서 logstash-encoder 9.0이 Jackson 3.0을 요구해 Spring Boot 3.5.x와 호환 안 되는
사고를 미리 방지해야 한다

## Decision

**전 서비스 버전 통일**.

### 박제된 버전
| 항목 | 버전 |
|---|---|
| Java | 21 LTS (Eclipse Temurin) |
| Spring Boot | **3.5.13** (모든 서비스) |
| `org.springframework.boot` plugin | **3.5.13** |
| `io.spring.dependency-management` | **1.1.7** |
| `com.gorylenko.gradle-git-properties` | **2.4.2** |
| `net.logstash.logback:logstash-logback-encoder` | **8.0** (9.0 금지) |

### 변경 절차
1. 새 버전이 필요하면 **모든 3개 서비스의 build.gradle을 동시 갱신**
2. 변경 사유를 commit message 본문에 명시

### 검증
- CI 단계에서 build.gradle의 버전이 PROJECT_CONTEXT.md와 일치하는지 검증

## Consequences

### 긍정
+ 한 서비스에서 빌드 통과하면 다른 서비스도 통과 보장 (의존성 동일하므로)
+ CVE 패치 시 모든 서비스에 한 번에 적용
+ 면접 시 "버전 관리 전략"으로 어필 가능
+ logstash-encoder 8.0 박제로 Jackson 3.0 호환성 사고 사전 차단

### 부정
- 한 서비스만 새 버전이 필요한 경우(드물게)도 강제 통일 → 모든 서비스 동시 업그레이드 필요
- Renovate/Dependabot 같은 자동 업그레이드 도구의 PR이 3개 서비스 동시 처리되도록 설정 필요
  - Phase 3 CI 단계에서 검토

### 면접 답변용 포인트
"모노레포에서 서비스마다 의존성 버전이 드리프트하는 사고를 방지하기 위해, 전 서비스의 핵심
의존성 버전을 PROJECT_CONTEXT.md에 박제하고 변경 시 동시 갱신 원칙을 적용했습니다.
실제 사례로 logstash-encoder 9.0이 Jackson 3.0을 요구해 Spring Boot 3.5.x와 호환 안 되는
문제를 미리 발견하고 8.0으로 고정한 결정이 박제되어 있습니다."

## Alternatives Considered

### 버전 자유 — 서비스마다 최신 채택
- 빠른 신기능 도입 가능
- 그러나 모노레포에서 의존성 드리프트 사고 빈번
- 거절

### Gradle Version Catalog (`libs.versions.toml`)
- Gradle 7.0+의 표준 의존성 관리 패턴
- 실제 운영에선 더 깔끔함
- 본 프로젝트는 단순함 우선이라 PROJECT_CONTEXT.md 박제로 충분
- Phase 6+에서 도입 검토 가능
- 거절 (현 단계)

## References
- Gradle Version Catalogs 공식 문서 (미래 도입 후보)