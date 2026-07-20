# Outbox/Kafka 운영 조회와 모니터링

## 1. 문제 배경

Outbox와 Kafka를 도입하면 주문 API의 응답과 외부 이벤트 처리는 분리된다.

이 분리는 장애 격리에 유리하지만, 운영자는 다음 상태를 별도로 관찰해야 한다.

```text
Outbox 이벤트가 쌓이고 있는가?
Kafka 발행 실패가 반복되는가?
Consumer 처리 실패가 증가하는가?
Dead Letter Topic으로 이동한 이벤트가 있는가?
```

이 상태를 확인할 수 없으면 주문은 성공했지만 외부 데이터 수집 플랫폼에는 반영되지 않는 상황을 놓칠 수 있다.

---

## 2. 단순 구현의 한계

로그만 남기는 방식은 다음 한계가 있다.

| 방식 | 한계 |
| --- | --- |
| 애플리케이션 로그 | 특정 이벤트를 찾기 어렵고 보관 기간이 제한됨 |
| DB 직접 조회 | 운영자가 테이블 구조를 알아야 함 |
| Kafka DLT만 확인 | 주문 시스템의 처리 이력과 연결하기 어려움 |
| 메트릭 없음 | Grafana 알림과 대시보드 구성이 어려움 |

따라서 관리자 조회 API와 Prometheus 메트릭을 함께 제공한다.

---

## 3. 선택한 전략

운영 조회는 두 층으로 나눈다.

| 층 | 목적 |
| --- | --- |
| 관리자 API | 특정 이벤트의 상태, 오류, 재처리 대상 확인 |
| Actuator/Prometheus 메트릭 | 전체 적체량, 실패 증가, Dead Letter 발생 관찰 |

관리자 API는 인증된 관리자만 호출할 수 있다.
Prometheus endpoint는 수집을 위해 인증 없이 열어두되, 운영 환경에서는 네트워크 접근 제어를 전제로 한다.

---

## 4. 구현 흐름

### 관리자 조회

```text
관리자 요청
→ Security 권한 확인
→ AdminOutboxEventController
→ OutboxEventQueryService
→ PageResponse 반환
```

Kafka Consumer 처리 이력과 Dead Letter도 같은 패턴으로 조회한다.

```text
GET /api/v1/admin/outbox-events
POST /api/v1/admin/outbox-events/{eventId}/retry
GET /api/v1/admin/processed-kafka-events
GET /api/v1/admin/dead-letter-order-events
GET /api/v1/admin/dead-letter-order-events/{deadLetterEventId}
```

### 메트릭

```text
EventMonitoringMeterBinder
→ Outbox 상태별 Gauge 등록
→ 가장 오래된 PENDING 이벤트 age 등록
→ Dead Letter 이벤트 수 등록

KafkaConsumerMetricsRecorder
→ success/failure/duplicate_skip Counter 기록

ExternalOrderEventMetricsRecorder
→ success/failure/timeout Counter 기록
```

---

## 5. 핵심 코드 위치

| 코드 | 역할 |
| --- | --- |
| `src/main/java/com/example/coffeeorder/event/outbox/controller/AdminOutboxEventController.java` | Outbox 목록 조회와 수동 재처리 |
| `src/main/java/com/example/coffeeorder/event/outbox/service/OutboxEventQueryService.java` | Outbox 조회 조건, 정렬, 페이징 |
| `src/main/java/com/example/coffeeorder/event/kafka/consumer/controller/AdminProcessedKafkaEventController.java` | Kafka 처리 이력 조회 |
| `src/main/java/com/example/coffeeorder/event/kafka/consumer/controller/AdminDeadLetterOrderEventController.java` | Dead Letter 목록/상세 조회 |
| `src/main/java/com/example/coffeeorder/event/metrics/EventMonitoringMeterBinder.java` | Outbox/Dead Letter Gauge |
| `src/main/java/com/example/coffeeorder/event/metrics/KafkaConsumerMetricsRecorder.java` | Kafka Consumer Counter |
| `src/main/java/com/example/coffeeorder/event/metrics/ExternalOrderEventMetricsRecorder.java` | 외부 API 호출 Counter |
| `src/main/java/com/example/coffeeorder/common/security/SecurityConfig.java` | 관리자 API 권한과 Prometheus 공개 경로 |

---

## 6. 테스트로 검증한 내용

| 테스트 | 검증 내용 |
| --- | --- |
| `AdminOutboxEventControllerIntegrationTest` | 관리자 Outbox 목록 조회, 상태 필터, 수동 재처리 |
| `AdminProcessedKafkaEventControllerIntegrationTest` | Kafka 처리 이력 조회, 상태 필터, 관리자 권한 |
| `AdminDeadLetterOrderEventControllerIntegrationTest` | Dead Letter 목록/상세 조회, 관리자 권한 |
| `EventMonitoringMeterBinderIntegrationTest` | Outbox 상태별 Gauge, 오래된 Pending age, Dead Letter 수 |
| `ExternalOrderEventMetricsRecorderTest` | 외부 API success/failure/timeout Counter |
| `PrometheusEndpointIntegrationTest` | `/actuator/prometheus` 메트릭 노출 |

---

## 7. 한계와 개선 방향

현재 애플리케이션은 Prometheus가 수집할 수 있는 지표와 관리자 조회 API를 제공한다.

다만 Grafana Dashboard, Alert Rule, Kafka Broker Lag 수집 설정은 저장소에 포함하지 않았다.

후속 개선 후보는 다음과 같다.

| 개선 | 설명 |
| --- | --- |
| Grafana Dashboard | Outbox 적체, Consumer 실패율, Dead Letter 수 시각화 |
| Alert Rule | 오래된 PENDING 이벤트, FAILED 증가, Dead Letter 발생 알림 |
| Kafka Lag 지표 | Consumer Group Lag를 Kafka Exporter로 수집 |
| 운영 Runbook | 실패 이벤트 재처리와 DLT 조사 절차 문서화 |
