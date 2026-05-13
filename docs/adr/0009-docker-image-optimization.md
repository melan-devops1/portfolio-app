# ADR-0009: Docker 이미지 최적화 — Multi-stage + Layered JAR + Alpine JRE

## Status
Accepted (2026-04-28) · Re-measured (2026-05-14, OTel agent 도입 반영)

## Context

ADR-0008에서 호스트 빌드 → Docker 패키징 분리를 결정했지만, **Docker 이미지 자체의 품질**은
별개 결정이 필요하다. 운영 컨테이너 이미지의 3가지 핵심 지표:

1. **사이즈**: pull 시간 + 디스크 사용량 + ECR 저장 비용
2. **빌드 캐시 효율**: 코드 한 줄 변경 시 전체 이미지 재빌드 vs 변경된 레이어만 재빌드
3. **공격 표면(보안)**: 컨테이너 안에 깔린 패키지 수 = 잠재적 CVE 수

순진한 Dockerfile:
```dockerfile
FROM eclipse-temurin:21
COPY build/libs/app.jar /app/app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]
```
문제:
- 사이즈: ~450MB (full JDK + Ubuntu 베이스)
- 캐시: 코드 한 줄만 바뀌어도 jar 전체(~80MB)가 통째로 새 레이어
- 보안: 사용 안 하는 패키지 다수

## Decision

**4가지 최적화 기법 조합**:

### 1) Multi-stage 빌드
- Stage 1 (`extractor`): JDK alpine에서 Spring Boot Layered JAR `extract` 실행 → 최종 이미지엔 미포함
- Stage 2 (`runtime`): JRE alpine + 추출된 레이어 4개 + OTel agent

### 2) Layered JAR (Spring Boot)
- Spring Boot 2.3+의 layertools가 jar를 4개 layer로 분해 (변경 빈도 낮은 순):
    - `dependencies/` ← 외부 의존성 (변경 적음, 캐시 강하게 활용)
    - `spring-boot-loader/` ← 런처 (거의 변경 없음)
    - `snapshot-dependencies/` ← 스냅샷 의존성 (변경 빈번)
    - `application/` ← 우리 코드 (변경 가장 잦음)
- Docker 레이어가 위에서 아래로 캐시되므로, 코드만 바뀌면 `application/`만 재빌드

### 3) Alpine JRE 베이스
- `eclipse-temurin:21-jre-alpine` (~210MB extracted / ~80MB compressed) vs `eclipse-temurin:21` (~450MB)
- JRE only (JDK 도구 불필요 — 운영 컨테이너에서 javac 안 씀)
- Alpine Linux (musl libc + busybox) — Ubuntu 대비 패키지 수 1/10

### 4) OTel agent + `ADD --chmod` 한 줄 (Phase 4 Epic 9 추가)
- `ADD --chmod=644 https://.../otel.jar /opt/otel/...` 단일 명령
- 이전엔 `ADD` 다음 별도 `RUN chmod` → 같은 jar가 두 레이어에 중복 저장 (디스크 +24MB / 압축 +20MB)
- `ADD --chmod` 옵션으로 단일 레이어 → 중복 제거

### 5) 보안 추가
- **non-root 사용자**: `USER spring:spring` (uid 1001)
- ADR-0010의 Trivy 스캔 baseline + ECR scan_on_push 다층 방어

## 사이즈 측정 방식 (재현 가능 보장)

| 측정 | 의미 | 명령 |
|---|---|---|
| **압축 (실질 지표)** | ECR 저장 / pull 시 네트워크 전송 — Pod 부팅 속도와 ECR 비용에 직접 영향 | `docker save <image> \| gzip \| wc -c` |
| 압축 해제 (디스크) | 노드 디스크 점유량 | `docker history --format '{{.Size}}'` 합산 |
| `docker images` 표시치 | Docker 29.x containerd image store는 BuildKit manifest/attestation 메타 포함해서 표시 — **참고용** | `docker images` |

**ADR의 인용 사이즈는 압축(`docker save \| gzip`) 기준**. 이게 운영적으로 가장 의미 있는 숫자.

## Consequences

### 측정 결과 (2026-05-14, 3개 서비스 모두 빌드)

| 시점 | 압축 (ECR pull) | 디스크 점유 | docker images 표시 |
|---|---|---|---|
| naive (`eclipse-temurin:21` baseline + 단일 jar) | ~266 MB (2026-04 측정) | ~450 MB | n/a |
| 최적화 v1 (Multi-stage + Layered + Alpine JRE, Phase 1) | **~130 MB** (2026-04-29) | ~210 MB | n/a |
| Phase 4 Epic 9 후 (OTel agent + 중복 RUN chmod 레이어) | 162.5 MB | 323.7 MB | 496 MB |
| **현재 (`ADD --chmod=644` 최적화 반영, Phase 4 마감)** | **142.5 MB** | **299.3 MB** | 450 MB |

