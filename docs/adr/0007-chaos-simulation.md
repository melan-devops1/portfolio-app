# ADR-0007: 의도적 Chaos 시뮬레이션 (payment-service의 5% 에러 + 100~2000ms 지연)

## Status
Accepted (2026-04-28)

## Context

본 프로젝트는 **DevOps 포트폴리오로 Observability 시연이 핵심**이다 (PROJECT_CONTEXT.md
면접 어필 우선순위 #3). Phase 4에서 다음을 시연해야 한다:
- Prometheus Alertmanager의 **에러율 임계치 알림** (보통 5%)
- **P99 latency 알림** (보통 1~2초)
- Grafana 대시보드의 **에러율 트렌드**, **응답시간 분포**

문제: **정상 동작하는 앱은 에러도 지연도 거의 없음**. 인위적으로 만들어내야 한다.

선택지:
1. **앱 코드에 의도적 chaos 주입** — 즉시 시연 가능
2. **Chaos Mesh / Litmus** 같은 K8s chaos 도구 도입 — 별도 인프라 부담
3. **부하 테스트 도구로 외부에서 만들기** — 환경 의존적, 재현성 낮음

## Decision

**payment-service 내부에 의도적 chaos 주입**:
1. **5% 랜덤 에러 반환** (HTTP 500 + ProblemDetail)
2. **100~2000ms 랜덤 지연** (모든 정상 응답에 추가)

### 환경변수로 제어 가능
- `CHAOS_ERROR_RATE` (default: 0.05)
- `CHAOS_DELAY_MIN_MS` (default: 100)
- `CHAOS_DELAY_MAX_MS` (default: 2000)

운영 시연 시:
- 5% 에러 → Alertmanager 5% 임계치 알림 트리거
- P99 ~2000ms → P99 latency 알림 시연

### 명시적으로 박제: 이건 "버그가 아니라 운영 시연 장치"
PROJECT_CONTEXT.md 함정 #7:
> "Chaos 시뮬레이션은 의도된 동작. 절대 '버그'로 간주하고 고치자고 제안하지 말 것."

응답 작성 규칙으로도 박제됨.

## Consequences

### 긍정
+ Phase 4의 Alertmanager 알림 시연이 **즉시 작동** (외부 chaos 도구 불필요)
+ Grafana 대시보드의 에러율/latency 트렌드가 항상 의미 있는 값을 보여줌
+ 환경변수로 끄면(`CHAOS_ERROR_RATE=0`) 정상 모드 가능 → 시연 끝나고 제거 가능
+ 면접에서 "운영 시연을 위해 의도적 chaos를 어떻게 주입했나" 답변 가능
+ 면접 스토리: "월간 SLA 99.9% 기준으로 에러율 5% / P99 2초 임계치를 설정했다"
  (PROJECT_CONTEXT.md 면접 어필 답변 예시와 정합)

### 부정
- 앱 코드가 도메인 로직 외에 chaos 로직을 갖게 됨 → 도메인 순수성 저해
  - 본 프로젝트가 운영 시연 우선이므로 의도된 trade-off
- 외부 클라이언트(예: order-service)가 payment-service를 호출할 때 5% 실패 처리해야 함
  - retry/circuit breaker 시연으로 오히려 긍정 효과
- 본 chaos는 "특정 서비스에 박제" — Chaos Mesh 같은 도구는 더 다양한 시나리오 가능
  - Phase 5+에서 추가 도입 검토 (네트워크 분할, Pod kill 등)

### 면접 답변용 포인트
"Phase 4의 Alertmanager 알림과 Grafana 대시보드의 에러율 트렌드를 즉시 시연하기 위해
payment-service에 의도적 chaos를 주입했습니다. 5% 랜덤 에러와 100~2000ms 랜덤 지연으로
SLA 99.9% / 에러율 5% / P99 2초 임계치 알림이 자연스럽게 트리거됩니다.
환경변수로 끌 수 있도록 설계했고, ADR로 의도된 동작임을 박제했습니다."

## Consequences for downstream services
- order-service → payment-service 호출은 **5% 확률로 실패**
- order-service 측에서 **graceful 실패 처리** 필요 (현재는 그대로 ProblemDetail 전파)
- Phase 5에서 Istio retry / circuit breaker 도입 시 이 chaos가 시연 자료가 됨

## Alternatives Considered

### Chaos Mesh / Litmus 도입
- K8s 레벨에서 다양한 chaos 시나리오 (Pod kill, 네트워크 분할, 디스크 fill 등)
- 그러나 본 프로젝트의 1차 목표(에러율/latency 알림 시연)엔 과한 도입
- Phase 5+에서 도입 검토
- 거절 (현 단계)

### 외부 부하 테스트 도구로 chaos 주입
- (예: k6, Locust로 의도적 에러 패턴 생성)
- 시연 시마다 외부 도구 실행 필요 → 재현성 낮음
- 거절

### Chaos 미주입, 정상 트래픽만
- Alertmanager 알림이 트리거되는 일이 거의 없음
- "알림이 잘 작동한다"를 증명할 수 없음 → 시연 불가
- 거절
