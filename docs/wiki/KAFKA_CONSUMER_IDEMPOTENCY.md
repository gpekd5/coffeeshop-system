# Kafka Consumer 멱등성 전략

## 1. 문제 배경

Outbox와 Kafka 구조는 이벤트 유실 가능성을 낮추지만, 같은 이벤트가 여러 번 전달될 수 있다.

```text
Outbox Publisher
→ Kafka 발행 성공
→ Outbox 상태 변경 전 서버 종료
→ 같은 이벤트 재발행
```

Kafka Consumer도 재시도, rebalance, 장애 복구 과정에서 같은 메시지를 다시 받을 수 있다.

중복 이벤트가 그대로 외부 데이터 수집 플랫폼에 전달되면 같은 주문이 여러 번 집계될 수 있다.

---

## 2. 단순 구현의 한계

Consumer에서 메시지를 받을 때마다 외부 API를 호출하면 다음 문제가 생긴다.

| 상황 | 문제 |
| --- | --- |
| 같은 `eventId` 재수신 | 외부 API 중복 호출 |
| Consumer 처리 중 서버 종료 | 처리 여부를 판단하기 어려움 |
| 외부 API 실패 후 Kafka 재시도 | 실패 이력과 재처리 기준이 사라짐 |
| 여러 Consumer 인스턴스 | JVM 메모리 기반 중복 방지는 불가능 |

중복 처리는 애플리케이션 메모리가 아니라 여러 인스턴스가 공유하는 저장소를 기준으로 해야 한다.

---

## 3. 선택한 전략

`processed_kafka_events` 테이블에 `eventId` 처리 상태를 저장한다.

| 상태 | 의미 |
| --- | --- |
| `PROCESSING` | 현재 Consumer가 처리 중 |
| `COMPLETED` | 외부 API 호출 성공 |
| `FAILED` | 외부 API 호출 실패 또는 Timeout |

처리 시작 시 `eventId`를 비관적 쓰기 락으로 조회한다.

```text
eventId 조회 FOR UPDATE
→ 없음: PROCESSING 생성 후 처리
→ COMPLETED: 외부 API 호출 생략
→ PROCESSING lease 유효: 다른 Consumer 처리 중으로 보고 생략
→ PROCESSING lease 만료 또는 FAILED: 다시 처리
```

---

## 4. 구현 흐름

```text
OrderCompletedEventConsumer
→ Kafka 메시지 수신
→ OrderCompletedEventProcessor
→ Payload 역직렬화
→ ProcessedKafkaEventService.beginProcessing()
→ 처리 대상이면 외부 API 호출
→ 성공: COMPLETED
→ 실패: FAILED 저장 후 예외 전파
→ Kafka ErrorHandler 재시도
→ 최대 재시도 후 Dead Letter Topic
```

`processing_deadline_at`은 Consumer가 `PROCESSING` 상태 저장 후 종료되는 상황을 위한 lease다.
lease가 유효하면 다른 Consumer가 같은 이벤트를 처리하지 않고, lease가 만료되면 장애 중단으로 보고 재처리한다.

---

## 5. 핵심 코드 위치

| 코드 | 역할 |
| --- | --- |
| `src/main/java/com/example/coffeeorder/event/kafka/consumer/OrderCompletedEventConsumer.java` | Kafka 주문 완료 Topic과 DLT 수신 |
| `src/main/java/com/example/coffeeorder/event/kafka/consumer/OrderCompletedEventProcessor.java` | 역직렬화, 중복 처리 판단, 외부 API 호출 |
| `src/main/java/com/example/coffeeorder/event/kafka/consumer/service/ProcessedKafkaEventService.java` | 처리 상태 선점, 완료, 실패 기록 |
| `src/main/java/com/example/coffeeorder/event/kafka/consumer/repository/ProcessedKafkaEventRepository.java` | `eventId` 비관적 쓰기 잠금 조회 |
| `src/main/java/com/example/coffeeorder/event/kafka/consumer/entity/ProcessedKafkaEvent.java` | Consumer 처리 상태 Entity |
| `src/main/java/com/example/coffeeorder/event/kafka/config/KafkaOrderEventConfig.java` | 재시도와 Dead Letter Publishing Recoverer 설정 |
| `src/main/java/com/example/coffeeorder/event/kafka/consumer/service/DeadLetterOrderEventService.java` | DLT 이벤트 저장 |

---

## 6. 테스트로 검증한 내용

