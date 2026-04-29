# portfolio-app

이커머스 마이크로서비스 3개 — Spring Boot 3.5.13 + Java 21.

> 전체 프로젝트 컨텍스트는 [portfolio-overview](https://github.com/melan-devops1/portfolio-overview)
> 참조. K8s 배포는 [portfolio-manifests](https://github.com/melan-devops1/portfolio-manifests),
> 인프라는 [portfolio-infra](https://github.com/melan-devops1/portfolio-infra).

## 서비스 구성

| 서비스 | 포트 | DB | 호출 |
|---|---|---|---|
| product-service | 8081 | productdb | (다운스트림 없음) |
| order-service | 8082 | orderdb | product → payment |
| payment-service | 8083 | paymentdb | (다운스트림 없음, Chaos 시뮬레이션 포함) |

호출 흐름: `Client → order → product (재고 확인) → payment (결제)`.

**payment-service는 의도적 Chaos** — 5% 랜덤 에러 + 100~2000ms 랜덤 지연.
운영 시연용(Alertmanager 알림, P99 latency 시연)이며 환경변수로 제어 (ADR-0007).

## 구조

```
portfolio-app/
├── product-service/              Spring Boot 모듈
│   ├── src/main/java/com/portfolio/product/
│   │   ├── controller/           ProductController
│   │   ├── service/              ProductService
│   │   ├── repository/           ProductRepository (JPA)
│   │   ├── domain/               Product
│   │   ├── dto/                  ProductRequest, ProductResponse
│   │   ├── exception/            GlobalExceptionHandler (ProblemDetail)
│   │   └── common/               RequestIdFilter (분산 추적)
│   ├── src/main/resources/
│   │   ├── application.yaml      profile별 분리 (local/prod)
│   │   └── logback-spring.xml    local=text / prod,k8s=JSON
│   ├── build.gradle
│   ├── Dockerfile                Multi-stage + Layered + Alpine JRE
│   └── .dockerignore
│
├── order-service/                동일 구조
├── payment-service/              동일 구조 + ChaosFilter
│
├── docker-compose.yaml           로컬 통합 검증 (PostgreSQL + 3개 서비스)
├── scripts/init-multiple-dbs.sh  PostgreSQL multi-DB 초기화
│
└── docs/
    ├── adr/                      ADR-0001~0010 (앱 단계 결정)
    └── security/
        └── trivy-baseline.md     보안 스캔 baseline 박제
```

## 사전 요구사항

- Java 21 (Eclipse Temurin)
- Docker 27+ (WSL2 통합 권장 in Windows)
- Gradle wrapper 사용 → 별도 설치 불필요

## 로컬 실행

### 단일 서비스 (H2 in-memory, 빠른 개발)

```bash
cd product-service
./gradlew bootRun
# http://localhost:8081/actuator/health → UP
```

`local` 프로파일이 default. H2 in-memory + 텍스트 로그.

### 통합 (PostgreSQL + 3개 서비스 + 분산 호출)

```bash
# 1) 모든 서비스 jar 빌드
./gradlew :product-service:bootJar :order-service:bootJar :payment-service:bootJar

# 2) Docker 이미지 빌드
for svc in product order payment; do
  docker build -t portfolio/${svc}-service:0.1.0 ${svc}-service
done

# 3) 통합 환경 시작
docker compose up -d

# 4) 검증 — 분산 호출 (order → product → payment)
curl -X POST http://localhost:8082/api/orders \
  -H "Content-Type: application/json" \
  -d '{"productId": 1, "quantity": 2}'

# 5) 정리
docker compose down -v
```

## 주요 엔드포인트

각 서비스 공통:

```
GET  /actuator/health              전체 상태
GET  /actuator/health/liveness     K8s liveness probe
GET  /actuator/health/readiness    K8s readiness probe
GET  /actuator/info                git commit hash, build time (ADR-0002)
GET  /actuator/prometheus          메트릭 (Phase 4 Prometheus scrape용)
```

비즈니스 API:

```
# product-service (8081)
GET    /api/products
POST   /api/products
GET    /api/products/{id}
DELETE /api/products/{id}

# order-service (8082)
POST   /api/orders                 product, payment 호출
GET    /api/orders/{id}

# payment-service (8083)
POST   /api/payments
GET    /api/payments/{id}
```

## ECR 푸시 (수동, Phase 5에서 GitHub Actions로 자동화 예정)

```bash
export AWS_ACCOUNT_ID=601766312629
export AWS_REGION=ap-northeast-2
export ECR_REGISTRY=${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com

# 로그인
aws ecr get-login-password --region $AWS_REGION \
  | docker login --username AWS --password-stdin $ECR_REGISTRY

# 빌드 + 태그 + 푸시
for svc in product order payment; do
  cd ${svc}-service
  ./gradlew clean bootJar
  docker build -t portfolio/${svc}-service:0.1.0 .
  docker tag portfolio/${svc}-service:0.1.0 \
    $ECR_REGISTRY/portfolio/${svc}-service:0.1.0
  docker push $ECR_REGISTRY/portfolio/${svc}-service:0.1.0
  cd ..
done
```

## Docker 빌드 패턴

호스트에서 Gradle 빌드 → Dockerfile은 jar 복사 + Layered 추출만 담당 (ADR-0008).
**Docker 안에서 Gradle 빌드 금지** — `.git` 누락으로 git-properties 실패.

이미지 사이즈: ~130MB (순진한 빌드 266MB 대비 51% 감소, ADR-0009).

## 관찰성 기능

| 기능 | 구현 |
|---|---|
| 구조화 로그 (JSON) | logstash-encoder 8.0, profile별 logback-spring.xml |
| 분산 추적 | MDC + X-Request-Id 헤더 + RestClient 인터셉터 (ADR-0005) |
| 메트릭 | Spring Boot Actuator + Micrometer + Prometheus 포맷 |
| 헬스체크 | Liveness / Readiness 분리 |
| 빌드 정보 | gradle-git-properties → /actuator/info |
| 보안 스캔 | Trivy + ECR scan_on_push (CRITICAL 차단, HIGH baseline) |

상세는 `./docs/adr/` 참조.

## 함정

작업하면서 만난 실제 이슈들.

**WSL2 ↔ Windows localhost**
`.wslconfig`에 `networkingMode=mirrored + localhostForwarding=true` 설정 필요.
미설정 시 IntelliJ에서 띄운 서비스를 PowerShell `curl`로 호출 못 함.

**PowerShell `curl`은 진짜 curl 아님**
`Invoke-WebRequest` alias라 동작 다름. WSL의 curl 사용 또는 PowerShell에서 `curl.exe` 명시.

**Git 초기화 위치**
portfolio-app 루트(`C:\Users\melan\projects\portfolio-app\`)에 `git init` 완료.
하위 product-service 등에서 빌드 시 자동으로 상위 `.git` 인식 → gradle-git-properties 정상.

**plain jar 비활성화 권장**
`build.gradle`에 `jar { enabled = false }` 추가. 안 하면 `build/libs/`에 plain jar +
boot jar 둘 다 생겨 Docker 이미지에 두 jar 들어감 → 사이즈 부풀어짐.

**Spring 프로파일 환경변수 우선순위**
`${VAR:default}` 패턴은 활성 프로파일에 명시된 값이 없을 때만 적용.
prod 프로파일에서도 환경변수 받으려면 prod 섹션에도 동일 패턴 박아야 함.

**Chaos 시뮬레이션은 의도된 동작**
payment-service의 5% 에러 + 100~2000ms 지연은 운영 시연 장치 (ADR-0007).
환경변수 `CHAOS_ENABLED=false`로 끌 수 있음. **버그가 아니다.**

전체 함정 리스트는 portfolio-overview의 PROJECT_CONTEXT.md `함정/주의사항` 섹션 참조.

## 설계 결정 (ADR)

| | 결정 |
|---|---|
| [0001](./docs/adr/0001-microservice-structure.md) | 마이크로서비스 분리 구조 (product/order/payment 단방향) |
| [0002](./docs/adr/0002-gradle-git-properties.md) | gradle-git-properties로 commit hash 자동 노출 |
| [0003](./docs/adr/0003-structured-logging.md) | logstash-encoder 8.0 (Spring Boot 내장 대신) |
| [0004](./docs/adr/0004-problem-detail.md) | RFC 7807 ProblemDetail 표준 에러 응답 |
| [0005](./docs/adr/0005-distributed-tracing.md) | MDC + X-Request-Id 분산 추적 기반 |
| [0006](./docs/adr/0006-dependency-version-unification.md) | 전 서비스 의존성 버전 통일 |
| [0007](./docs/adr/0007-chaos-simulation.md) | payment-service 의도적 Chaos 시뮬레이션 |
| [0008](./docs/adr/0008-docker-build-strategy.md) | 호스트 빌드 → Docker 패키징 분리 |
| [0009](./docs/adr/0009-docker-image-optimization.md) | Multi-stage + Layered + Alpine JRE (130MB) |
| [0010](./docs/adr/0010-security-scan-baseline.md) | Trivy CRITICAL 차단 / HIGH 평가 후 baseline 등록 |

## 버전 박제

`portfolio-overview`의 PROJECT_CONTEXT.md가 단일 진실 소스. 변경 시 그 문서를 먼저 갱신.

핵심 버전 (변경 금지, 필요 시 ADR 추가):

```
Java                     21 LTS (Eclipse Temurin)
Spring Boot              3.5.13
Gradle plugins           org.springframework.boot 3.5.13
                         io.spring.dependency-management 1.1.7
                         com.gorylenko.gradle-git-properties 2.4.2
logstash-encoder         8.0  (9.0은 Jackson 3.0 요구로 호환 불가)
HTTP client              RestClient (RestTemplate 금지)
DB (local)               H2 in-memory (PostgreSQL mode)
DB (prod)                PostgreSQL 15
```