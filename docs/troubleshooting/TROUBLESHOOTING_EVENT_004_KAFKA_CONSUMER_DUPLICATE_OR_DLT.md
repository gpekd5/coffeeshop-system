# Kafka Consumer 중복 처리와 Dead Letter

## 1. 증상

Kafka Consumer 처리 단계에서 다음 문제가 보이면 Consumer 멱등성 또는 DLT 흐름을 확인한다.

| 증상 | 예시 |
| --- | --- |
| 같은 주문 이벤트가 외부 API로 여러 번 전송됨 | 동일 `eventId` 중복 호출 |
| `processed_kafka_events`에 `FAILED`가 증가함 | 외부 API 실패 또는 Payload 처리 실패 |
| `PROCESSING` 상태가 오래 유지됨 | Consumer 처리 중 종료 또는 lease 대기 |
| Dead Letter 이벤트가 증가함 | Kafka 재시도 한도 초과 |

---

## 2. 영향

Outbox와 Kafka는 At-Least-Once 전송을 전제로 한다.
따라서 같은 이벤트가 다시 전달될 수 있고, Consumer는 `eventId` 기준으로 중복 처리를 막아야 한다.

중복 방지가 깨지면 외부 데이터 수집 플랫폼에 같은 주문이 여러 번 반영될 수 있다.

---

## 3. 먼저 확인할 것

| 확인 대상 | 정상 기준 |
| --- | --- |
| Consumer 활성화 | `APP_KAFKA_ORDER_EVENT_CONSUMER_ENABLED=true` |
| Consumer Group | `APP_KAFKA_ORDER_EVENT_CONSUMER_GROUP_ID`가 의도한 값 |
| 처리 이력 | `processed_kafka_events.event_id` 기준 상태 확인 |
| DLT Topic | `APP_KAFKA_ORDER_EVENT_DLT` 설정 확인 |
| 외부 URL | 운영에서는 local/mock URL 사용 금지 |

---

## 4. 원인별 확인 방법

### 같은 이벤트가 다시 들어온 경우

`eventId`가 이미 `COMPLETED`이면 Consumer는 외부 API 호출을 생략해야 한다.

```sql
select event_id,
       status,
       attempt_count,
       last_error,
       processing_deadline_at,
       processed_at
from processed_kafka_events
where event_id = ?;
```

`COMPLETED`인데 외부 API가 다시 호출된다면 `ProcessedKafkaEventService.beginProcessing()`의 중복 판단을 확인한다.

### `PROCESSING`이 오래 남은 경우

Consumer가 처리 중 종료되면 `PROCESSING` 상태가 남을 수 있다.
이때 `processing_deadline_at`이 지나면 재처리 대상이 된다.

```text
APP_KAFKA_ORDER_EVENT_CONSUMER_PROCESSING_LEASE_SECONDS
```

`processing_deadline_at`이 미래면 다른 Consumer가 처리 중인 것으로 보고 건너뛰는 것이 정상이다.

### Dead Letter가 증가하는 경우

Kafka 재시도 횟수를 넘으면 DLT로 이동하고 `dead_letter_order_events`에 저장된다.

```sql
select id,
       event_id,
       original_topic,
       dead_letter_topic,
       failure_reason,
       received_at
from dead_letter_order_events
order by received_at desc;
```

Payload 역직렬화 실패, 외부 API 지속 실패, 잘못된 Topic/헤더 값을 구분해서 본다.

---

## 5. 복구 또는 대응

| 상황 | 대응 |
| --- | --- |
| `COMPLETED` 중복 호출 | `eventId` 전달 경로와 Consumer 멱등성 로직 확인 |
| `FAILED` 반복 | 외부 API 응답 코드, Timeout, URL, Payload 확인 |
| `PROCESSING` 장기 유지 | lease 만료 후 재처리되는지 확인 |
| DLT 적재 | 관리자 API `GET /api/v1/admin/dead-letter-order-events`로 실패 원인 확인 |

DLT 이벤트는 현재 조회만 제공한다.
재처리 기능이 필요하면 별도 이슈로 설계해야 한다.

---

## 6. 관련 코드와 테스트

| 위치 | 설명 |
| --- | --- |
| [OrderCompletedEventConsumer.java](../../src/main/java/com/example/coffeeorder/event/kafka/consumer/OrderCompletedEventConsumer.java) | Kafka Topic과 DLT 수신 |
| [OrderCompletedEventProcessor.java](../../src/main/java/com/example/coffeeorder/event/kafka/consumer/OrderCompletedEventProcessor.java) | Consumer 처리, 외부 API 호출, 실패 전파 |
| [ProcessedKafkaEventService.java](../../src/main/java/com/example/coffeeorder/event/kafka/consumer/service/ProcessedKafkaEventService.java) | 중복 처리 판단과 lease 관리 |
| [AdminProcessedKafkaEventController.java](../../src/main/java/com/example/coffeeorder/event/kafka/consumer/controller/AdminProcessedKafkaEventController.java) | 처리 이력 조회 API |
| [AdminDeadLetterOrderEventController.java](../../src/main/java/com/example/coffeeorder/event/kafka/consumer/controller/AdminDeadLetterOrderEventController.java) | Dead Letter 조회 API |
| [OrderCompletedEventProcessorIntegrationTest.java](../../src/test/java/com/example/coffeeorder/event/kafka/consumer/OrderCompletedEventProcessorIntegrationTest.java) | 중복 스킵, 실패, lease 만료 재처리 |
| [AdminDeadLetterOrderEventControllerIntegrationTest.java](../../src/test/java/com/example/coffeeorder/event/kafka/consumer/controller/AdminDeadLetterOrderEventControllerIntegrationTest.java) | DLT 관리자 조회 |

관련 기술 문서: [Kafka Consumer 멱등성 전략](../wiki/KAFKA_CONSUMER_IDEMPOTENCY.md)
