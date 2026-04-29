# ADR-0008: Docker 패키징 전략 — 호스트 빌드 → Docker 패키징 분리

## Status
Accepted (2026-04-28)

## Context

Spring Boot 앱을 Docker 이미지로 만드는 두 가지 접근:

### 접근 A: Docker 안에서 Gradle 실행 (full Docker build)
```dockerfile
FROM eclipse-temurin:21 AS build
COPY . /src
WORKDIR /src
RUN ./gradlew bootJar
FROM eclipse-temurin:21-jre-alpine
COPY --from=build /src/build/libs/*.jar /app/app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]
```

### 접근 B: 호스트에서 Gradle 실행 + Docker는 패키징만
```bash
# 호스트
./gradlew bootJar
docker build -t portfolio/product-service:0.1.0 .
```
```dockerfile
FROM eclipse-temurin:21-jre-alpine
COPY build/libs/*.jar /app/app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]
```

본 프로젝트에서 접근 A를 처음 시도했을 때 **3개의 사고**를 만남:
1. **`.git` 누락**: ADR-0002 gradle-git-properties가 빌드 컨텍스트의 `.git`을 요구하는데
   `.dockerignore`로 제외됨 → 빌드 실패. 포함하자니 Docker 컨텍스트가 거대해짐
2. **빌드 시간**: Docker 안에서 Gradle 캐시가 매번 비워짐 → 의존성 재다운로드로 빌드 시간 길어짐
3. **GitHub Actions 일관성**: 향후 Phase 5의 CI 파이프라인은 호스트에서 `./gradlew bootJar` 후
   Docker 이미지를 만드는 패턴이 표준 → 로컬 빌드도 같은 패턴이 일관성 있음

## Decision

**호스트 빌드 + Docker 패키징 분리**.

### 빌드 흐름
```
1. (호스트) ./gradlew bootJar              ← jar 생성
2. (호스트) docker build -t ... .          ← jar 복사 + Layered 추출 (ADR-0009)
3. (호스트) docker push ...                 ← ECR (Phase 2.4)
```

### Dockerfile 책임 한정
- jar 복사
- Layered JAR 분해 (ADR-0009)
- Alpine JRE 베이스 (ADR-0009)
- Spring Boot 실행
- **Gradle 호출 없음**

### 이 결정이 강제하는 것 (PROJECT_CONTEXT.md 함정 #8 박제)
> "호스트에서 ./gradlew bootJar로 jar 생성 → Dockerfile은 jar 복사 + Layered 추출만 담당.
> Docker 안에서 Gradle 빌드하지 말 것 (.git 누락으로 gradle-git-properties 실패 + 빌드 시간 길어짐).
> GitHub Actions 패턴과 일관."

## Consequences

### 긍정
+ ADR-0002 gradle-git-properties가 호스트의 .git을 자연스럽게 사용 → 사고 0
+ Gradle 캐시(~/.gradle/caches)가 호스트에 누적 → 빌드 시간 단축
+ GitHub Actions 워크플로우와 동일 패턴 → 로컬 빌드 = CI 빌드 (재현성 확보)
+ Dockerfile이 단순해져 리뷰/디버깅 용이

### 부정
- Docker만 깔린 환경에선 Java + Gradle도 필요 → "어디서나 docker build로 끝" 원칙 위배
  - 본 프로젝트는 개발자 머신에 Java/Gradle이 항상 있다고 가정 (포트폴리오 1인 프로젝트라 OK)
- jar 빌드 실패 시 Docker 빌드 안 시작 → 별도 흐름 관리
  - CI에선 `bootJar → docker build`로 단계 분리

### 면접 답변용 포인트
"Docker 빌드 시 호스트에서 jar를 만들고 Docker는 패키징만 담당하는 패턴을 채택했습니다.
gradle-git-properties가 `.git` 디렉터리를 요구하는데 `.dockerignore`로 제외되면 빌드가
실패하고, 포함하면 컨텍스트가 거대해지는 사고를 미리 방지하기 위함입니다. 또한 GitHub
Actions의 표준 CI 패턴과 일치시켜 로컬과 CI 빌드의 재현성을 확보했습니다."

## Alternatives Considered

### Docker 안에서 Gradle 실행 (full Docker build)
- 호스트에 Java/Gradle 불필요
- 그러나 `.git` 누락 사고 + Gradle 캐시 비효율
- 거절

### Jib (구글의 Docker-less 컨테이너 빌드 플러그인)
- Gradle/Maven 플러그인으로 Docker daemon 없이 이미지 생성
- 빌드 시간 빠름 (레이어 자동 분해)
- 그러나 Dockerfile 가시성 손실 → 베이스 이미지 보안 점검 어려움
- ADR-0009의 Multi-stage/Layered JAR 학습 의도와 맞지 않음
- 거절

### Buildpacks (Cloud Native Buildpacks)
- `pack build` 명령으로 Dockerfile 없이 이미지 생성
- 그러나 베이스 이미지 선택 자유도 낮음 + 학습 곡선
- 본 프로젝트는 Dockerfile 가시성 우선
- 거절

## References
- ADR-0002: gradle-git-properties (호스트 .git 의존)
- ADR-0009: Multi-stage + Layered + Alpine JRE 이미지 최적화
- PROJECT_CONTEXT.md 함정 #8: 호스트 빌드 → Docker 패키징 패턴 박제