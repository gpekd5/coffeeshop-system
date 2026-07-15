# 최근 7일 인기 메뉴 집계 전략

## 1. 문제 배경

인기 메뉴 API는 최근 7일 동안 가장 많이 주문된 메뉴 상위 3개를 반환한다.

여기서 중요한 점은 "많이 주문된"의 기준이다.

이 프로젝트에서는 주문 수량 합계가 아니라 주문별 메뉴 등장 횟수를 사용한다.

```text
주문 A: 아메리카노 5개

아메리카노 주문 횟수 = 1회
아메리카노 주문 수량 = 5개
```

인기 메뉴는 주문 횟수를 기준으로 하므로 위 주문은 1회만 집계된다.

---

## 2. 집계 정책

문서 기준 정책은 다음과 같다.

| 정책 | 내용 |
| --- | --- |
| 집계 기간 | 조회 시점 기준 최근 7일 |
| 집계 원본 | 내부 주문 DB |
| 주문 상태 | `COMPLETED` 주문만 포함 |
| 제외 대상 | 취소 주문, 실패 주문, 기간 밖 주문 |
| 인기 기준 | 메뉴가 포함된 주문 수 |
| 반환 개수 | 상위 3개 |
| 정렬 1순위 | 주문 횟수 내림차순 |
| 정렬 2순위 | 최근 주문 시각 내림차순 |
| 정렬 3순위 | 메뉴 ID 오름차순 |

외부 데이터 수집 플랫폼 전송 결과는 인기 메뉴 집계에 영향을 주지 않는다.

---

## 3. 단순 구현의 함정

가장 쉬운 실수는 `quantity`를 더하는 것이다.

```sql
sum(order_item.quantity)
```

하지만 이 방식은 정책과 다르다.

또 다른 실수는 주문 항목 행을 그대로 세는 것이다.

```sql
count(order_item.id)
```

현재 구조에서는 하나의 주문에 같은 메뉴가 중복 행으로 들어가지 않지만, 비즈니스 정책상 더 안전한 표현은 주문 ID를 기준으로 distinct count를 하는 것이다.

---

## 4. 선택한 전략

집계 쿼리는 `count(distinct o.id)`를 사용한다.

```sql
count(distinct o.id)
```

이렇게 하면 하나의 주문에 특정 메뉴가 포함되었는지를 기준으로 1회만 계산할 수 있다.

또한 메뉴가 삭제되거나 판매 중지되어도 최근 7일 완료 주문에 포함됐다면 집계 대상이어야 하므로, 메뉴 삭제 여부로 필터링하지 않는다.

---

## 5. 구현 흐름

```text
GET /api/v1/menus/popular
→ MenuController
→ MenuService
→ 현재 시각 계산
→ 시작 시각 = 현재 시각 - 7일
→ OrderItemRepository 집계 쿼리
→ 정렬된 Top 3 조회
→ Service에서 rank 부여
→ PopularMenuResponse 반환
```

현재 시각은 `Clock`을 주입받아 계산한다.

이렇게 하면 테스트에서 고정된 시간을 사용할 수 있어 기간 경계 테스트가 안정적이다.

---

## 6. 핵심 코드 위치

| 코드 | 역할 |
| --- | --- |
| `src/main/java/com/example/coffeeorder/menu/controller/MenuController.java` | `/api/v1/menus/popular` API |
| `src/main/java/com/example/coffeeorder/menu/service/MenuService.java` | 최근 7일 범위 계산, rank 부여 |
| `src/main/java/com/example/coffeeorder/order/repository/OrderItemRepository.java` | 인기 메뉴 집계 쿼리 |
| `src/main/java/com/example/coffeeorder/order/repository/projection/PopularMenuAggregation.java` | 집계 쿼리 결과 |
| `src/main/java/com/example/coffeeorder/menu/dto/response/PopularMenuResponse.java` | API 응답 DTO |

---

## 7. 쿼리 핵심

```java
@Query("""
        select new com.example.coffeeorder.order.repository.projection.PopularMenuAggregation(
            m.id,
            m.name,
            m.category,
            m.price,
            m.status,
            count(distinct o.id),
            max(o.orderedAt)
        )
        from OrderItem oi
        join oi.order o
        join Menu m on m.id = oi.menuId
        where o.status = :status
          and o.orderedAt >= :startDateTime
          and o.orderedAt <= :endDateTime
        group by m.id, m.name, m.category, m.price, m.status
        order by count(distinct o.id) desc, max(o.orderedAt) desc, m.id asc
        """)
```

핵심은 세 가지다.

| 코드 | 의미 |
| --- | --- |
| `o.status = COMPLETED` | 완료 주문만 포함 |
| `o.orderedAt >= start` and `<= end` | 최근 7일 경계 포함 |
| `count(distinct o.id)` | 수량이 아닌 주문별 등장 횟수 |

---

## 8. 테스트로 검증한 내용

`PopularMenuControllerIntegrationTest`에서 다음을 검증한다.

| 테스트 | 검증 내용 |
| --- | --- |
| 최근 7일 완료 주문 집계 | 경계 포함, 기간 밖 제외 |
| 주문별 등장 횟수 | 한 주문에서 수량이 5개여도 1회 |
| 취소 주문 제외 | `CANCELLED` 주문은 집계하지 않음 |
| 미래 주문 제외 | 조회 시점 이후 주문은 제외 |
| 동률 정렬 | 주문 횟수, 최근 주문 시각, 메뉴 ID 순서 |
| 삭제 메뉴 포함 | 삭제된 메뉴도 최근 완료 주문이면 반환 |
| 빈 결과 | 대상이 없으면 `data: []` 반환 |

---

## 9. 한계와 개선 방향

현재는 API 요청마다 DB 집계 쿼리를 실행한다.

초기 구현에서는 정확성을 우선하므로 이 방식이 적절하다.

트래픽이 증가하면 다음 방식을 검토할 수 있다.

| 개선 방향 | 설명 |
| --- | --- |
| Redis 캐시 | 짧은 TTL로 인기 메뉴 결과 캐싱 |
| 집계 테이블 | 주문 완료 시 집계 데이터 갱신 |
| Kafka Consumer 기반 집계 | 주문 완료 이벤트를 비동기로 소비해 별도 저장소에 반영 |

다만 캐시나 별도 집계 저장소를 도입해도 원본 주문 데이터와의 일관성 검증은 필요하다.
