# ADR-0001: Spring Boot 3.5 채택 (vs 4.0)

## Status
Accepted (2026-04-27)

## Context
프로젝트 시작 시점에 Spring Boot 4.0.5와 3.5.13이 함께 지원되고 있었다.
포트폴리오 프로젝트지만 실제 기업 운영 환경에 가까운 선택이 필요했다.

## Decision
Spring Boot 3.5.13을 채택한다.

## Consequences
+ 업계 채택률이 높아 자료/이슈 트래킹이 용이
+ Jakarta EE 9 호환으로 검증된 라이브러리 폭이 넓음
+ 3.5.12 이상으로 최신 보안 패치 (CVE-2026-22731, CVE-2026-22733) 반영
- 2026-06-30 OSS 지원 종료 → 향후 4.x 마이그레이션 필요 (별도 ADR 예정)

## Alternatives Considered
- Spring Boot 4.0.5: 출시 5개월로 검증 기간 부족, 활발한 패치 진행 중
- Spring Boot 3.4.x: 2025-12-31 EOL로 보안 패치 미수신
