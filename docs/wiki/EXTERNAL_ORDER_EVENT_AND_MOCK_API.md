# 외부 API 장애 처리와 Mock API 전략

## 1. 문제 배경

주문이 완료되면 주문 데이터를 외부 데이터 수집 플랫폼으로 전달해야 한다.

하지만 외부 시스템은 언제든 느려지거나 실패할 수 있다.

```text
주문 결제 완료
→ 외부 데이터 수집 API 호출
→ 외부 API 500 또는 Timeout
```

이때 외부 API 실패 때문에 이미 완료된 주문과 포인트 결제를 Rollback하면 사용자 입장에서는 정상 주문이 실패로 보인다.

따라서 외부 데이터 전송 실패는 주문 성공 여부와 분리해야 한다.

---

## 2. 단순 구현의 한계

가장 단순한 구현은 주문 트랜잭션 안에서 외부 API를 호출하는 것이다.

```text
DB Transaction 시작
→ 주문 저장
→ 포인트 차감
→ 외부 API 호출
→ Commit
```

이 방식은 다음 문제가 있다.

| 문제 | 영향 |
| --- | --- |
| 외부 API 지연 | DB Connection과 Lock을 오래 점유 |
| 외부 API 실패 | 주문까지 실패 처리될 위험 |
| Timeout | 사용자 응답시간 증가 |
| 외부 API 장애 | 주문 성공률에 직접 영향 |

외부 호출은 네트워크 I/O이므로 DB 트랜잭션 안에 넣지 않는 것이 이 프로젝트의 기본 원칙이다.

---

## 3. 선택한 전략

1차 구현에서는 DB Commit 이후 외부 Mock API를 동기 호출한다.

```text
주문 DB Transaction
→ Commit
→ 외부 Mock API 호출
→ 전송 결과 로그 기록
→ 주문 응답 반환
```

핵심은 두 가지다.

| 전략 | 설명 |
| --- | --- |
| 외부 호출은 Commit 이후 실행 | 주문, 결제, 포인트 정합성을 먼저 확정 |
| 실패/Timeout은 로그로 기록 | 완료된 주문 상태를 변경하지 않음 |

Mock API는 실제 외부 수집 플랫폼 대신 정상, 실패, 지연, Timeout을 재현하는 학습용 API다.
현재 구현에서는 성능 비교가 필요할 때 `APP_ORDER_EVENT_DELIVERY_MODE=sync`로 이 1차 흐름을 켤 수 있다.
기본값인 `outbox`에서는 주문 API가 외부 API를 직접 호출하지 않고 Outbox/Kafka 흐름으로 분리한다.

---

## 4. 구현 흐름

```text
OrderController
→ OrderFacade
→ OrderTransactionService
→ 주문 DB Commit
→ OrderEventDeliveryService
→ ExternalOrderEventClient
→ MockOrderEventController
→ ExternalOrderEventLog 저장
```

Mock API는 다음 Query Parameter로 장애 상황을 만든다.

| 파라미터 | 설명 |
| --- | --- |
| `delayMillis` | 응답 지연 시간. 0 이상 3000 이하 |
| `status` | 반환 HTTP Status. 100 이상 599 이하 |

운영 `prod` 프로필에서는 Mock API 컨트롤러를 등록하지 않는다.
Kafka Consumer를 운영에서 켤 때도 외부 이벤트 URL이 localhost 또는 `/mock/v1/order-events`이면 시작 단계에서 거부한다.

---

## 5. 핵심 코드 위치

| 코드 | 역할 |
| --- | --- |
| `src/main/java/com/example/coffeeorder/event/service/OrderEventDeliveryService.java` | Commit 이후 외부 이벤트 전송과 결과 로그 기록 |
| `src/main/java/com/example/coffeeorder/event/client/HttpExternalOrderEventClient.java` | Java HttpClient 기반 외부 API 호출 |
| `src/main/java/com/example/coffeeorder/event/controller/MockOrderEventController.java` | 로컬/테스트용 Mock API |
| `src/main/java/com/example/coffeeorder/event/entity/ExternalOrderEventLog.java` | 외부 전송 결과 로그 |
| `src/main/java/com/example/coffeeorder/event/repository/ExternalOrderEventLogRepository.java` | 전송 로그 조회 |
| `src/main/java/com/example/coffeeorder/event/kafka/config/ProdExternalOrderEventPropertiesValidator.java` | 운영 Consumer 외부 URL 안전성 검증 |

