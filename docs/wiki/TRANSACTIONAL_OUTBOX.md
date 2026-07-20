# Transactional Outbox 설계

## 1. 문제 배경

주문이 완료되면 주문 데이터와 함께 외부 전송 대상 이벤트도 반드시 남아야 한다.

하지만 주문 저장과 Kafka 발행을 각각 따로 실행하면 Dual Write 문제가 생긴다.

```text
주문 DB Commit 성공
→ Kafka 발행 실패
```

또는 반대로 다음 상황도 가능하다.

```text
Kafka 발행 성공
→ 주문 DB Rollback
```

두 저장소를 하나의 트랜잭션으로 묶을 수 없으므로, 주문 DB와 Kafka 사이에 안정적인 중간 저장소가 필요하다.

---

## 2. 단순 구현의 한계

주문 트랜잭션 안에서 Kafka에 직접 발행하면 다음 문제가 있다.

| 방식 | 문제 |
| --- | --- |
| DB 저장 후 Kafka 직접 발행 | DB Commit 전 이벤트가 외부로 나갈 수 있음 |
| Kafka 발행 후 DB 저장 | DB 실패 시 존재하지 않는 주문 이벤트가 발행됨 |
| Commit 이후 Kafka 발행 | 서버 종료 시 이벤트 유실 가능 |

Kafka 발행 실패가 완료된 주문 상태를 바꾸면 안 되지만, 이벤트가 아예 사라져도 안 된다.

---

## 3. 선택한 전략

주문 트랜잭션 안에서 `outbox_events` 테이블에 주문 완료 이벤트를 함께 저장한다.

```text
[주문 트랜잭션]
주문 저장
→ 결제 저장
→ 포인트 차감
→ Outbox Event 저장
→ Commit

[별도 처리]
Outbox Publisher
→ Kafka 발행
→ Outbox 상태 변경
```

Outbox 저장이 실패하면 주문도 Rollback된다.
Kafka 발행 실패는 주문 상태를 변경하지 않고 Outbox 상태와 재시도 정보만 갱신한다.

---

## 4. 구현 흐름

```text
OrderTransactionService
→ 주문/결제/포인트 DB 작업
→ OutboxEventService.saveOrderCompletedEvent()
→ outbox_events PENDING 저장
→ Commit

OutboxPublisherScheduler
→ OutboxPublisherService.publishAvailableEvents()
→ 발행 가능 이벤트 비관적 쓰기 잠금 조회
→ 짧은 lease 예약
→ Kafka 발행
→ 성공: PUBLISHED
→ 실패: FAILED, retry_count, next_retry_at, last_error 갱신
```

Outbox Publisher는 `PENDING` 이벤트와 재시도 시간이 지난 `FAILED` 이벤트를 발행 대상으로 본다.

---

## 5. 핵심 코드 위치

| 코드 | 역할 |
| --- | --- |
| `src/main/java/com/example/coffeeorder/order/service/OrderTransactionService.java` | 주문 트랜잭션 안에서 Outbox 저장 호출 |
| `src/main/java/com/example/coffeeorder/event/outbox/service/OutboxEventService.java` | Outbox 저장, 예약, 성공/실패 상태 변경 |
| `src/main/java/com/example/coffeeorder/event/outbox/entity/OutboxEvent.java` | Outbox Event 상태와 재시도 정보 |
| `src/main/java/com/example/coffeeorder/event/outbox/repository/OutboxEventRepository.java` | 발행 가능 이벤트 비관적 쓰기 잠금 조회 |
| `src/main/java/com/example/coffeeorder/event/kafka/publisher/OutboxPublisherScheduler.java` | 주기적 발행 트리거 |
| `src/main/java/com/example/coffeeorder/event/kafka/publisher/OutboxPublisherService.java` | Kafka 발행과 Outbox 상태 갱신 |
| `src/main/java/com/example/coffeeorder/event/kafka/producer/SpringKafkaOrderCompletedEventProducer.java` | Kafka Producer 구현 |

---

## 6. 테스트로 검증한 내용

