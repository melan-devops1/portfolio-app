# ADR-0009: Docker 이미지 최적화 — Multi-stage + Layered JAR + Alpine JRE

## Status
Accepted (2026-04-28)

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

**3가지 최적화 기법 조합**:

### 1) Multi-stage 빌드
```dockerfile
# Stage 1: Layered JAR 분해
FROM eclipse-temurin:21-jre-alpine AS layered
WORKDIR /app
COPY build/libs/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract --destination .

# Stage 2: 최종 이미지
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=layered /app/dependencies/ ./
COPY --from=layered /app/spring-boot-loader/ ./
COPY --from=layered /app/snapshot-dependencies/ ./
COPY --from=layered /app/application/ ./
USER spring:spring
ENTRYPOINT ["java","org.springframework.boot.loader.launch.JarLauncher"]
```

### 2) Layered JAR (Spring Boot)
- Spring Boot 2.3+의 layertools가 jar를 4개 layer로 분해:
    - `dependencies/` ← 외부 의존성 (변경 적음, 캐시 강하게 활용)
    - `spring-boot-loader/` ← 런처 (거의 변경 없음)
    - `snapshot-dependencies/` ← 스냅샷 의존성 (변경 빈번)
    - `application/` ← 우리 코드 (변경 가장 잦음)
- Docker 레이어가 위에서 아래로 캐시되므로, 코드만 바뀌면 `application/`만 새로 빌드

### 3) Alpine JRE 베이스
- `eclipse-temurin:21-jre-alpine` (~150MB) vs `eclipse-temurin:21` (~450MB)
- JRE only (JDK 도구 불필요 — 운영 컨테이너에서 javac 안 씀)
- Alpine Linux (musl libc + busybox) — Ubuntu 대비 패키지 수 1/10

### 4) 보안 추가 (PROJECT_CONTEXT.md 면접 어필 포인트)
- **non-root 사용자**: `USER spring:spring` (uid 1001)
- (참고: ADR-0010의 Trivy 스캔 baseline과 다층 방어)

## Consequences

### 긍정
++ **이미지 사이즈 130MB** (순진한 빌드 266MB 대비 약 51% 감소)
+   - 측정 환경: product-service jar (Spring Boot 3.5.13)
+   - 비교 대상: 동일 jar를 `eclipse-temurin:21` 베이스로 빌드 (multi-stage/layered 미적용)
+   - 측정 일자: 2026-04-29
+ **빌드 캐시**: 코드만 변경 시 application/ 레이어(수 MB)만 재빌드
+ **공격 표면 축소**: Alpine + JRE only → CVE 노출 면 감소 (Trivy 스캔 baseline 통과 용이)
+ ECR pull 시간 단축 → Pod 부팅 시간 단축 (K8s 스케일아웃 빠름)
+ ECR 저장 비용 절감 (10개 이미지 × 150MB = 1.5GB)

### 부정
- Alpine의 musl libc → glibc 기반 라이브러리(예: 일부 native binding)와 호환 이슈 가능
    - 본 프로젝트의 Spring Boot + Hibernate + H2/PostgreSQL 조합에선 문제 없음
- Multi-stage Dockerfile이 길어져 가독성 저하
    - 주석으로 보강

### 면접 답변용 포인트 (수치 박제)
+"기본 빌드(`eclipse-temurin:21` 베이스 + 단일 jar) 대비 Multi-stage + Layered + Alpine JRE
+조합으로 이미지 사이즈를 266MB → 130MB로 **약 51% 줄였습니다**. Spring Boot의 layertools로
의존성과 애플리케이션 코드를 별도 레이어로 분리해 코드 변경 시 application 레이어만
재빌드되도록 했고, ECR pull 시간을 단축해 K8s 스케일아웃 응답성을 높였습니다."

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