---

## 6. 테스트로 검증한 내용

| 테스트 | 검증 내용 |
| --- | --- |
| `OrderEventDeliveryServiceIntegrationTest` | 성공, 실패, Timeout 결과 로그 기록 |
| `OrderExternalEventIntegrationTest` | 외부 호출 실패가 완료 주문을 Rollback하지 않음 |
| `MockOrderEventControllerIntegrationTest` | Mock API 정상, 실패, 지연, 파라미터 검증 |
| `MockOrderEventControllerDisabledIntegrationTest` | Mock API 비활성화 시 접근 불가 |
| `MockOrderEventProfileConditionTest` | local/test/prod 프로필별 Mock API 등록 조건 |
| `ProdExternalOrderEventPropertiesValidatorTest` | prod Consumer 활성화 시 local/mock URL 차단 |

---

## 실제 적용 코드

### 적용 파일 경로

| 파일 | 역할 |
| --- | --- |
| `src/main/java/com/example/coffeeorder/order/facade/OrderFacade.java` | 주문 트랜잭션 완료 후 sync 모드 외부 이벤트 전송 |
| `src/main/java/com/example/coffeeorder/event/service/OrderEventDeliveryService.java` | 주문 완료 이벤트 생성, 외부 API 호출, 결과 로그 기록 |
| `src/main/java/com/example/coffeeorder/event/client/HttpExternalOrderEventClient.java` | 외부 API HTTP 호출, 실패/Timeout 결과 변환 |
| `src/main/java/com/example/coffeeorder/event/service/ExternalOrderEventLogService.java` | 외부 전송 결과를 별도 트랜잭션으로 저장 |
| `src/main/java/com/example/coffeeorder/event/controller/MockOrderEventController.java` | local/test 전용 Mock API, 지연/실패 응답 재현 |

### 호출 흐름

```text
sync 모드 주문
→ OrderFacade.createOrder()
→ OrderTransactionService Commit
→ OrderFacade.sendSynchronouslyIfNeeded()
→ OrderEventDeliveryService.sendOrderCompletedEvent()
→ HttpExternalOrderEventClient.send()
→ ExternalOrderEventLogService.record()
```

### 핵심 코드만 짧게 발췌

```java
if (!orderEventDeliveryProperties.mode().isSynchronous() || result.alreadyProcessed()) {
    return;
}
orderEventDeliveryService.sendOrderCompletedEvent(memberId, result.response());
```

```java
try {
    HttpResponse<String> response = httpClient.send(createRequest(request), ...);
    if (isSuccessful(response.statusCode())) {
        return recordAndReturn(ExternalOrderEventSendResult.success(...));
    }
    return recordAndReturn(ExternalOrderEventSendResult.failed(...));
} catch (HttpTimeoutException exception) {
    return recordAndReturn(ExternalOrderEventSendResult.timeout(...));
}
```

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void record(OrderCompletedEventRequest event, ExternalOrderEventSendResult result) {
    externalOrderEventLogRepository.saveAndFlush(ExternalOrderEventLog.of(event, result));
}
```

### 이 코드가 해당 개념을 구현하는 이유

주문 DB 트랜잭션은 `OrderTransactionService`에서 먼저 끝나고, 외부 API 호출은 `OrderFacade`의 후속 처리로 실행된다. 따라서 외부 API 실패나 Timeout이 이미 완료된 주문을 Rollback하지 않는다. 전송 결과는 `REQUIRES_NEW` 트랜잭션으로 별도 저장되어 외부 연동 성공/실패/Timeout을 추적할 수 있고, Mock API의 `delayMillis`와 `status` 파라미터로 실제 외부 시스템 지연과 장애를 로컬에서 재현한다.

---

## 7. 한계와 개선 방향

1차 동기 Mock API 구조는 외부 장애가 주문 DB를 Rollback하지 않도록 분리했지만, 주문 응답은 여전히 외부 API 응답시간의 영향을 받는다.

또한 다음 구간에서 서버가 종료되면 이벤트가 유실될 수 있다.

```text
주문 Commit 완료
→ 외부 API 호출 전 서버 종료
```

이 한계를 줄이기 위해 2차 구조에서는 Transactional Outbox와 Kafka를 사용한다.
주문 트랜잭션 안에서 Outbox Event를 저장하고, 별도 Publisher와 Consumer가 외부 데이터 수집 플랫폼으로 전달한다.
