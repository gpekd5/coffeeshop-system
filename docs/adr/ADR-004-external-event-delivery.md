# ADR-004: 외부 주문 이벤트 전송 전략

## 상태

제안됨

---

## 1. 배경

주문이 완료되면 주문 데이터를 외부 데이터 수집 플랫폼으로 전달해야 한다.

외부 시스템은 주문 통계, 분석 또는 리포트 생성을 위해 주문 완료 이벤트를 수집한다고 가정한다.

초기에는 외부 Mock API를 HTTP로 호출한다.

이후에는 외부 시스템의 응답 지연과 장애가 주문 API에 미치는 영향을 줄이고, Commit된 주문 이벤트의 유실 가능성을 낮추기 위해 Kafka 기반 비동기 구조로 고도화한다.

---

## 2. 요구사항

외부 이벤트 전송은 다음 조건을 고려해야 한다.

- 외부 시스템 장애가 주문 DB 트랜잭션을 Rollback시키면 안 된다.
- 외부 API가 느려도 DB 락이 오래 유지되면 안 된다.
- 주문 Commit 후 이벤트가 유실될 가능성을 줄여야 한다.
- 전송 실패 시 재시도할 수 있어야 한다.
- 동일 이벤트가 중복 전달될 수 있음을 고려해야 한다.
- Consumer는 동일 이벤트를 여러 번 처리하지 않아야 한다.
- 1차 동기 방식과 2차 비동기 방식의 차이를 측정할 수 있어야 한다.

---

## 3. 고려한 대안

| 방식 | 설명 | 장점 | 단점 | 이번 프로젝트 적합성 |
|---|---|---|---|---|
| 트랜잭션 내부 동기 HTTP | DB 트랜잭션 안에서 외부 API 호출 | 외부 호출 실패를 즉시 알 수 있다. 흐름이 단순해 보인다. | 외부 지연 동안 DB 락과 Connection을 점유한다. 외부 장애로 주문이 Rollback될 수 있다. | 부적합 |
| Commit 이후 동기 HTTP | DB Commit 후 외부 API 호출 | DB 락을 짧게 유지한다. 구현이 단순하다. 외부 장애와 DB 트랜잭션을 분리한다. | 외부 응답시간이 사용자 응답에 포함된다. Commit 후 서버 종료 시 이벤트가 유실될 수 있다. | 1차에 적합 |
| `@Async` 비동기 호출 | 애플리케이션 Thread Pool에서 비동기 실행 | 구현이 비교적 단순하고 사용자 응답을 빠르게 할 수 있다. | 서버 종료 시 작업이 유실될 수 있다. 재시도와 상태 추적이 어렵다. | 낮음 |
| Application Event | Spring Event로 내부 이벤트 발행 | 도메인 로직과 후속 처리를 분리하기 쉽다. | 기본적으로 프로세스 내부 이벤트다. 서버 장애 시 유실 가능하며 외부 전송 보장이 어렵다. | 보조 수단 |
| DB 이벤트 테이블 + Scheduler | 이벤트를 DB에 저장하고 주기적으로 전송 | 이벤트 유실을 줄일 수 있다. Kafka 없이 구현 가능하다. | Polling과 상태 관리가 필요하다. 처리량 확장에 한계가 있다. | 보통 |
| Transactional Outbox + Kafka | 주문과 Outbox를 함께 저장하고 Kafka로 비동기 전송 | 이벤트 유실 가능성을 줄인다. 재시도와 Consumer 확장이 가능하다. 주문 응답과 외부 처리를 분리한다. | 구현과 운영 복잡도가 증가한다. 중복 발행과 Consumer 멱등성을 고려해야 한다. | 2차에 적합 |
| Kafka 직접 발행 후 DB 저장 | Kafka 발행과 주문 저장을 따로 수행 | 이벤트를 빠르게 발행할 수 있다. | Kafka 성공 후 DB 실패 또는 DB 성공 후 Kafka 실패의 Dual Write 문제가 발생한다. | 부적합 |

---

## 4. 결정

외부 이벤트 전송은 두 단계로 구현한다.

### 1차 구현

```text
주문 DB Transaction
→ Commit
→ 외부 Mock API 동기 호출
→ 주문 결과 반환
```

1차에서는 동기 HTTP 방식의 지연, 실패 및 이벤트 유실 가능성을 직접 확인한다.

### 2차 고도화

```text
[주문 트랜잭션]

주문 저장
→ 결제 저장
→ 포인트 차감
→ Outbox Event 저장
→ Commit

        ↓

[별도 처리]

Outbox Publisher
→ Kafka
→ Order Event Consumer
→ External Data Platform
```

2차에서는 Transactional Outbox Pattern과 Kafka를 적용한다.

---

## 5. 1차에서 동기 HTTP를 사용하는 이유

### 5.1 기본 문제를 먼저 관찰한다

