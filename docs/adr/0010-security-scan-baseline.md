# ADR-0010: 보안 스캔 baseline 정책 (Trivy CRITICAL 차단 / HIGH 영향도 평가)

## Status
Accepted (2026-04-28)

## Context

운영 환경에 컨테이너 이미지를 배포하기 전 **보안 취약점 스캔**이 필수다. 정책 결정 항목:

1. **어느 도구로 스캔할까** — Trivy, Grype, Snyk 등
2. **어느 단계에서 스캔할까** — 빌드 시점 / push 시점 / 런타임
3. **어떤 심각도부터 차단할까** — CRITICAL / HIGH / MEDIUM
4. **무시할 수 있는 CVE는 어떻게 박제할까** — 모든 CVE를 즉시 fix할 수는 없음

순진한 접근 ("HIGH 이상 모두 차단")의 함정:
- Java 생태계의 CVE는 자주 발견되고, 패치까지 시간 걸림
- 특정 CVE는 우리 사용 패턴에서 영향 없음 (예: 사용 안 하는 모듈)
- 무조건 차단하면 빌드가 자주 막혀 개발 속도 저하

## Decision

**다층 방어 + 심각도별 차등 정책**.

### 1) 도구: Trivy
- **빌드 시점**: 로컬에서 `trivy image` 실행 (ADR-0009의 이미지 빌드 직후)
  - 버전: Trivy v0.70 (DB: mirror.gcr.io/aquasec/trivy-db:2)
  - 스캐너: vuln, secret
  - 심각도 필터: HIGH, CRITICAL
- **push 시점**: ECR scan_on_push (ADR-0016에서 결정)
- 두 도구가 서로 다른 CVE DB를 보고 결과가 다를 수 있음 → **다층 방어**

### 2) 심각도별 정책
| 심각도 | 정책 |
|---|---|
| **CRITICAL** | **빌드 차단** — 무조건 fix 후 push |
| **HIGH** | **영향도 평가 후 결정** — baseline에 등록하면 통과 |
| **MEDIUM/LOW** | 경고만, 차단 없음 |

### 3) Baseline 관리
**Baseline 문서 위치**: `docs/security/trivy-baseline.md`

**Phase 1 측정 결과 (2026-04-28)**:
- 대상 이미지: 3개 서비스 모두 (`portfolio/{product,order,payment}-service:0.1.0`)
- 베이스: `eclipse-temurin:21-jre-alpine` (Alpine 3.23.4)
- 결과: **CRITICAL 0 / HIGH 2 (이미지마다 동일 세트) / Secrets 0 / OS package vulns 0**

**Baseline 등록된 HIGH (2건, 동일 원인)**:
- **CVE-2026-34483, CVE-2026-34487** (Apache Tomcat)
  - 영향 패키지: `org.apache.tomcat.embed:tomcat-embed-core` 10.1.53
  - Fixed in: 10.1.54 (또는 9.0.116, 11.0.21)
  - 분류: Information Disclosure
  - Vector: JsonAccessLogValve 사용 시 로그에 민감 데이터 노출

**영향도 평가 (등록 근거)**:
- 본 프로젝트는 JsonAccessLogValve 미사용 (logback-spring.xml만 사용)
- 운영 로그는 logstash-encoder JSON 구조화 (ADR-0003), Tomcat AccessLog 비활성
- 실제 노출 경로 없음 → **위험 낮음**
- Spring Boot 3.5.x patch release 시 Tomcat 자동 업그레이드로 해소 예정
- 즉시 fix 필요 시 `build.gradle`에서 `ext['tomcat.version'] = '10.1.54'`로 override 가능

**현재 형태**: `.trivyignore` 자동 무시 파일은 미사용. baseline은 `docs/security/trivy-baseline.md`
에 사람 가독 형태로 박제. Phase 3 CI 도입 시 워크플로우가 이 문서를 참조하여 회귀 검증.

### 4) Baseline 등록 기준
HIGH CVE를 baseline에 등록하려면:
1. **사용 패턴 분석**: 우리 코드가 해당 취약 모듈을 실제로 호출하는가?
2. **Exploit 가능성**: 외부 공격자가 도달 가능한 경로인가?
3. **패치 ETA**: 상위 라이브러리에서 patch 예정 시점
4. **만료 일자**: baseline 등록 시 검토 만료일 명시 (보통 90일)

**적용 사례 — Tomcat CVE 2건**:
- (1) 사용 패턴: JsonAccessLogValve 미사용 → 영향 없음
- (2) Exploit: 외부 → 로그 노출 경로 없음 (logstash-encoder만 사용)
- (3) 패치 ETA: Spring Boot 3.5.x 다음 patch에서 자동 해소
- (4) 만료: Spring Boot patch 도입 시 자동 해소 (별도 만료일 미설정)
→ baseline 수용 결정

