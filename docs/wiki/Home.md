# Coffee Order System 기술 학습 문서

이 문서는 커피 주문 및 포인트 결제 시스템을 구현하면서 학습할 만한 기술 전략을 정리한 Wiki 초안이다.

기본 CRUD보다 다음 주제에 집중한다.

| 문서 | 핵심 주제 |
| --- | --- |
| [주문 트랜잭션 경계와 Rollback 전략](ORDER_TRANSACTION_BOUNDARY.md) | 주문, 결제, 포인트, 장바구니 삭제를 하나의 트랜잭션으로 묶는 이유 |
| [주문 멱등성 처리 전략](ORDER_IDEMPOTENCY.md) | 재시도와 동시 요청에서도 중복 주문을 막는 방법 |
| [포인트 동시성 제어 전략](POINT_CONCURRENCY_CONTROL.md) | 같은 회원의 동시 주문에서 포인트 초과 차감을 막는 방법 |
| [Redis 기반 토큰 상태 관리](REDIS_TOKEN_STATE_MANAGEMENT.md) | Access Token Blacklist, Refresh Token Whitelist, Rotation 원자성 |
| [최근 7일 인기 메뉴 DB 집계와 캐시 확장 전략](POPULAR_MENU_AGGREGATION.md) | 현재 DB 집계 기준과 향후 Redis 캐시 확장 기준 |
| [외부 API 장애 처리와 Mock API 전략](EXTERNAL_ORDER_EVENT_AND_MOCK_API.md) | 외부 시스템 실패가 완료 주문을 Rollback하지 않게 분리하는 방법 |
| [Transactional Outbox 설계](TRANSACTIONAL_OUTBOX.md) | 주문과 이벤트 저장을 같은 트랜잭션으로 묶고 Kafka 발행을 분리하는 방법 |
| [Kafka Consumer 멱등성 전략](KAFKA_CONSUMER_IDEMPOTENCY.md) | 중복 이벤트와 Consumer 중단 상황에서 외부 API 중복 호출을 막는 방법 |
| [Outbox/Kafka 운영 조회와 모니터링](EVENT_OPERATIONS_MONITORING.md) | 관리자 API와 Prometheus 메트릭으로 이벤트 처리 상태를 관찰하는 방법 |
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

각 기술 문서에는 실제 구현을 바로 따라갈 수 있도록 다음 항목도 함께 정리한다.

- 적용 파일 경로
- 호출 흐름
- 핵심 코드 발췌
- 해당 코드가 개념을 구현하는 이유

---

## 기존 Wiki 문서

- [요구사항 분석 및 핵심 기능 정의](1.-요구사항-분석-및-핵심-기능-정의)

---

## 함께 보면 좋은 운영 문서

- [트러블슈팅 인덱스](../troubleshooting/README.md)
