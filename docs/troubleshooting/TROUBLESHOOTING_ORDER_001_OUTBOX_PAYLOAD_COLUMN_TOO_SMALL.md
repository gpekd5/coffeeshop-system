# 주문 처리 중 Outbox Payload 컬럼 크기 문제

## 1. 증상

새로운 `Idempotency-Key`를 사용하고 장바구니와 포인트가 정상인데도 주문 API가 계속 실패한다.

| 증상 | 예시 |
| --- | --- |
| 주문 API가 `409 Conflict`를 반환함 | `ORDER_PROCESSING_CONFLICT` |
| 멱등키를 새 UUID로 바꿔도 동일하게 실패함 | `550e8400-e29b-41d4-a716-446655440001` |
| 장바구니 항목과 포인트는 그대로 남아 있음 | 주문 트랜잭션 Rollback |
| `orders`, `payments`, `outbox_events`는 늘지 않음 | 주문 저장 중 예외 발생 |

응답 예시는 다음과 같다.

```json
{
  "success": false,
  "code": "ORDER_PROCESSING_CONFLICT",
  "message": "주문 처리 중 동시성 충돌이 발생했습니다.",
  "data": null
}
```

---

## 2. 영향

주문 트랜잭션이 Rollback되므로 주문, 결제, 포인트 사용 이력, Outbox 이벤트는 생성되지 않는다.

사용자 입장에서는 주문이 실패하고 장바구니가 유지된다.
겉으로는 동시성 또는 멱등키 문제처럼 보이지만, 실제 원인이 DB 컬럼 크기일 수 있다.

---

## 3. 먼저 확인할 것

| 확인 대상 | 정상 기준 |
| --- | --- |
| 멱등키 형식 | UUID 형식, 예: `550e8400-e29b-41d4-a716-446655440001` |
| 장바구니 | 주문 가능한 `ON_SALE` 메뉴가 1개 이상 존재 |
| 포인트 | 주문 금액 이상 보유 |
| 주문 목록 | 같은 회원과 멱등키의 기존 주문 존재 여부 |
| Outbox Payload 컬럼 | `outbox_events.payload`가 `TEXT` 이상 |

멱등키와 장바구니가 정상인데도 같은 오류가 반복되면 DB 컬럼 정의를 바로 확인한다.

---

## 4. 원인별 확인 방법

### 같은 멱등키를 재사용한 경우

이미 성공한 주문의 멱등키를 다시 사용하면 정상적으로는 기존 주문 결과가 반환된다.

```text
200 OK
code: ORDER_ALREADY_PROCESSED
```

따라서 새 UUID를 사용했는데도 `ORDER_PROCESSING_CONFLICT`가 반복된다면 단순 멱등키 재사용 문제가 아닐 가능성이 높다.

### 주문 완료 이벤트 Payload 저장 실패

주문 트랜잭션은 주문, 결제, 포인트 이력, 장바구니 삭제와 함께 Outbox 이벤트를 저장한다.

```text
주문 저장
→ 주문 항목 저장
→ 결제 저장
→ 포인트 이력 저장
→ 장바구니 항목 삭제
→ outbox_events 저장
```

이때 `outbox_events.payload`가 `tinytext`처럼 작은 타입이면 주문 완료 이벤트 JSON을 저장하지 못한다.
MySQL에서는 `tinytext`가 최대 255바이트라서 메뉴 정보가 포함된 주문 이벤트 Payload가 쉽게 초과된다.

다음 쿼리로 컬럼 타입을 확인한다.

```sql
select table_name,
       column_name,
       column_type
from information_schema.columns
where table_schema = database()
  and table_name in ('outbox_events', 'dead_letter_order_events')
  and column_name = 'payload'
order by table_name;
```

문제가 있는 예시는 다음과 같다.

```text
outbox_events.payload=tinytext
dead_letter_order_events.payload=tinytext
```

정상 기준은 다음과 같다.

```text
outbox_events.payload=text
dead_letter_order_events.payload=text
```

### 에러 코드가 실제 원인을 가리는 경우

`OrderFacade`는 주문 생성 중 `DataIntegrityViolationException`이 발생하면 동시 멱등 요청일 가능성을 고려해 기존 주문을 재조회한다.

```text
DataIntegrityViolationException
→ 같은 회원 + 같은 멱등키 주문 재조회
→ 기존 주문 없음
→ ORDER_PROCESSING_CONFLICT
```

이 흐름은 동일 멱등키 동시 요청을 처리하기 위한 방어 로직이다.
하지만 Outbox Payload 길이 초과처럼 다른 DB 무결성 오류도 같은 예외 타입으로 들어오면 겉으로는 `ORDER_PROCESSING_CONFLICT`가 반환될 수 있다.

서버 로그에서 다음과 같은 메시지가 있는지 확인한다.

```text
Data too long for column 'payload'
DataIntegrityViolationException
```

---

## 5. 복구 또는 대응

기존 로컬 DB는 다음 SQL로 보정한다.

```sql
ALTER TABLE outbox_events MODIFY payload TEXT NOT NULL;
ALTER TABLE dead_letter_order_events MODIFY payload TEXT NOT NULL;
```

이후 장바구니를 다시 채우고 새 UUID 멱등키로 주문을 재시도한다.

새 스키마 생성 시 같은 문제가 반복되지 않도록 Entity에는 명시적으로 `TEXT` 컬럼 정의를 둔다.

```java
@Lob
@Column(
        nullable = false,
        columnDefinition = "TEXT"
)
private String payload;
```

로컬 데모 SQL을 다시 실행하는 경우 `docs/http/00-local-demo-data.sql`에 포함된 ALTER 문으로 기존 컬럼이 보정된다.

---

## 6. 관련 코드와 테스트

| 위치 | 설명 |
| --- | --- |
| [OrderFacade.java](../../src/main/java/com/example/coffeeorder/order/facade/OrderFacade.java) | `DataIntegrityViolationException` 발생 시 기존 멱등 주문 재조회 |
| [OrderTransactionService.java](../../src/main/java/com/example/coffeeorder/order/service/OrderTransactionService.java) | 주문 트랜잭션 안에서 Outbox 이벤트 저장 |
| [OutboxEvent.java](../../src/main/java/com/example/coffeeorder/event/outbox/entity/OutboxEvent.java) | Outbox Payload 컬럼 정의 |
| [DeadLetterOrderEvent.java](../../src/main/java/com/example/coffeeorder/event/kafka/consumer/entity/DeadLetterOrderEvent.java) | Dead Letter Payload 컬럼 정의 |
| [00-local-demo-data.sql](../http/00-local-demo-data.sql) | 로컬 DB Payload 컬럼 보정 SQL |
| [OrderControllerIntegrationTest.java](../../src/test/java/com/example/coffeeorder/order/controller/OrderControllerIntegrationTest.java) | 주문 API 통합 테스트 |
| [OrderOutboxIntegrationTest.java](../../src/test/java/com/example/coffeeorder/order/service/OrderOutboxIntegrationTest.java) | 주문과 Outbox 동시 저장 검증 |

관련 기술 문서: [Transactional Outbox 설계](../wiki/TRANSACTIONAL_OUTBOX.md)
