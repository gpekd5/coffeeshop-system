# Coffee Order System 트러블슈팅

이 디렉터리는 구현 중 만날 수 있는 장애 상황을 증상 기준으로 정리한다.

설계 의도와 선택 배경은 `docs/wiki` 문서를 보고, 실제 문제가 생겼을 때 어디를 확인해야 하는지는 이 문서를 본다.

---

## 문서 목록

| 문서 | 증상 |
| --- | --- |
| [Redis 토큰 상태 문제](TROUBLESHOOTING_AUTH_001_REDIS_TOKEN_STATE.md) | 로그아웃했는데 Access Token이 계속 유효하거나 Refresh Token 재발급이 실패함 |
| [Mock API 프로필 노출 문제](TROUBLESHOOTING_EVENT_001_MOCK_API_PROFILE_EXPOSURE.md) | 운영 환경에서 Mock API가 노출되거나 local/mock URL로 이벤트를 전송함 |
| [외부 주문 이벤트 전송 실패](TROUBLESHOOTING_EVENT_002_EXTERNAL_ORDER_EVENT_FAILURE.md) | 주문은 완료됐지만 외부 데이터 수집 플랫폼 전송이 실패하거나 Timeout 됨 |
| [Outbox/Kafka 발행 적체](TROUBLESHOOTING_EVENT_003_OUTBOX_KAFKA_STUCK.md) | `outbox_events`가 `PENDING` 또는 `FAILED` 상태에 오래 머무름 |
| [Kafka Consumer 중복 처리와 Dead Letter](TROUBLESHOOTING_EVENT_004_KAFKA_CONSUMER_DUPLICATE_OR_DLT.md) | 같은 이벤트가 중복 소비되거나 Dead Letter 이벤트가 증가함 |

---

## 작성 기준

각 문서는 다음 순서를 따른다.

1. 증상
2. 영향
3. 먼저 확인할 것
4. 원인별 확인 방법
5. 복구 또는 대응
6. 관련 코드와 테스트

---

## 관련 기술 문서

| 문서 | 설명 |
| --- | --- |
| [Redis 기반 토큰 상태 관리](../wiki/REDIS_TOKEN_STATE_MANAGEMENT.md) | Access Token Blacklist, Refresh Token Whitelist, Rotation/Logout 원자성 |
| [외부 API 장애 처리와 Mock API 전략](../wiki/EXTERNAL_ORDER_EVENT_AND_MOCK_API.md) | 외부 API 실패를 주문 성공 여부와 분리하는 이유 |
| [Transactional Outbox 설계](../wiki/TRANSACTIONAL_OUTBOX.md) | 주문 DB Commit과 Kafka 발행 사이의 유실 방지 |
| [Kafka Consumer 멱등성 전략](../wiki/KAFKA_CONSUMER_IDEMPOTENCY.md) | 중복 이벤트와 Consumer 중단 대응 |
| [Outbox/Kafka 운영 조회와 모니터링](../wiki/EVENT_OPERATIONS_MONITORING.md) | 관리자 API와 Prometheus 메트릭 확인 방법 |
