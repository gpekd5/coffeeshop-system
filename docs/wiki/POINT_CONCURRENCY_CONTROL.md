# 포인트 동시성 제어 전략

## 1. 문제 배경

포인트는 금액과 직접 연결된 데이터다.

같은 회원이 동시에 여러 주문을 요청하면 포인트가 초과 차감될 수 있다.

예를 들어 회원이 5,000P를 가지고 있고, 3,000P 주문 두 건이 동시에 들어온다고 가정한다.

```text
초기 잔액: 5,000P

요청 A: 3,000P 주문
요청 B: 3,000P 주문
```

동시성 제어가 없으면 두 요청이 모두 잔액 5,000P를 보고 성공할 수 있다.

결과적으로 최종 잔액이 음수가 되거나, 주문 성공 수와 포인트 이력이 맞지 않을 수 있다.

---

## 2. 단순 구현의 한계

단순 조회 후 차감 방식은 Lost Update 문제를 만들 수 있다.

```text
요청 A: 잔액 5,000 조회
요청 B: 잔액 5,000 조회
요청 A: 3,000 차감 후 2,000 저장
요청 B: 3,000 차감 후 2,000 저장
```

두 주문이 성공했는데 최종 잔액은 2,000P로 보일 수 있다.

실제로는 6,000P가 차감되어야 하므로 데이터가 맞지 않는다.

---

## 3. 선택한 전략

현재 프로젝트는 포인트 행에 비관적 쓰기 잠금을 사용한다.

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
```

핵심은 같은 회원의 포인트 행을 한 요청만 수정할 수 있게 만드는 것이다.

```text
요청 A
→ 포인트 행 Lock 획득
→ 잔액 확인
→ 차감
→ Commit

요청 B
→ Lock 대기
→ 요청 A Commit 이후 잔액 확인
→ 잔액 부족이면 실패
```

---

## 4. 잠금 순서

주문 처리에서는 여러 데이터를 잠근다.

Deadlock 가능성을 줄이기 위해 잠금 순서를 고정한다.

```text
장바구니
→ 메뉴 ID 오름차순
→ 포인트
```

이 순서는 `docs/ARCHITECTURE.md`와 `AGENTS.md`의 구현 지침을 따른다.

---

## 5. 구현 흐름

```text
OrderTransactionService
→ 장바구니 쓰기 잠금 조회
→ 메뉴 읽기 잠금 조회
→ 포인트 쓰기 잠금 조회
→ 잔액 검증
→ 포인트 차감
→ 주문 저장
→ 결제 저장
→ 포인트 이력 저장
→ Commit
```

포인트 충전도 같은 포인트 행을 잠근 뒤 증가시킨다.

이렇게 하면 충전과 주문이 동시에 발생해도 최종 잔액이 일관된다.

---

## 6. 핵심 코드 위치

| 코드 | 역할 |
| --- | --- |
| `src/main/java/com/example/coffeeorder/point/repository/PointRepository.java` | 포인트 비관적 쓰기 잠금 조회 |
| `src/main/java/com/example/coffeeorder/point/service/PointService.java` | 포인트 충전 시 잠금 조회 |
| `src/main/java/com/example/coffeeorder/order/service/OrderTransactionService.java` | 주문 시 포인트 잠금 후 차감 |
| `src/main/java/com/example/coffeeorder/point/entity/Point.java` | 잔액 검증과 차감 도메인 규칙 |

---

## 7. 테스트로 검증한 내용

| 테스트 | 검증 내용 |
| --- | --- |
| 포인트 동시 충전 | 여러 충전 요청 후 최종 잔액과 이력 수가 정확함 |
| 서로 다른 멱등키 동시 주문 | 성공 주문 수, 실패 주문 수, 잔액, 이력 정합성 검증 |
| 포인트 부족 주문 | 주문과 결제, 포인트 이력, 장바구니 삭제가 Rollback됨 |

관련 테스트:

```text
PointControllerIntegrationTest
OrderControllerIntegrationTest
```

---

## 8. 대안 비교

| 방식 | 장점 | 단점 |
| --- | --- | --- |
| 비관적 락 | 구현이 명확하고 초과 차감을 확실히 방지 | 같은 회원 요청은 순차 처리됨 |
| 낙관적 락 | 충돌이 적으면 성능이 좋음 | 충돌 시 재시도 설계 필요 |
| 조건부 UPDATE | 락 대기 감소 가능 | 주문 저장과 함께 정합성 설계가 더 복잡 |
| Redis 분산 락 | 애플리케이션 단위 제어 가능 | DB 정합성의 최종 방어선은 아님 |

현재 프로젝트에서는 정확성이 중요하므로 비관적 락을 우선 선택했다.

---

## 실제 적용 코드

### 적용 파일 경로

| 파일 | 역할 |
| --- | --- |
| `src/main/java/com/example/coffeeorder/order/service/OrderTransactionService.java` | 주문 트랜잭션 중 포인트 잠금 획득과 차감 |
| `src/main/java/com/example/coffeeorder/point/repository/PointRepository.java` | 회원 포인트 행 비관적 쓰기 잠금 |
| `src/main/java/com/example/coffeeorder/point/entity/Point.java` | 잔액 부족 검증과 차감 규칙 |

### 호출 흐름

```text
OrderTransactionService.createOrder()
→ 장바구니 잠금
→ 메뉴 ID 오름차순 잠금
→ PointRepository.findByMemberIdForUpdate()
→ Point.use(totalAmount)
→ 주문/결제/이력 저장
→ Commit
```

### 핵심 코드만 짧게 발췌

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("""
        select p
        from Point p
        join fetch p.member
        where p.member.id = :memberId
        """)
Optional<Point> findByMemberIdForUpdate(@Param("memberId") Long memberId);
```

```java
Point point = findPointForUpdate(memberId);
point.use(totalAmount);
```

```java
public void use(long amount) {
    if (balance < amount) {
        throw new BusinessException(ErrorCode.POINT_NOT_ENOUGH);
    }
    this.balance -= amount;
}
```

### 이 코드가 해당 개념을 구현하는 이유

동일 회원의 주문 요청은 같은 `points` 행을 잠그므로 한 요청이 Commit 또는 Rollback될 때까지 다음 요청이 잔액을 읽고 차감하지 못한다. 두 번째 요청은 첫 번째 요청의 차감 결과가 반영된 잔액을 기준으로 `Point.use()`를 실행하므로 포인트가 음수가 되는 중복 차감을 막을 수 있다.

---

## 9. 한계와 개선 방향

트래픽이 증가하면 같은 회원의 주문 요청은 포인트 행 하나에서 병목이 생길 수 있다.

후속 성능 테스트에서는 다음 전략을 비교할 수 있다.

```text
비관적 락
낙관적 락
조건부 UPDATE
Redis 분산 락
```

다만 어떤 전략을 쓰더라도 최종 잔액 정합성은 반드시 데이터베이스 기준으로 보장되어야 한다.