처음부터 Kafka를 적용하면 동기 호출이 어떤 문제를 발생시키는지 직접 확인하기 어렵다.

Mock API를 통해 다음 상황을 재현한다.

```text
정상 응답
응답 지연
HTTP 500
Timeout
```

### 5.2 구현 난이도가 낮다

외부 API 연동의 기본 흐름과 요청 Payload를 먼저 확인할 수 있다.

Kafka, Outbox, Consumer를 한 번에 구현하지 않고 단계적으로 확장한다.

### 5.3 성능 비교 기준을 만든다

1차 구조의 평균 응답시간, P95 및 처리량을 측정하여 2차 Kafka 구조의 개선 효과와 비교한다.

---

## 6. 1차 동기 방식의 한계

### 6.1 사용자 응답 지연

외부 Mock API가 3초 지연되면 주문 DB 처리가 완료되었더라도 사용자 응답도 약 3초 이상 지연된다.

### 6.2 외부 장애 영향

외부 API Timeout이나 500 응답을 처리해야 한다.

주문 DB는 이미 Commit되었으므로 외부 실패를 주문 실패로 처리해서는 안 된다.

### 6.3 이벤트 유실

다음 구간에서 서버가 종료되면 이벤트가 전송되지 않을 수 있다.

```text
주문 Commit 완료
→ 애플리케이션 종료
→ 외부 API 호출 미실행
```

### 6.4 안정적인 재처리 부족

실패 로그만 남기는 방식으로는 누락된 이벤트를 안정적으로 찾아 재전송하기 어렵다.

---

## 7. Outbox + Kafka를 선택한 이유

### 7.1 Dual Write 문제 방지

주문 DB 저장과 Kafka 발행을 각각 실행하면 다음 상황이 발생할 수 있다.

```text
주문 DB Commit 성공
Kafka 발행 실패
```

또는:

```text
Kafka 발행 성공
주문 DB Rollback
```

Outbox Event를 주문과 같은 DB 트랜잭션으로 저장하면 주문 Commit 여부와 이벤트 기록 여부를 일치시킬 수 있다.

### 7.2 외부 시스템과 주문 응답 분리

주문 API는 DB 처리 완료 후 결과를 반환한다.

외부 시스템의 응답 지연과 장애는 Kafka Consumer 단계에서 처리하므로 주문 API에 직접 영향을 주지 않는다.

### 7.3 실패 재처리

Outbox 상태를 기준으로 Kafka 발행 실패 이벤트를 재조회할 수 있다.

```text
PENDING
→ 발행 성공: PUBLISHED
→ 발행 실패: PENDING 또는 FAILED
```

### 7.4 Consumer 확장

주문 완료 이벤트를 여러 Consumer가 독립적으로 활용할 수 있다.

```text
주문 분석 Consumer
매출 집계 Consumer
외부 데이터 전송 Consumer
```

초기 구현에서는 외부 데이터 전송 Consumer만 구현한다.

---

## 8. Outbox Event 설계

Outbox Event에는 다음 정보를 저장한다.

```text
eventId
aggregateType
aggregateId
eventType
payload
status
retryCount
createdAt
publishedAt
```

예시:

```text
eventId: UUID
aggregateType: ORDER
aggregateId: orderId
eventType: ORDER_COMPLETED
status: PENDING
```

주문과 Outbox Event는 같은 트랜잭션으로 저장한다.

---

## 9. 발행 보장 수준

본 구조는 **Exactly Once**를 완전히 보장하는 구조로 정의하지 않는다.

Outbox Publisher가 Kafka 발행에는 성공했지만 Outbox 상태 변경 전에 종료될 수 있다.

```text
Kafka 발행 성공
→ 서버 종료
→ Outbox 상태 PENDING 유지
→ 재시작 후 같은 이벤트 재발행
```

따라서 동일 Event ID가 Kafka에 여러 번 발행될 수 있다.

본 프로젝트는 다음 방식으로 처리한다.

```text
At-Least-Once 전송
+ Consumer 멱등성
```

---

## 10. Consumer 멱등성

Consumer는 `eventId`를 기준으로 이미 처리한 이벤트인지 확인한다.

```text
이벤트 수신
→ eventId 처리 여부 확인
→ 미처리: 외부 API 호출 후 처리 완료 저장
→ 처리 완료: 호출 생략
```

중복 이벤트가 전달되더라도 외부 시스템에 같은 주문이 여러 번 반영되지 않도록 한다.

초기 구현 방법 후보:

| 방식 | 장점 | 단점 |
|---|---|---|
| 처리 이벤트 DB 테이블 | 영속적이고 여러 Consumer 인스턴스가 공유할 수 있다. | 테이블과 저장 로직이 추가된다. |
| Redis Set | 빠르고 구현이 비교적 간단하다. | TTL과 Redis 장애를 고려해야 한다. |
| 외부 API 자체 멱등키 | 외부 시스템에서 최종 중복을 차단할 수 있다. | 외부 시스템이 멱등성을 지원해야 한다. |

