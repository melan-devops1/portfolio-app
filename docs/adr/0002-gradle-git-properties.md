# ADR-0002: gradle-git-properties로 commit hash 자동 노출

## Status
Accepted (2026-04-27)

## Context

운영 환경에서 흔한 질문:
> "지금 운영 중인 서비스가 정확히 어떤 코드를 돌고 있는가?"

K8s에 배포된 컨테이너 이미지의 태그(`0.1.0` 등)를 봐도 그 태그가 어떤 git commit을 담고
있는지 추적이 어려울 수 있다. 특히:
- `latest` 태그 사용 시
- 동일 태그 재push 시 (IMMUTABLE 정책 도입 전)
- 빌드 시점과 배포 시점이 분리된 CI/CD 파이프라인에서

해결 방향:
- 빌드 시점에 git commit hash, branch, build time을 jar에 박아둔다
- Spring Boot Actuator의 `/actuator/info` 엔드포인트로 노출

## Decision

**`com.gorylenko.gradle-git-properties` 플러그인 채택 (버전 2.4.2)**.

```gradle
plugins {
    id 'com.gorylenko.gradle-git-properties' version '2.4.2'
}

gitProperties {
    failOnNoGitDirectory = false
    keys = ['git.branch', 'git.commit.id.abbrev', 'git.commit.time', 'git.tags']
}
```

빌드 시점에 자동으로 `META-INF/git.properties` 파일이 jar에 포함되고,
Spring Boot Actuator가 이를 자동 인식하여 `/actuator/info`로 노출.

### 노출 예시
GET /actuator/info
{
  "git": {
    "branch": "main",
    "commit": { "id": "a1b2c3d", "time": "2026-04-29T10:23:11Z" },
    "tags": "v0.1.0"
  }
}

## Consequences

### 긍정
+ 운영 중인 어떤 Pod이든 `/actuator/info`로 정확한 commit hash 확인 가능
+ "이 Pod이 정확히 어떤 코드인가?" 질문에 1초 답변
+ ADR-0016(ECR IMMUTABLE)과 함께 배포 추적성 2중 보강
+ Phase 4의 Grafana 대시보드에 build_info 메트릭으로 노출 가능 (계획)

### 부정
- 빌드 시 `.git` 디렉터리가 필요 (Docker 안에서 빌드하면 누락 위험)
  - 함정 #4, #8에서 다룸: 호스트 빌드 → Docker 패키징 패턴 (ADR-0008과 정합)
- `failOnNoGitDirectory = false`로 .git 없을 때도 빌드 통과하지만, 그 경우 git.properties가
  안 만들어져 운영에서 추적 불가
  - CI 파이프라인에서 .git 누락 검증 단계 권장 (Phase 3에서 추가)

### 면접 답변용 포인트
"운영 중인 컨테이너의 코드 식별을 위해 gradle-git-properties로 빌드 시점에 git 정보를 jar에
박아두고, Actuator info 엔드포인트로 노출했습니다. ECR의 IMMUTABLE 태그 정책과 함께
'운영 중인 Pod = 특정 commit'이라는 1:1 매핑을 보장합니다."

## Alternatives Considered

### 직접 빌드 시점 환경변수로 주입
- (예: `-Dgit.commit.id=$(git rev-parse HEAD)` 직접 전달)
- 빌드 스크립트가 복잡해짐
- 플러그인이 표준 패턴이라 거절

### Git 정보 없이 ECR 이미지 태그만 사용
- ECR 태그가 SemVer + Git SHA 형식이면 일부 추적 가능
- 그러나 이미지 외부에서 매핑을 찾아야 해서 운영 부담
- 거절

## References
- Spring Boot Actuator info endpoint 공식 문서