### 5) Phase 3 Epic 5 (CI 파이프라인) 통합 계획
(작성 시점: Phase 3.4.2 완료, Epic 5 미시작)

GitHub Actions 워크플로우에서 Trivy를 빌드 단계에 포함:
```yaml
- name: Trivy scan
  uses: aquasecurity/trivy-action@master
  with:
    image-ref: ${{ env.IMAGE }}
    exit-code: '1'
    severity: 'CRITICAL'    ← CRITICAL만 빌드 실패
    ignore-unfixed: true
```

**계획**:
- PR마다 Trivy 자동 스캔
- CRITICAL 발생 시 머지 차단
- HIGH 신규 발생 시 → `docs/security/trivy-baseline.md`와 비교 → baseline 외 신규 CVE면 머지 차단
- Trivy DB 캐시 (actions/cache) 활용으로 함정 #14의 다운로드 시간 단축

## Consequences

### 긍정
+ CRITICAL 차단으로 명백한 보안 위험 자동 차단
+ HIGH baseline 정책으로 "fix 불가능한 CVE에 의한 빌드 영구 차단" 회피
+ 다층 방어 (Trivy + ECR scan_on_push) → 서로 다른 DB 커버
+ baseline 만료일 정책으로 "한 번 무시하면 영원히 무시" 사고 방지

### 부정
- baseline 관리 부담: 만료일 추적 + 정기 재평가 필요
- HIGH baseline은 100% 운영 판단 → 실수 시 진짜 CVE 무시 가능
  - mitigation: baseline 등록은 PR review 필수
- Trivy DB 다운로드 시간 (vulndb 91MB + javadb 859MB) — 함정 #14에 박제됨
  - mitigation: CI에서 actions/cache 활용 (Phase 3에서 적용)

### 한마디로 하면
"보안 스캔은 Trivy 빌드 시점 스캔 + ECR scan_on_push의 다층 방어로 구성했습니다.
CRITICAL은 무조건 빌드 차단, HIGH는 우리 코드 영향도를 평가해 baseline 등록 가능하지만
만료일을 명시해 정기 재평가하도록 했습니다. 이는 '모든 CVE 즉시 차단'이 현실적으로
불가능한 Java 생태계 특성을 고려한 균형 정책입니다."

### Docker BuildKit attestation (Phase 3 Epic 5에서 정식 통합)

CI 파이프라인의 docker/build-push-action@v6에서 `provenance: true`, `sbom: true` 설정으로
SLSA Provenance Level 2 + SPDX SBOM이 ECR에 자동 첨부됨. 별도 도구나 워크플로우 단계
없이 supply chain 보안 표준을 준수하는 효과.

상세는 ADR-0020 (CI 파이프라인) 7번 섹션 참조.

### 면접 답변용 포인트 (BuildKit attestation)
"GitHub Actions의 docker/build-push-action에 provenance와 sbom 옵션을 켜서 BuildKit이
자동으로 SLSA Provenance Level 2와 SPDX 형식 SBOM을 생성해 ECR에 attestation으로
첨부하도록 했습니다. 별도 도구나 워크플로우 단계 없이 supply chain 표준을 준수했고,
향후 Sigstore/Cosign 도입 시 이 attestation을 자동 검증 게이트로 활용할 수 있습니다."

## Alternatives Considered

### Snyk
- 더 많은 CVE 커버리지 + UI 풍부
- 유료 + Phase 6+의 운영 환경 진입 시 검토
- 본 프로젝트는 Trivy의 무료 + 충분한 커버리지로 결정
- 거절

### Grype
- Trivy와 비슷한 OSS 스캐너
- 커뮤니티 채택률은 Trivy가 더 높음
- 본 프로젝트는 표준성 우선
- 거절

### "HIGH 이상 모두 차단" (엄격 정책)
- 가장 안전
- 그러나 Java 생태계에서 빌드 자주 막힘 → 개발 속도 저하
- 1인 포트폴리오 프로젝트에 과한 부담
- 거절

### 스캔 안 함
- 빌드 빠름
- 운영 컨테이너의 보안 취약점 무방비 노출
- DevOps 포트폴리오 가치 0
- 거절

## References
- Trivy 공식 문서
- ADR-0009: Alpine JRE 채택 (베이스 자체의 CVE 노출 면 축소로 본 ADR baseline 통과 용이)
- ADR-0016: ECR scan_on_push (본 ADR과 다층 방어 구성)
- PROJECT_CONTEXT.md 함정 #14: Trivy DB 다운로드 시간
- 본 ADR의 운영 문서: `docs/security/trivy-baseline.md`
- ADR-0020: CI 파이프라인 (BuildKit attestation 정식 통합)