| 테스트 | 검증 내용 |
| --- | --- |
| `OrderCompletedEventConsumerTest` | Kafka 메시지와 DLT 메시지 위임 |
| `OrderCompletedEventProcessorIntegrationTest` | 정상 처리, 중복 스킵, 실패, Timeout, lease 만료 재처리 |
| `AdminProcessedKafkaEventControllerIntegrationTest` | Consumer 처리 이력 관리자 조회 |
| `AdminDeadLetterOrderEventControllerIntegrationTest` | Dead Letter 주문 이벤트 목록/상세 조회 |

특히 중요한 검증은 다음이다.

```text
같은 eventId를 두 번 처리해도 외부 API는 한 번만 호출된다.
COMPLETED 이벤트는 재호출하지 않는다.
PROCESSING lease가 만료되면 재처리할 수 있다.
외부 API 실패는 FAILED로 기록하고 Kafka 재시도 흐름으로 넘긴다.
```

---

## 실제 적용 코드

### 적용 파일 경로

| 파일 | 역할 |
| --- | --- |
| `src/main/java/com/example/coffeeorder/event/kafka/consumer/OrderCompletedEventConsumer.java` | Kafka Topic과 DLT 수신 |
| `src/main/java/com/example/coffeeorder/event/kafka/consumer/OrderCompletedEventProcessor.java` | 메시지 역직렬화, 중복 판단, 외부 API 호출 |
| `src/main/java/com/example/coffeeorder/event/kafka/consumer/service/ProcessedKafkaEventService.java` | 처리 시작/완료/실패 상태 관리 |
| `src/main/java/com/example/coffeeorder/event/kafka/consumer/entity/ProcessedKafkaEvent.java` | `eventId` 기준 처리 이력과 lease 상태 |
| `src/main/java/com/example/coffeeorder/event/kafka/consumer/repository/ProcessedKafkaEventRepository.java` | 처리 이력 비관적 쓰기 잠금 |

### 호출 흐름

```text
Kafka order.completed 메시지
→ OrderCompletedEventConsumer.consume()
→ OrderCompletedEventProcessor.process()
→ ProcessedKafkaEventService.beginProcessing()
→ 새 이벤트면 PROCESSING 저장
→ COMPLETED 또는 lease 유효 PROCESSING이면 중복 스킵
→ 외부 API 호출
→ 성공: COMPLETED
→ 실패: FAILED 후 Kafka 재시도/DLT 흐름
```

### 핵심 코드만 짧게 발췌

```java
boolean shouldProcess = processedKafkaEventService.beginProcessing(...);

if (!shouldProcess) {
    kafkaConsumerMetricsRecorder.recordDuplicateSkip();
    return;
}
```

```java
ProcessedKafkaEvent event = processedKafkaEventRepository
        .findByEventIdForUpdate(eventId)
        .orElse(null);

if (event == null) {
    processedKafkaEventRepository.saveAndFlush(ProcessedKafkaEvent.start(...));
    return true;
}

if (event.isCompleted() || event.isProcessingLeaseActive(now)) {
    return false;
}
```

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<ProcessedKafkaEvent> findByEventIdForUpdate(@Param("eventId") String eventId);
```

### 이 코드가 해당 개념을 구현하는 이유

Kafka는 At-Least-Once 전송 특성상 같은 메시지가 다시 들어올 수 있다. Consumer는 외부 API를 호출하기 전에 `processed_kafka_events`를 `eventId` 기준으로 잠그고 처리 상태를 확인한다. 이미 `COMPLETED`인 이벤트나 아직 lease가 유효한 `PROCESSING` 이벤트는 외부 API를 다시 호출하지 않으므로 중복 소비가 중복 처리로 이어지지 않는다.

---

## 7. 한계와 개선 방향

현재 Consumer 멱등성은 애플리케이션 DB의 `processed_kafka_events`를 기준으로 한다.

이 방식은 여러 애플리케이션 인스턴스가 같은 처리 상태를 공유할 수 있어 명확하지만, 모든 Consumer 처리 시작마다 DB 조회와 잠금이 필요하다.

후속 개선 후보는 다음과 같다.

| 개선 | 설명 |
| --- | --- |
| 외부 API 멱등키 연동 | 외부 수집 플랫폼도 `eventId` 기준으로 최종 중복 방지 |
| 처리 이력 보관 정책 | 오래된 `COMPLETED` 이벤트 정리 |
| Consumer Lag 모니터링 | Kafka Broker 기준 Lag 지표 추가 |
| 재시도 정책 세분화 | 실패 원인별 재시도 가능 여부 구분 |
