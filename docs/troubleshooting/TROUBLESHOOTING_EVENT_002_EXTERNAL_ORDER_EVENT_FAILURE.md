# 외부 주문 이벤트 전송 실패

## 1. 증상

주문은 완료됐지만 외부 데이터 수집 플랫폼에 주문 완료 이벤트가 반영되지 않는 상황이다.

| 증상 | 예시 |
| --- | --- |
| 주문 API는 성공했지만 외부 플랫폼에는 데이터가 없음 | 주문 상태는 `COMPLETED` |
| 외부 이벤트 로그가 `FAILED` 또는 `TIMEOUT` | `external_order_event_logs.status` 확인 |
| Kafka Consumer 처리 이력이 `FAILED` | `processed_kafka_events.status` 확인 |
| Dead Letter 이벤트가 생김 | `dead_letter_order_events` 확인 |

---

## 2. 영향

외부 전송 실패는 완료 주문을 Rollback하지 않는다.

이 프로젝트의 우선순위는 주문, 결제, 포인트 정합성을 먼저 확정하고 외부 시스템 장애는 별도 이력으로 추적하는 것이다.
따라서 장애가 발생해도 사용자 주문은 성공 상태로 유지된다.

---

## 3. 먼저 확인할 것

| 확인 대상 | 정상 기준 |
| --- | --- |
| 1차 동기 전송 활성화 | 해당 경로를 사용할 때 `APP_EXTERNAL_ORDER_EVENT_ENABLED=true` |
| Kafka Consumer 활성화 | 비동기 경로를 사용할 때 `APP_KAFKA_ORDER_EVENT_CONSUMER_ENABLED=true` |
| 외부 URL | `APP_EXTERNAL_ORDER_EVENT_BASE_URL`, `APP_EXTERNAL_ORDER_EVENT_PATH`가 올바름 |
| Timeout | 외부 응답 시간이 `APP_EXTERNAL_ORDER_EVENT_RESPONSE_TIMEOUT_MILLIS` 이내 |
| 주문 상태 | `orders.status=COMPLETED` |
| 결제 상태 | `payments.status=COMPLETED` |

---

## 4. 원인별 확인 방법

### 1차 동기 전송 경로

`OrderEventDeliveryService`를 사용하는 경로에서는 전송 결과가 `external_order_event_logs`에 저장된다.

```sql
select event_id,
       order_id,
       status,
       response_status_code,
       error_code,
       error_message,
       sent_at
from external_order_event_logs
where order_id = ?;
```

`FAILED`이면 외부 API가 오류 응답을 반환했을 가능성이 높다.
`TIMEOUT`이면 외부 응답 지연 또는 Timeout 설정이 원인일 수 있다.

### Outbox/Kafka 비동기 전송 경로

Outbox를 사용하는 경로에서는 먼저 주문 트랜잭션 안에서 `outbox_events`가 저장된다.

```sql
select id,
       aggregate_id,
       status,
       retry_count,
       next_retry_at,
       last_error,
       published_at
from outbox_events
where aggregate_id = ?;
```

`PUBLISHED`인데 외부 플랫폼에 데이터가 없다면 Kafka Consumer 이후 단계를 확인한다.

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

---

## 5. 복구 또는 대응

| 상황 | 대응 |
| --- | --- |
| 외부 API 일시 장애 | 장애 복구 후 Outbox 재시도 또는 Consumer 재처리 흐름 확인 |
| Outbox `FAILED` | 관리자 API `POST /api/v1/admin/outbox-events/{eventId}/retry`로 재처리 요청 |
| Consumer `FAILED` 반복 | 외부 URL, 응답 코드, Timeout, Payload 오류 확인 |
| DLT 적재 | `GET /api/v1/admin/dead-letter-order-events`로 원본 Payload와 실패 원인 확인 |

운영에서 수동 DB 수정으로 이벤트 상태를 바꾸는 것은 마지막 수단으로 둔다.
가능하면 관리자 API와 재처리 흐름을 사용한다.

---

## 6. 관련 코드와 테스트

| 위치 | 설명 |
| --- | --- |
| [OrderEventDeliveryService.java](../../src/main/java/com/example/coffeeorder/event/service/OrderEventDeliveryService.java) | 외부 이벤트 전송과 결과 로그 기록 |
| [HttpExternalOrderEventClient.java](../../src/main/java/com/example/coffeeorder/event/client/HttpExternalOrderEventClient.java) | 외부 API HTTP 호출 |
| [ExternalOrderEventLog.java](../../src/main/java/com/example/coffeeorder/event/entity/ExternalOrderEventLog.java) | 외부 전송 결과 이력 |
| [OrderCompletedEventProcessor.java](../../src/main/java/com/example/coffeeorder/event/kafka/consumer/OrderCompletedEventProcessor.java) | Kafka Consumer 외부 API 전송 |
| [OrderEventDeliveryServiceIntegrationTest.java](../../src/test/java/com/example/coffeeorder/event/service/OrderEventDeliveryServiceIntegrationTest.java) | 성공, 실패, Timeout 로그 검증 |
| [OrderExternalEventIntegrationTest.java](../../src/test/java/com/example/coffeeorder/order/controller/OrderExternalEventIntegrationTest.java) | 외부 전송 실패가 주문을 Rollback하지 않음 |
| [OrderCompletedEventProcessorIntegrationTest.java](../../src/test/java/com/example/coffeeorder/event/kafka/consumer/OrderCompletedEventProcessorIntegrationTest.java) | Consumer 성공, 실패, Timeout 처리 |

관련 기술 문서: [외부 API 장애 처리와 Mock API 전략](../wiki/EXTERNAL_ORDER_EVENT_AND_MOCK_API.md)
