# 주문 이벤트 전송 방식 성능 비교

이 문서는 주문 완료 이벤트 전송 방식을 `sync`와 `outbox`로 바꾸며
외부 Mock API 지연이 주문 API 응답시간에 미치는 영향을 측정하는 절차를 정리한다.

비교의 목적은 Kafka가 주문 자체를 빠르게 만든다는 주장이 아니라,
외부 시스템 지연을 사용자 주문 응답 경로에서 분리하는 효과를 수치로 확인하는 것이다.

---

## 1. 비교 대상

| 모드 | 처리 흐름 | 주문 응답시간에 포함되는 작업 |
| --- | --- | --- |
| `sync` | 주문 DB Commit → 외부 Mock API 동기 호출 → 응답 | DB 처리 시간 + 외부 API 응답시간 |
| `outbox` | 주문 트랜잭션에서 Outbox 저장 → 응답 → Kafka Publisher/Consumer 처리 | DB 처리 시간 + Outbox 저장 시간 |

`sync` 모드는 성능 비교용 로컬 실험 모드다.
운영 기본 흐름은 `outbox`이며, Outbox/Kafka 구조에서 외부 API 호출은 Consumer 단계에서 수행된다.

---

## 2. 테스트 환경 기록

성능 테스트 결과를 문서에 남길 때는 아래 값을 함께 기록한다.
단일 로컬 머신 결과는 운영 절대 성능이 아니라 같은 장비 안에서의 상대 비교로 해석한다.

| 항목 | 값 |
| --- | --- |
| 테스트 일시 | 예: 2026-07-20 13:00 KST |
| OS | 예: Windows 11 |
| CPU | 예: Intel/AMD 모델명, 코어 수 |
| RAM | 예: 32GB |
| Java | 21 |
| Gradle | Wrapper |
| Docker Desktop | 버전 |
| MySQL | Docker MySQL 8.4 |
| Redis | Docker Redis 7.4 |
| Kafka | Docker Kafka 3.7 |
| 테스트 도구 | k6 |
| 애플리케이션 인스턴스 | 1 |
| DB 초기 데이터 | `docs/http/00-local-demo-data.sql` |

---

## 3. 로컬 인프라 실행

```powershell
docker compose up -d
```

Compose v1 환경에서는 다음 명령을 사용한다.

```powershell
docker-compose up -d
```

애플리케이션 실행 전에 `docs/http/00-local-demo-data.sql`로 기본 메뉴를 준비한다.
k6 스크립트의 기본 `MENU_ID`는 `1`이므로, 다른 메뉴 ID를 사용할 경우 `-e MENU_ID=...`로 지정한다.

---

## 4. 애플리케이션 실행 모드

### 4.1 sync 모드

외부 Mock API 지연이 주문 API 응답시간에 직접 반영되는 기준선이다.
외부 지연은 `APP_EXTERNAL_ORDER_EVENT_PATH`의 Query Parameter로 조절한다.

```powershell
$env:APP_ORDER_EVENT_DELIVERY_MODE = "sync"
$env:APP_EXTERNAL_ORDER_EVENT_ENABLED = "true"
$env:APP_EXTERNAL_ORDER_EVENT_BASE_URL = "http://localhost:8080"
$env:APP_EXTERNAL_ORDER_EVENT_PATH = "/mock/v1/order-events?delayMillis=300"
$env:APP_KAFKA_ORDER_EVENT_PUBLISHER_ENABLED = "false"
$env:APP_KAFKA_ORDER_EVENT_CONSUMER_ENABLED = "false"
.\gradlew.bat bootRun
```

### 4.2 outbox 모드

주문 API는 Outbox 저장 후 응답하고, 외부 Mock API 호출은 Kafka Consumer가 비동기로 수행한다.
Publisher 지연을 줄여 로컬 실험을 빠르게 돌리려면 fixed delay를 낮춘다.

