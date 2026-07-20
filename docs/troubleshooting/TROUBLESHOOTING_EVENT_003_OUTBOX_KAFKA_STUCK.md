# Outbox/Kafka 발행 적체

## 1. 증상

주문은 성공하지만 `outbox_events`가 발행 완료 상태로 바뀌지 않는 상황이다.

| 증상 | 예시 |
| --- | --- |
| `PENDING` 이벤트가 계속 쌓임 | Publisher가 실행되지 않거나 Kafka 연결 실패 |
| `FAILED` 이벤트가 증가함 | Kafka 발행 예외 반복 |
| 가장 오래된 Pending age가 커짐 | 발행 처리량 부족 또는 Publisher 중단 |
| Kafka Topic에 메시지가 없음 | Outbox에서 Kafka로 넘어가지 않음 |

---

## 2. 영향

Outbox 적체는 주문 성공 자체를 깨지는 않지만, 외부 데이터 수집 플랫폼으로 이벤트가 늦게 전달된다.

오래 방치하면 외부 집계 데이터가 실제 주문 데이터와 계속 벌어진다.

---

## 3. 먼저 확인할 것

| 확인 대상 | 정상 기준 |
| --- | --- |
| Publisher 활성화 | `APP_KAFKA_ORDER_EVENT_PUBLISHER_ENABLED=true` |
| Kafka 접속 | `SPRING_KAFKA_BOOTSTRAP_SERVERS`가 실제 Broker를 가리킴 |
| Topic | `APP_KAFKA_ORDER_EVENT_TOPIC`이 존재하거나 생성 가능 |
| Outbox 상태 | `PENDING`, `FAILED`, `PUBLISHED` 비율 확인 |
| 재시도 시각 | `next_retry_at`이 현재 시각보다 과거여야 재시도 대상 |

---

## 4. 원인별 확인 방법

### Publisher가 비활성화된 경우

Kafka Publisher는 기본 비활성화다.

```text
APP_KAFKA_ORDER_EVENT_PUBLISHER_ENABLED=false
```

이 값이면 Outbox 저장까지만 되고 Kafka 발행은 실행되지 않는다.
학습 또는 운영 테스트에서 Kafka 발행까지 보려면 true로 켠다.

### Kafka 연결 실패

Outbox 이벤트가 `FAILED`로 바뀌고 `last_error`에 Kafka 연결 오류가 남는다.

```sql
select id,
       aggregate_id,
       status,
       retry_count,
       next_retry_at,
       last_error
from outbox_events
where status in ('PENDING', 'FAILED')
order by created_at asc;
```

`last_error`가 Broker 연결, Timeout, Topic 오류라면 Kafka 설정을 먼저 확인한다.

### Lease 때문에 잠시 대기 중인 경우

Publisher는 발행 대상 이벤트를 선점하면서 `next_retry_at`을 짧은 lease 시각으로 갱신한다.
서버가 발행 중 종료되면 lease가 끝난 뒤 다시 발행 대상이 된다.

```text
APP_KAFKA_ORDER_EVENT_PUBLISH_LEASE_SECONDS
```

`next_retry_at`이 미래면 아직 정상 대기 상태일 수 있다.

---

## 5. 복구 또는 대응

| 상황 | 대응 |
| --- | --- |
| Publisher 비활성화 | `APP_KAFKA_ORDER_EVENT_PUBLISHER_ENABLED=true` 설정 후 재기동 |
| Kafka Broker 장애 | Broker 복구 후 `FAILED` 이벤트의 `next_retry_at` 경과 확인 |
| 특정 이벤트 수동 재처리 | `POST /api/v1/admin/outbox-events/{eventId}/retry` 호출 |
| 적체량 증가 | `APP_KAFKA_ORDER_EVENT_PUBLISHER_BATCH_SIZE`, 발행 주기, Kafka 처리량 검토 |

Prometheus에서는 다음 지표를 우선 본다.

```text
coffee_order_outbox_events{status="PENDING"}
coffee_order_outbox_events{status="FAILED"}
coffee_order_outbox_oldest_pending_age_seconds
```

---

## 6. 관련 코드와 테스트

| 위치 | 설명 |
| --- | --- |
| [OutboxPublisherScheduler.java](../../src/main/java/com/example/coffeeorder/event/kafka/publisher/OutboxPublisherScheduler.java) | 주기적 Publisher 실행 |
| [OutboxPublisherService.java](../../src/main/java/com/example/coffeeorder/event/kafka/publisher/OutboxPublisherService.java) | Kafka 발행 성공/실패 처리 |
| [OutboxEventService.java](../../src/main/java/com/example/coffeeorder/event/outbox/service/OutboxEventService.java) | Outbox 조회, 예약, 상태 변경 |
| [AdminOutboxEventController.java](../../src/main/java/com/example/coffeeorder/event/outbox/controller/AdminOutboxEventController.java) | Outbox 조회와 수동 재처리 API |
| [EventMonitoringMeterBinder.java](../../src/main/java/com/example/coffeeorder/event/metrics/EventMonitoringMeterBinder.java) | Outbox 상태별 메트릭 |
| [OutboxPublisherServiceIntegrationTest.java](../../src/test/java/com/example/coffeeorder/event/kafka/publisher/OutboxPublisherServiceIntegrationTest.java) | 발행 성공/실패 상태 전이 |
| [AdminOutboxEventControllerIntegrationTest.java](../../src/test/java/com/example/coffeeorder/event/outbox/controller/AdminOutboxEventControllerIntegrationTest.java) | 관리자 조회와 재처리 API |
| [EventMonitoringMeterBinderIntegrationTest.java](../../src/test/java/com/example/coffeeorder/event/metrics/EventMonitoringMeterBinderIntegrationTest.java) | Outbox 메트릭 검증 |

관련 기술 문서: [Transactional Outbox 설계](../wiki/TRANSACTIONAL_OUTBOX.md)
