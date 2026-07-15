# Coffee Order System 기술 학습 문서

이 문서는 커피 주문 및 포인트 결제 시스템을 구현하면서 학습할 만한 기술 전략을 정리한 Wiki 초안이다.

기본 CRUD보다 다음 주제에 집중한다.

| 문서 | 핵심 주제 |
| --- | --- |
| [주문 트랜잭션 경계와 Rollback 전략](ORDER_TRANSACTION_BOUNDARY.md) | 주문, 결제, 포인트, 장바구니 삭제를 하나의 트랜잭션으로 묶는 이유 |
| [주문 멱등성 처리 전략](ORDER_IDEMPOTENCY.md) | 재시도와 동시 요청에서도 중복 주문을 막는 방법 |
| [포인트 동시성 제어 전략](POINT_CONCURRENCY_CONTROL.md) | 같은 회원의 동시 주문에서 포인트 초과 차감을 막는 방법 |
| [Redis 기반 토큰 상태 관리](REDIS_TOKEN_STATE_MANAGEMENT.md) | Access Token Blacklist, Refresh Token Whitelist, Rotation 원자성 |
| [최근 7일 인기 메뉴 집계 전략](POPULAR_MENU_AGGREGATION.md) | 주문 수량이 아니라 주문별 메뉴 등장 횟수를 집계하는 방법 |
| [API 검증 오류 응답 설계](VALIDATION_ERROR_RESPONSE.md) | `data.errors[]` 구조로 검증 실패를 일관되게 반환하는 방법 |

---

## 문서 작성 기준

각 문서는 다음 형식을 따른다.

1. 문제 배경
2. 단순 구현의 한계
3. 선택한 전략
4. 구현 흐름
5. 핵심 코드 위치
6. 테스트로 검증한 내용
7. 한계와 개선 방향

---

## 아직 문서화하면 좋은 후속 주제

다음 기능이 구현되면 별도 문서로 추가한다.

| 예정 문서 | 연결 이슈 |
| --- | --- |
| 외부 주문 데이터 Mock API 동기 전송 전략 | #18 |
| Transactional Outbox 설계 | #19 |
| Kafka 기반 주문 이벤트 비동기 전송 전략 | #20 |
| 핵심 트랜잭션 및 외부 연동 통합 테스트 전략 | #21 |

---

## 기존 Wiki 문서

- [요구사항 분석 및 핵심 기능 정의](1.-요구사항-분석-및-핵심-기능-정의)
