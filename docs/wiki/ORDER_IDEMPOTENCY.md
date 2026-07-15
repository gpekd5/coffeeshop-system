# 주문 멱등성 처리 전략

## 1. 문제 배경

주문 API는 네트워크 재시도나 사용자의 반복 클릭으로 같은 요청이 여러 번 들어올 수 있다.

예를 들어 사용자가 주문 버튼을 눌렀는데 응답이 늦어 다시 누르면 서버에는 같은 주문 요청이 두 번 도착할 수 있다.

```text
요청 1: 주문 생성
요청 2: 같은 주문 재시도
```

이때 주문이 두 번 생성되면 포인트도 두 번 차감된다.

---

## 2. 단순 선조회 방식의 한계

단순히 주문 생성 전에 기존 주문을 조회하는 방식은 동시 요청에서 안전하지 않다.

```text
요청 A: 기존 주문 없음 확인
요청 B: 기존 주문 없음 확인
요청 A: 주문 저장
요청 B: 주문 저장
```

두 요청이 거의 동시에 실행되면 둘 다 "기존 주문 없음"을 확인할 수 있다.

따라서 애플리케이션 선조회만으로는 중복 주문을 완전히 막을 수 없다.

---

## 3. 선택한 전략

현재 프로젝트는 두 단계로 멱등성을 보장한다.

| 단계 | 역할 |
| --- | --- |
| 애플리케이션 선조회 | 이미 처리된 멱등키면 기존 주문 결과를 바로 반환 |
| DB Unique 제약조건 | 동시 요청에서 최종적으로 중복 INSERT를 차단 |

`orders` 테이블에는 다음 Unique 제약조건을 둔다.

```text
(member_id, idempotency_key)
```

멱등키는 회원 범위에서만 유일하다.

즉 서로 다른 회원은 같은 `Idempotency-Key`를 사용해도 각각 주문할 수 있다.

---

## 4. 구현 흐름

```text
OrderController
→ Idempotency-Key Header 추출
→ OrderFacade
→ 멱등키 형식 검증
→ OrderTransactionService

[트랜잭션 내부]
1. memberId + idempotencyKey로 기존 주문 조회
2. 있으면 기존 주문 결과 반환
3. 없으면 신규 주문 처리

[동시 요청 충돌]
1. 두 요청이 동시에 INSERT 시도
2. 하나만 성공
3. 나머지는 DB Unique 제약조건 위반
4. OrderFacade가 DataIntegrityViolationException 처리
5. 기존 주문을 다시 조회해 반환
```

---

## 5. 핵심 코드 위치

| 코드 | 역할 |
| --- | --- |
| `src/main/java/com/example/coffeeorder/order/controller/OrderController.java` | `Idempotency-Key` Header 수신 |
| `src/main/java/com/example/coffeeorder/order/facade/OrderFacade.java` | 멱등키 검증, Unique 충돌 후 기존 주문 조회 |
| `src/main/java/com/example/coffeeorder/order/service/OrderTransactionService.java` | 기존 주문 선조회와 주문 생성 처리 |
| `src/main/java/com/example/coffeeorder/order/entity/Order.java` | `(member_id, idempotency_key)` Unique 제약조건 선언 |
| `src/main/java/com/example/coffeeorder/order/repository/OrderRepository.java` | `findByMember_IdAndIdempotencyKey` 조회 |

---

## 6. API 응답 전략

이미 처리된 주문이면 신규 주문과 같은 `200 OK`로 기존 주문 정보를 반환한다.

다만 응답 code와 message는 멱등 재요청임을 나타낸다.

```json
{
  "success": true,
  "code": "ORDER_ALREADY_PROCESSED",
  "message": "이미 처리된 주문입니다.",
  "data": {
    "orderId": 1
  }
}
```

클라이언트는 이 응답을 실패로 처리하지 않고, 이미 성공한 주문 결과로 볼 수 있다.

---

## 7. 테스트로 검증한 내용

`OrderControllerIntegrationTest`에서 다음을 검증한다.

| 테스트 | 검증 내용 |
| --- | --- |
| 멱등키 누락 | `IDEMPOTENCY_KEY_REQUIRED` 반환 |
| UUID 형식 아님 | `INVALID_INPUT` 반환 |
| 동일 멱등키 재요청 | 기존 주문 반환, 포인트 추가 차감 없음 |
| 다른 회원의 동일 멱등키 | 회원별로 각각 주문 가능 |
| 동일 멱등키 동시 요청 | 주문, 결제, 포인트 차감이 한 번만 발생 |

---

## 8. 한계와 개선 방향

현재 멱등키는 같은 회원의 동일 key만 구분한다.

후속 개선에서 검토할 수 있는 내용은 다음과 같다.

| 개선점 | 설명 |
| --- | --- |
| 요청 본문 해시 저장 | 같은 멱등키로 다른 주문 내용을 보내는 충돌 감지 |
| 멱등키 보관 기간 | 오래된 멱등키를 언제까지 유지할지 결정 |
| Redis 임시 상태 | 처리 중인 요청 상태를 더 빠르게 공유 |

현재 과제 범위에서는 DB Unique 제약조건이 가장 중요한 최종 방어선이다.