초기 2차 구현에서는 `processed_kafka_events` 테이블에 처리 Event ID를 저장하고,
외부 Mock API에는 동일한 `eventId`를 전달해 멱등성을 검증한다.

`COMPLETED` 상태의 Event ID가 다시 수신되면 외부 API 호출을 생략하고,
`FAILED` 상태의 Event ID는 Kafka 재시도 또는 재수신 시 다시 처리한다.

---

## 11. 재시도와 Dead Letter Topic

Consumer 외부 API 호출 실패 시 제한된 횟수만 재시도한다.

```text
Consumer 처리 실패
→ 재시도
→ 최대 횟수 초과
→ Dead Letter Topic 이동
```

Dead Letter Topic 이벤트에는 다음 정보가 유지되어야 한다.

- 원본 Event ID
- 원본 Payload
- 원본 Topic
- 실패 원인
- 재시도 횟수
- 최종 실패 시각

구현에서는 Dead Letter Topic으로 이동한 이벤트를 `dead_letter_order_events`에 저장한다.

무한 재시도는 특정 실패 이벤트가 Consumer 처리를 계속 막을 수 있으므로 사용하지 않는다.

---

## 12. 예상 단점과 위험

### 12.1 구현 복잡도 증가

다음 구성요소가 추가된다.

```text
Outbox Entity
Outbox Repository
Outbox Publisher
Kafka Producer
Kafka Consumer
Consumer 멱등성 저장소
재시도
Dead Letter Topic
```

### 12.2 이벤트 처리 지연

비동기 구조이므로 주문 완료와 외부 시스템 반영 시점 사이에 지연이 발생할 수 있다.

외부 데이터 수집은 즉시 강한 정합성이 필요한 기능이 아니므로 지연을 허용한다.

### 12.3 중복 발행 가능성

At-Least-Once 방식에서는 중복 이벤트가 전달될 수 있다.

Consumer 멱등성을 반드시 구현해야 한다.

### 12.4 운영 대상 증가

Kafka Broker, Topic, Consumer Lag 및 Outbox 적체 상태를 모니터링해야 한다.

---

## 13. 검증 계획

### 1차 정상 호출

예상 결과:

- 주문 Commit 성공
- 외부 Mock API 한 번 호출
- 주문 결과 정상 반환

### 1차 응답 지연

Mock API 지연:

```text
100ms
500ms
1초
3초
```

측정 항목:

- 주문 API 평균 응답시간
- P95
- 처리량
- 외부 지연과 주문 응답시간 관계

### 1차 HTTP 500 및 Timeout

예상 결과:

- 주문 상태 `COMPLETED`
- 포인트 차감 유지
- 결제 데이터 유지
- 실패 로그 기록
- DB Rollback 없음

### Commit 후 서버 종료

예상 결과:

- 주문은 DB에 존재한다.
- 외부 이벤트가 유실될 수 있다.
- 1차 동기 구조의 한계를 확인한다.

### Outbox 원자성

2차에서 검증한다.

예상 결과:

- 주문 성공 시 Outbox Event가 존재한다.
- Outbox 저장 실패 시 주문도 Rollback된다.

### Kafka 발행 실패

예상 결과:

- Outbox Event가 삭제되지 않는다.
- 상태가 `PENDING` 또는 `FAILED`로 유지된다.
- 재실행 시 다시 발행된다.

### Consumer 중복 수신

같은 `eventId`를 두 번 전달한다.

예상 결과:

- 외부 API는 한 번만 처리된다.
- 중복 이벤트는 처리 완료로 판단하여 생략한다.

### 동기·비동기 성능 비교

비교 지표:

- 평균 응답시간
- P95
- 주문 처리량
- 실패 요청 수
- 외부 API 장애 영향
- 이벤트 유실 수
- 중복 처리 수
- Consumer Lag
- 이벤트 최종 처리시간

---

## 14. 최종 결정 요약

| 항목 | 결정 |
|---|---|
| 1차 전송 방식 | Commit 이후 동기 HTTP |
| 1차 외부 대상 | Mock API |
| 외부 실패 시 주문 | 유지 |
| 1차 핵심 한계 | 응답 지연, 이벤트 유실 가능성 |
| 2차 전송 방식 | Transactional Outbox + Kafka |
| 주문과 이벤트 기록 | 동일 DB 트랜잭션 |
| 전송 보장 | At-Least-Once |
| 중복 처리 방지 | Event ID 기반 Consumer 멱등성 |
| 발행 실패 | Outbox 재시도 |
| Consumer 최종 실패 | Dead Letter Topic |
| 비교 대상 | 동기 HTTP와 Kafka 비동기 처리 |
