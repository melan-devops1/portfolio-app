# Container Image Security Baseline

## Scan Metadata
- **Date**: 2026-04-28
- **Tool**: Trivy v0.70 (mirror.gcr.io/aquasec/trivy-db:2)
- **Scope**: HIGH, CRITICAL severity
- **Scanners**: vuln, secret

## Images Scanned
| Image | Base | Status |
|---|---|---|
| portfolio/product-service:0.1.0 | eclipse-temurin:21-jre-alpine (Alpine 3.23.4) | ✅ |
| portfolio/order-service:0.1.0 | eclipse-temurin:21-jre-alpine (Alpine 3.23.4) | ✅ |
| portfolio/payment-service:0.1.0 | eclipse-temurin:21-jre-alpine (Alpine 3.23.4) | ✅ |

## Findings Summary

| Severity | Count |
|---|---|
| CRITICAL | **0** |
| HIGH | 2 (per image, same set) |
| Secrets | 0 |
| OS package vulns | 0 |

## HIGH Vulnerabilities — Detail & Mitigation

### CVE-2026-34483 / CVE-2026-34487 — Apache Tomcat
- **Package**: `org.apache.tomcat.embed:tomcat-embed-core` 10.1.53
- **Fixed in**: 10.1.54 (and 9.0.116, 11.0.21)
- **Category**: Information Disclosure
- **Vector**: JsonAccessLogValve 사용 시 / 로그 파일 내 민감 데이터 노출
- **Impact assessment**:
    - 본 프로젝트는 JsonAccessLogValve 미사용 (logback-spring.xml만 사용)
    - 운영 로그는 JSON 구조화로 logstash-encoder 사용, Tomcat AccessLog 비활성
    - 실제 노출 위험 **낮음**
- **Decision**: **Phase 1 baseline으로 수용**.
  Spring Boot 3.5.x patch release 시 Tomcat 자동 업그레이드 예정.
  필요 시 `ext['tomcat.version'] = '10.1.54'`로 즉시 override 가능.

## Why This Baseline Matters
- 향후 모든 PR/릴리즈에서 본 baseline과 비교
- 새로운 CRITICAL 발생 시 머지 차단 (Phase 3 GitHub Actions에서 enforce 예정)
- HIGH 추가 발생 시 영향도 평가 후 의사결정

## Next Steps (Future Phases)
- [ ] Phase 3: GitHub Actions에서 PR마다 Trivy 자동 스캔 + CRITICAL 차단
- [ ] Phase 3: 본 baseline 파일을 워크플로우에서 참조하여 회귀 검증
- [ ] Phase 4 이후: Renovate/Dependabot으로 의존성 자동 업데이트
- [ ] 운영 단계: Trivy Operator로 K8s 런타임 스캔