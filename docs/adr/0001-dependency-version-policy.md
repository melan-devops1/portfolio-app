# ADR-0002: 의존성 버전 통일 정책

## Status
Accepted (2026-04-27)

## Context
마이크로서비스가 여러 개 추가되면서 각 서비스의 Spring Boot/플러그인 버전이
서로 어긋나는 상황이 발생했다. (product=3.5.0, order=3.5.13)

## Decision
- 모든 서비스는 동일한 Spring Boot patch 버전을 사용한다.
- 현재 기준: **Spring Boot 3.5.13** (3.5.12에서 CVE-2026-22731, 22733 패치)
- 의존성 업그레이드 시 모든 서비스를 동시에 업데이트한다.

## Future Improvement
서비스가 5개 이상으로 늘어나면 Gradle Version Catalog(`gradle/libs.versions.toml`)
도입하여 단일 소스 관리.

## Rationale
- 라이브러리 호환성 문제 예방
- 보안 패치 일관 적용
- 운영 환경에서 디버깅 시 버전 차이로 인한 혼란 제거