```powershell
$env:APP_ORDER_EVENT_DELIVERY_MODE = "outbox"
$env:APP_EXTERNAL_ORDER_EVENT_ENABLED = "true"
$env:APP_EXTERNAL_ORDER_EVENT_BASE_URL = "http://localhost:8080"
$env:APP_EXTERNAL_ORDER_EVENT_PATH = "/mock/v1/order-events?delayMillis=300"
$env:SPRING_KAFKA_BOOTSTRAP_SERVERS = "localhost:9092"
$env:APP_KAFKA_ORDER_EVENT_PUBLISHER_ENABLED = "true"
$env:APP_KAFKA_ORDER_EVENT_CONSUMER_ENABLED = "true"
$env:APP_KAFKA_ORDER_EVENT_PUBLISHER_FIXED_DELAY_MILLIS = "500"
.\gradlew.bat bootRun
```

---

## 5. k6 실행

각 모드와 지연 조건별로 애플리케이션을 다시 실행한 뒤 같은 k6 조건을 적용한다.

```powershell
New-Item -ItemType Directory -Force build/performance

k6 run `
  -e BASE_URL=http://localhost:8080 `
  -e VUS=10 `
  -e USER_COUNT=10 `
  -e DURATION=1m `
  -e MENU_ID=1 `
  --summary-export build/performance/sync-300.json `
  performance/k6/order-event-delivery-comparison.js
```

권장 시나리오:

| 모드 | 외부 지연 |
| --- | --- |
| `sync` | `0ms`, `300ms`, `1000ms` |
| `outbox` | `0ms`, `300ms`, `1000ms` |

k6 스크립트는 회원가입, 로그인, 포인트 충전, 장바구니 준비를 수행한다.
그래프에 사용하는 커스텀 지표는 `POST /api/v1/orders` 요청만 기록한 `order_api_duration`이다.

---

## 6. 결과 CSV 기록

k6 `--summary-export` JSON을 CSV에 누적한다.

```powershell
python scripts/performance/record_k6_order_event_result.py `
  --input build/performance/sync-300.json `
  --mode sync `
  --delay 300 `
  --vus 10 `
  --duration 1m
```

같은 방식으로 `sync-0`, `sync-1000`, `outbox-0`, `outbox-300`, `outbox-1000` 결과를 기록한다.
기본 출력 파일은 `docs/performance/order-event-delivery-results.csv`다.

CSV 컬럼:

```text
recordedAt,mode,externalDelayMillis,vus,duration,requests,rps,avgMs,p95Ms,p99Ms,errorRate
```

---

## 7. 그래프 생성

CSV가 준비되면 다음 명령으로 p95 응답시간 비교 그래프를 생성한다.

```powershell
python scripts/performance/plot_order_event_delivery.py `
  --input docs/performance/order-event-delivery-results.csv `
  --output docs/performance/order-event-delivery-comparison.svg
```

그래프 해석 기준:

| 기대 결과 | 설명 |
| --- | --- |
| `sync` p95가 외부 지연에 비례해 증가 | 주문 응답이 외부 API를 기다림 |
| `outbox` p95가 상대적으로 완만함 | 주문 응답과 외부 이벤트 처리가 분리됨 |
| `outbox`에서 Outbox PENDING 증가 가능 | Consumer 처리량보다 주문 유입이 빠르면 적체가 생김 |

`outbox` 결과를 볼 때는 주문 API 응답시간과 함께 `/actuator/prometheus`의
Outbox, Kafka Consumer, Dead Letter 지표를 함께 확인한다.

---

## 8. 제외한 검증

이번 성능 테스트 환경은 단일 애플리케이션 인스턴스에서 전송 방식별 응답시간 차이를 비교한다.

다음 항목은 별도 안정성 검증 이슈로 분리한다.

- 다중 애플리케이션 인스턴스 정합성 검증
- Nginx 또는 로드밸런서 구성
- Kafka Consumer Lag 운영 수집
- 운영 수준 Kafka Producer/Consumer 튜닝
