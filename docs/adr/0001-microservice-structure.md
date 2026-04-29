# ADR-0001: 마이크로서비스 분리 구조 (product / order / payment)

## Status
Accepted (2026-04-25)

## Context

DevOps 포트폴리오 프로젝트의 도메인 시나리오로 이커머스 마이크로서비스를 채택했다.
"앱 코드는 동작하는 최소한의 품질"이 목표지만, **K8s/Service Mesh/분산 추적/Observability를
시연하려면 서비스 간 호출이 일어나는 구조가 필요**하다. 단일 모놀리식 앱으로는 분산 시스템의
문제(타임아웃, retry, circuit breaker, mTLS, 분산 추적 등)를 보여줄 수 없다.

서비스 분리 단위 결정 항목:
1. 몇 개로 나눌까 — 너무 적으면 시연 가치 부족, 너무 많으면 운영 부담
2. 어떤 도메인으로 나눌까 — 면접관이 직관적으로 이해할 수 있어야 함
3. 호출 관계는 — DAG가 트리 구조로 깔끔해야 분산 추적 시연이 명확

## Decision

**3개 서비스 분리 + 단방향 호출 체인**:

| 서비스 | 포트 | DB | 역할 | 호출 |
|---|---|---|---|---|
| product-service | 8081 | productdb | 상품 CRUD | (다운스트림 없음) |
| order-service | 8082 | orderdb | 주문 생성 | product → payment |
| payment-service | 8083 | paymentdb | 결제 모의 | (다운스트림 없음) |

호출 흐름: `Client → order-service → product-service` (재고 확인) → `payment-service` (결제 처리).

### DB 분리 원칙
- 서비스마다 별도 DB (`productdb`, `orderdb`, `paymentdb`)
- "마이크로서비스 = DB 공유 금지" 원칙 시연

### 호출 패턴
- 동기 HTTP (RestClient) — 분산 추적 시연이 단순
- 메시지 큐(Kafka 등) 미도입 — 본 프로젝트의 인프라/Observability 본질에서 벗어남

## Consequences

### 긍정
+ 분산 추적, mTLS, Service Mesh 시연을 위한 최소 충분 구조
+ Trace는 트리(client → order → {product, payment})로 면접관이 한눈에 이해 가능
+ DB 분리로 "서비스 독립 배포" 원칙 강조 가능
+ payment-service에 의도적 chaos(ADR-0007) 주입해 Alertmanager 알림 시연 가능

### 부정
- 서비스 3개 운영 부담 (Pod 수 증가 → t3.large × 2 노드로 빠듯할 수 있음)
- 도메인 로직이 빈약해 "왜 이 분리?"라는 면접 질문에 대한 진짜 답은 "운영 시연 목적"
    - 솔직하게 답변하는 게 정답: "도메인 깊이보다 운영 가능성 시연이 본 프로젝트의 목표"

### 면접 답변용 포인트
"마이크로서비스 분리는 도메인 복잡도가 그것을 정당화할 때 의미 있습니다. 본 프로젝트는
운영 인프라 시연이 목적이라 도메인을 의도적으로 단순하게 유지하면서, 서비스 간 호출 체인이
존재하는 최소 구조(3개 + 단방향)로 구성했습니다."

## Alternatives Considered

### 단일 모놀리식 앱
- 단순함 / 빠른 구축
- 분산 추적, mTLS, Service Mesh 시연 불가
- 거절

### 5개 이상 서비스 분리
- (예: user, product, cart, order, payment, notification)
- 시연 가치는 비슷하지만 t3.large × 2 노드에선 리소스 부담
- Phase 4~5에서 Observability 학습 우선이라 서비스 수보단 깊이 우선
- 거절

### 비동기 메시지 큐 (Kafka 등)
- "이벤트 기반 마이크로서비스" 패턴 시연 가능
- 그러나 인프라 부담 큼 (Kafka 클러스터 자체 운영)
- Phase 5+에서 추가 검토 가능
- 본 단계에선 거절