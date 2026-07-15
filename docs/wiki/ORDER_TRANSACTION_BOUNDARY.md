# 주문 트랜잭션 경계와 Rollback 전략

## 1. 문제 배경

커피 주문은 단순히 `orders` 테이블에 행 하나를 저장하는 작업이 아니다.

주문이 성공하려면 다음 데이터가 함께 맞아야 한다.

```text
장바구니 항목
→ 메뉴 상태와 가격
→ 포인트 잔액
→ 주문
→ 주문 항목
→ 결제
→ 포인트 이력
→ 장바구니 항목 삭제
```

이 중 하나만 성공하고 다른 단계가 실패하면 데이터 정합성이 깨진다.

예를 들어 포인트는 차감됐는데 주문이 저장되지 않으면 사용자는 돈만 잃은 상태가 된다.

---

## 2. 단순 구현의 한계

각 단계를 별도 트랜잭션으로 처리하면 다음 문제가 생긴다.

| 상황 | 문제 |
| --- | --- |
| 포인트 차감 성공 후 주문 저장 실패 | 포인트만 사라진다. |
| 주문 저장 성공 후 결제 저장 실패 | 결제 없는 주문이 남는다. |
| 주문 저장 성공 후 포인트 이력 저장 실패 | 잔액 변경 근거가 사라진다. |
| 주문 성공 후 장바구니 삭제 실패 | 이미 주문한 상품이 장바구니에 남는다. |

따라서 주문 DB 처리 흐름은 하나의 논리적 작업 단위로 묶어야 한다.

---

## 3. 선택한 전략

주문 DB 처리는 `OrderTransactionService`에서 하나의 트랜잭션으로 처리한다.

핵심 원칙은 다음과 같다.

| 원칙 | 설명 |
| --- | --- |
| DB 정합성 작업은 하나의 트랜잭션 | 주문, 결제, 포인트, 이력을 함께 Commit 또는 Rollback |
| 외부 API 호출은 트랜잭션 밖 | 네트워크 지연 때문에 DB Lock이 오래 유지되는 것을 방지 |
| 장바구니, 메뉴, 포인트 잠금 순서 고정 | Deadlock 가능성을 줄임 |
| 실패 시 예외를 삼키지 않음 | Spring 트랜잭션 Rollback이 동작하도록 예외 전파 |

---

## 4. 구현 흐름

```text
OrderController
→ OrderFacade
→ OrderTransactionService

[트랜잭션 내부]
1. 기존 멱등 주문 확인
2. 장바구니 비관적 쓰기 잠금 조회
3. 장바구니 항목 검증
4. 메뉴 비관적 읽기 잠금 조회
5. 메뉴 판매 상태와 가격 검증
6. 총 주문 금액 계산
7. 포인트 비관적 쓰기 잠금 조회
8. 포인트 잔액 검증 및 차감
9. 주문 저장
10. 주문 항목 저장
11. 결제 저장
12. 포인트 이력 저장
13. 장바구니 항목 삭제
14. Commit
```

---

## 5. 핵심 코드 위치

| 코드 | 역할 |
| --- | --- |
| `src/main/java/com/example/coffeeorder/order/controller/OrderController.java` | 주문 요청 수신, 멱등키 Header 추출 |
| `src/main/java/com/example/coffeeorder/order/facade/OrderFacade.java` | 멱등성 충돌 처리, 트랜잭션 서비스 호출 |
| `src/main/java/com/example/coffeeorder/order/service/OrderTransactionService.java` | 주문 DB 작업을 하나의 트랜잭션으로 처리 |
| `src/main/java/com/example/coffeeorder/cart/repository/CartRepository.java` | 장바구니 비관적 쓰기 잠금 |
| `src/main/java/com/example/coffeeorder/menu/repository/MenuRepository.java` | 메뉴 비관적 읽기 잠금 |
| `src/main/java/com/example/coffeeorder/point/repository/PointRepository.java` | 포인트 비관적 쓰기 잠금 |

---

## 6. 테스트로 검증한 내용

`OrderControllerIntegrationTest`에서 다음을 검증한다.

| 테스트 관점 | 검증 내용 |
| --- | --- |
| 정상 주문 | 주문, 결제, 포인트 이력, 장바구니 삭제가 함께 반영된다. |
| 포인트 부족 | 주문, 결제, 포인트 이력, 장바구니 삭제가 모두 Rollback된다. |
| 판매 불가 메뉴 | 주문이 생성되지 않고 장바구니가 유지된다. |
| 동시 주문 | 포인트 잔액과 주문 데이터 정합성이 유지된다. |

---

## 7. 한계와 개선 방향

현재 구조는 DB 정합성에 집중한다.

후속 이슈에서 외부 주문 데이터 전송이 추가되면 다음 문제가 생긴다.

```text
주문 Commit 성공
→ 외부 API 호출 전 서버 종료
→ 외부 이벤트 유실 가능
```

이 문제는 Transactional Outbox에서 다룬다.
