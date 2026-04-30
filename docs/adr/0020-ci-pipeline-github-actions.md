# ADR-0020: CI 파이프라인 — GitHub Actions + OIDC + paths-filter 모노레포 패턴

## Status
Accepted (2026-04-30)

## Context

Phase 3 Epic 5에서 CI 파이프라인 도입. 기존 흐름은 수동:
```
1. 코드 변경 (IntelliJ)
2. ./gradlew clean bootJar (수동)
3. docker build (수동)
4. docker tag + ECR push (수동, ~10분)
5. K8s manifest 갱신 (수동)
```

이걸 **`git push` 트리거의 자동 파이프라인**으로 전환 필요.

## Decision

### 1) CI 도구: GitHub Actions
- ADR-0017에서 GitHub Actions OIDC 결정 — 그 결정의 첫 실전 적용
- AWS Code* 시리즈(CodeBuild, CodePipeline) 대신 채택
- 이유: GitHub과 자연스러운 통합, 무료 ubuntu-latest runner, OIDC로 정적 자격증명 0개

### 2) 워크플로우 분리
**두 워크플로우로 분리** (하나로 합치지 않음):

| 워크플로우 | 트리거 | 역할 |
|---|---|---|
| `pr-checks.yml` | PR open/sync | lint + test (빠른 피드백) |
| `build-and-push.yml` | main push + workflow_dispatch | 빌드 + Trivy + ECR push |

이유:
- PR 단계의 빠른 피드백 (외부 자원 호출 0)
- main merge 시점에만 ECR 비용/시간 발생
- 운영 표준 패턴 (검증 단계 분리)

### 3) 모노레포 패턴: 변경된 서비스만 빌드
- `dorny/paths-filter@v3`로 변경된 서비스 자동 감지
- matrix로 변경된 서비스만 병렬 빌드
- 변경 없는 서비스는 빌드/push 스킵 → 시간/비용/대역폭 절약

코드 패턴:
```yaml
detect-changes:
  outputs:
    services: ${{ steps.filter.outputs.changes }}
  steps:
    - uses: dorny/paths-filter@v3
      id: filter
      with:
        filters: |
          product-service:
            - 'product-service/**'
          order-service:
            - 'order-service/**'
          payment-service:
            - 'payment-service/**'

build:
  if: needs.detect-changes.outputs.services != '[]'
  strategy:
    matrix:
      service: ${{ fromJson(needs.detect-changes.outputs.services) }}
```

### 4) 인증: OIDC (정적 자격증명 0개)
- ADR-0017의 IAM Role (`github-actions-ecr`) 사용
- `aws-actions/configure-aws-credentials@v4`로 OIDC JWT → STS 임시 자격증명 발급
- AWS access key를 GitHub Secret에 저장하지 않음

### 5) 이미지 태깅: SHA + latest
- `docker/metadata-action@v5`로 자동 생성
- `<commit-sha-short>` (예: `a1b2c3d`) — 정확한 추적성
- `latest` — 사람이 인식하기 쉬움
- ADR-0016 IMMUTABLE 정책과 정합 (SHA는 항상 unique)

### 6) Docker 빌드: BuildKit + 레이어 캐싱
- `docker/build-push-action@v6`
- `cache-from: type=gha,scope=<service>` — GitHub Actions 캐시
- `mode=max` — 모든 레이어 캐시 (mode=min은 최종만)
- 서비스마다 scope 분리 (cross-contamination 방지)

### 7) BuildKit Attestation 자동 첨부
- `provenance: true` — SLSA Provenance Level 2
- `sbom: true` — SPDX 형식 SBOM
- ECR에 attestation으로 자동 push
- ADR-0010의 [Pending] BuildKit attestation 항목 자연 해소

### 8) Trivy 스캔 통합
- `aquasecurity/trivy-action@master`
- `severity: 'CRITICAL'` + `exit-code: '1'` — CRITICAL 1개라도 실패
- `ignore-unfixed: true` — patch 없는 CVE는 스킵
- baseline은 `docs/security/trivy-baseline.md` (ADR-0010)

### 9) 수동 트리거: workflow_dispatch
- 코드 변경 없이 빌드 가능
- 시나리오:
    - 베이스 이미지 CVE 패치로 전체 재빌드
    - ECR 이미지 사고 시 복구
    - 운영 비상 상황의 강제 재배포
- `type: choice`로 UI 드롭다운 (실수 방지)
- detect-changes job이 트리거 종류에 따라 paths-filter 또는 input 사용