- naive 대비 ECR 압축 기준 **266 → 142.5 MB ≈ 46% 감소** (현재, OTel agent 포함)
- naive 대비 ECR 압축 기준 **266 → 130 MB ≈ 51% 감소** (Phase 1 OTel 미포함)
- 레이어별 breakdown: 각 서비스 `Dockerfile` 하단의 표 참조

### 긍정
+ **이미지 사이즈** (압축 기준): naive 266 MB → 현재 142.5 MB (~46% 감소, OTel 포함)
+ **빌드 캐시**: 코드만 변경 시 application/ 레이어(수백 KB)만 재빌드
+ **공격 표면 축소**: Alpine + JRE only → CVE 노출 면 감소 (Trivy 스캔 baseline 통과 용이)
+ ECR pull 시간 단축 → Pod 부팅 시간 단축 (K8s 스케일아웃 빠름)
+ ECR 저장 비용 절감 (10개 이미지 × 142.5MB = ~1.4GB)
+ `ADD --chmod` 도입으로 OTel jar 중복 레이어 제거 (-20 MB 압축 / -24 MB 디스크)

### 부정
- Alpine의 musl libc → glibc 기반 라이브러리(예: 일부 native binding)와 호환 이슈 가능
    - 본 프로젝트의 Spring Boot + Hibernate + H2/PostgreSQL 조합에선 문제 없음
- Multi-stage Dockerfile이 길어져 가독성 저하
    - 주석 + 레이어별 사이즈 표로 보강

### 면접 답변용 포인트 (수치 박제)
"기본 빌드(`eclipse-temurin:21` 베이스 + 단일 jar, naive) 대비 Multi-stage + Layered JAR +
Alpine JRE 조합으로 **ECR pull 사이즈(`docker save | gzip` 기준)를 약 266 MB → 142.5 MB로 ~46% 줄였습니다**.
이 숫자는 Pod 부팅 속도와 ECR 저장 비용에 직접 영향을 주는 실질 지표라 압축 기준으로 측정했습니다.
Phase 4에서 OpenTelemetry Java Agent를 동봉할 때 `ADD` 다음 별도 `RUN chmod`가 같은 jar를
두 레이어에 중복 저장하던 비효율을 발견해 **`ADD --chmod=644` 한 줄로 합쳐 약 20MB 더 줄였습니다**.
이전 ADR에 박은 130MB는 OTel 추가 전 측정값으로, 현재는 OTel 포함이라 142.5 MB가 정답입니다."

## Alternatives Considered

### Distroless 베이스 (`gcr.io/distroless/java21`)
- Alpine보다 더 작고 안전 (shell도 없어 공격 표면 최소)
- 그러나 디버깅 어려움 (`kubectl exec`로 shell 접근 불가)
- Phase 6+ 운영 환경 진입 시 검토
- 거절 (현 단계, 디버깅 우선)

### JLink로 커스텀 JRE 만들기
- 우리 앱이 쓰는 모듈만 골라 더 작은 JRE 생성 (~50MB까지 가능)
- 그러나 빌드 복잡도 큼
- Alpine JRE로도 충분한 사이즈 달성
- 거절

### 단일 stage Dockerfile
- 더 단순함
- Layered JAR 분해 못함 → 캐시 효율 낮음
- 거절

### Ubuntu/Debian 베이스 + JRE
- 호환성 가장 좋음
- 그러나 사이즈 + 보안 측면에서 Alpine에 밀림
- 거절

## References
- Spring Boot Layered JAR 공식 문서
- Eclipse Temurin Alpine 이미지
- ADR-0008: 호스트 빌드 → Docker 패키징 분리 (본 ADR과 정합)
- ADR-0010: Trivy 스캔 baseline (본 ADR의 Alpine 채택이 baseline 통과에 기여)

## 변경 이력
- 2026-04-28: 초안 — Multi-stage + Layered + Alpine JRE 결정
- 2026-04-29: 측정 — naive 266MB / 최적화 130MB (Phase 1, OTel 미포함)
- 2026-05-14: 재측정 — OTel agent 동봉 후 142.5MB (`ADD --chmod=644` 최적화 반영).
              측정 방식 명시 (`docker save | gzip` = 압축 = ECR pull 기준).