| 테스트 | 검증 내용 |
| --- | --- |
| `OrderOutboxIntegrationTest` | 주문 성공 시 Outbox Event가 함께 저장됨 |
| `OrderOutboxRollbackIntegrationTest` | Outbox 저장 실패 시 주문도 Rollback됨 |
| `OutboxEventServiceIntegrationTest` | 발행 대상 조회, retry, 상태 전이 |
| `OutboxPublisherServiceIntegrationTest` | Kafka 발행 성공/실패에 따른 Outbox 상태 변경 |
| `AdminOutboxEventControllerIntegrationTest` | 관리자 Outbox 조회와 수동 재처리 API |

---

## 실제 적용 코드

### 적용 파일 경로

| 파일 | 역할 |
| --- | --- |
| `src/main/java/com/example/coffeeorder/order/service/OrderTransactionService.java` | 주문 트랜잭션 안에서 Outbox 저장 호출 |
| `src/main/java/com/example/coffeeorder/event/outbox/service/OutboxEventService.java` | Outbox 저장, 발행 예약, 성공/실패 상태 변경 |
| `src/main/java/com/example/coffeeorder/event/outbox/entity/OutboxEvent.java` | Outbox 상태와 재시도 정보 |
| `src/main/java/com/example/coffeeorder/event/outbox/repository/OutboxEventRepository.java` | 발행 대상 이벤트 비관적 쓰기 잠금 조회 |
| `src/main/java/com/example/coffeeorder/event/kafka/publisher/OutboxPublisherService.java` | 예약한 Outbox 이벤트를 Kafka로 발행 |

### 호출 흐름

```text
주문 트랜잭션
→ OrderTransactionService.createOrder()
→ OutboxEventService.saveOrderCompletedEvent()
→ outbox_events PENDING 저장
→ Commit

비동기 발행
→ OutboxPublisherService.publishAvailableEvents()
→ OutboxEventService.reservePublishableEventsForPublish()
→ Kafka 발행
→ 성공: PUBLISHED
→ 실패: FAILED + nextRetryAt
```

### 핵심 코드만 짧게 발췌

```java
@Transactional(propagation = Propagation.MANDATORY)
public OutboxEvent saveOrderCompletedEvent(Long memberId, OrderCreateResponse order, LocalDateTime occurredAt) {
    OutboxEvent event = OutboxEvent.orderCompleted(eventId, order.orderId(), serialize(payload));
    return outboxEventRepository.saveAndFlush(event);
}
```

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("""
        select event
        from OutboxEvent event
        where (...)
        order by event.createdAt asc, event.id asc
        """)
List<OutboxEvent> findPublishableEventsForUpdate(...);
```

```java
try {
    orderCompletedEventProducer.send(event);
    outboxEventService.markPublished(event.getId());
} catch (RuntimeException exception) {
    outboxEventService.markPublishFailed(event.getId(), exception.getMessage());
}
```

### 이 코드가 해당 개념을 구현하는 이유

`saveOrderCompletedEvent()`가 `Propagation.MANDATORY`로 선언되어 있어 반드시 주문 트랜잭션 안에서만 Outbox 이벤트를 저장한다. 주문 Commit과 Outbox 저장이 함께 성공해야 하므로 DB Commit 이후 Kafka 발행 전 서버가 종료되어도 이벤트가 DB에 남는다. Publisher는 발행 가능한 이벤트를 비관적 락으로 예약하고, Kafka 발행 결과에 따라 상태를 갱신해 재시도 가능한 At-Least-Once 흐름을 만든다.

---

## 7. 한계와 개선 방향

현재 구조는 At-Least-Once 전송을 전제로 한다.

Outbox Publisher가 Kafka 발행에는 성공했지만 `PUBLISHED` 상태 변경 전에 종료되면 같은 이벤트가 다시 발행될 수 있다.

```text
Kafka 발행 성공
→ 서버 종료
→ Outbox 상태 PENDING 유지
→ 재시작 후 재발행
```

따라서 Consumer 멱등성이 반드시 필요하다.

후속 개선 후보는 다음과 같다.

| 개선 | 설명 |
| --- | --- |
| Outbox 보관 정책 | 오래된 `PUBLISHED` 이벤트 정리 |
| Publisher 병렬 처리 | Partition 또는 샤딩 기준으로 발행 처리량 확장 |
| Kafka 발행 정책 고도화 | Producer ack, retry, timeout 운영값 세분화 |
| 장애 알림 | 오래된 PENDING, FAILED 증가 시 알림 |