### 10) 캐싱 전략 (다층)
- **Gradle 캐싱**: `gradle/actions/setup-gradle@v4` (자동, ~/.gradle/caches)
- **Docker 레이어 캐싱**: `cache-from/cache-to: type=gha`
- **PR vs main 분리**: `cache-read-only: ${{ github.event_name == 'pull_request' }}`
    - PR 빌드는 캐시 읽기만 (main 캐시 오염 방지)
    - main 빌드만 캐시 쓰기

## Consequences

### 긍정
+ 코드 push → 5~10분 안에 ECR에 새 이미지 (수동 ~10분 + 인지 부하 0)
+ 모노레포 효율성 — 변경된 서비스만 빌드
+ OIDC로 정적 자격증명 0개 (보안 강화)
+ BuildKit attestation 자동 — supply chain 표준 준수
+ Trivy 통합 — CRITICAL 자동 차단
+ workflow_dispatch — 비상 빌드 흐름
+ ADR-0017 (OIDC) + ADR-0010 (Trivy) + ADR-0016 (IMMUTABLE) + ADR-0009 (Multi-stage) 모두 한 곳에서 정합

### 부정
- GitHub Actions 무료 한도 (퍼블릭 레포는 무제한, 우리는 OK)
- 첫 빌드는 캐시 없어 5~10분 (이후 1~3분)
- dorny/paths-filter@v3가 Node 20 기반 — 2026-09-16 이후 작동 중단
  → 메인테이너 v4 release 또는 대체재 (`tj-actions/changed-files`) 검토 예정

### 면접 답변용 포인트
"Phase 3 Epic 5에서 GitHub Actions 기반 CI 파이프라인을 도입했습니다. 핵심 결정 다섯 가지:

1. **OIDC 인증** (ADR-0017 첫 실전): IAM access key를 GitHub Secret에 저장하지 않고
   OIDC JWT로 STS 임시 자격증명 받아 정적 자격증명 0개를 달성했습니다.

2. **모노레포 changed-only 빌드**: dorny/paths-filter로 변경된 서비스만 matrix 병렬 빌드.
   product-service만 수정한 PR은 product만 빌드/push, order/payment는 그대로.
   빌드 시간/ECR 비용/대역폭 절약.

3. **다층 캐싱**: Gradle 캐싱 + Docker BuildKit GHA 캐싱 + PR/main 분리(읽기/쓰기).

4. **BuildKit attestation 자동**: provenance + sbom 옵션만 켜서 SLSA + SBOM이 ECR에
   자동 첨부. 별도 도구 없이 supply chain 표준 준수.

5. **workflow_dispatch**: 베이스 이미지 CVE 패치 등 코드 변경 없는 비상 빌드 시나리오 대비."

## Alternatives Considered

### CI 도구: AWS CodeBuild + CodePipeline
- AWS 네이티브 통합
- 그러나 GitHub과의 자연스러운 통합 부족
- OIDC 도입 후 GitHub Actions의 격차 메워짐
- 거절

### CI 도구: Jenkins
- 가장 유연
- 그러나 self-hosted runner 운영 부담
- 1인 포트폴리오엔 과한 도입
- 거절

### 워크플로우 통합 (하나에 PR + push 둘 다)
- 코드 중복 줄임
- 그러나 트리거 분기 로직이 복잡
- "분리해서 명확히"가 운영 표준
- 거절

### 변경 감지 없이 매번 3개 빌드
- 코드 단순
- 그러나 모노레포에서 명백한 낭비
- 거절

### tj-actions/changed-files (paths-filter 대체)
- 더 강력한 옵션 (regex 등)
- 더 복잡한 설정
- 우리 케이스엔 paths-filter로 충분
- Phase 5+에서 dorny가 Node 24 release 안 하면 마이그레이션 검토

## References
- ADR-0017: GitHub Actions OIDC (본 ADR이 첫 실전 적용)
- ADR-0010: Trivy 스캔 baseline (본 ADR에서 워크플로우 통합)
- ADR-0016: ECR IMMUTABLE 정책 (SHA 태그 정합)
- ADR-0009: Multi-stage Docker 이미지 (본 ADR이 캐싱 효율 극대화)
- 함정 #34: 모노레포 패턴 A vs B (워크플로우 작성 시)
- 함정 #35: gradlew 실행 권한 (Windows ↔ Linux)
- 함정 #36: setup-gradle v3 → v4 옵션 변경
- 함정 #37: GitHub Secrets vs Variables 혼동
- 함정 #38: paths-filter의 의도된 비대칭 빌드