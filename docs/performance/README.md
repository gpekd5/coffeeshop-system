# 성능 테스트 학습 가이드

이 디렉터리는 주문 완료 이벤트 전송 방식을 비교하기 위한 성능 테스트 문서를 모아둔다.

이번 프로젝트의 성능 테스트는 단순히 "몇 ms가 빠른가"를 보는 목적이 아니다.
외부 데이터 수집 플랫폼이 느려질 때, 그 지연이 사용자 주문 응답에 포함되는 구조와
분리되는 구조의 차이를 직접 측정하는 데 초점을 둔다.

---

## 1. 학습 목표

| 질문 | 확인 방법 |
| --- | --- |
| 외부 API 지연이 주문 API 응답시간에 얼마나 반영되는가? | `sync` 모드에서 Mock API `delayMillis`를 늘리며 p95를 비교한다. |
| Outbox/Kafka 구조가 주문 자체를 빠르게 만드는가? | 아니다. 외부 API 호출을 주문 응답 경로 밖으로 분리하는 효과를 본다. |
| 비동기 구조에서는 어떤 지표를 함께 봐야 하는가? | 주문 API p95와 함께 Outbox 적체, Kafka Consumer 처리량, Dead Letter 수를 본다. |
| 결과를 어떻게 남겨야 다시 비교할 수 있는가? | k6 JSON을 CSV로 누적하고 SVG 그래프로 변환한다. |

---

## 2. 문서 읽는 순서

| 순서 | 문서 | 목적 |
| ---: | --- | --- |
| 1 | [성능 비교 실행 절차](./ORDER_EVENT_DELIVERY_PERFORMANCE_TEST.md) | 로컬 인프라, 애플리케이션 모드, k6 실행, CSV/SVG 생성 순서 |
| 2 | [성능 테스트 결과](./ORDER_EVENT_DELIVERY_PERFORMANCE_RESULT_2026-07-20.md) | 실제 로컬 측정 환경, 결과 표, 그래프, 해석 |
| 3 | [결과 CSV](./order-event-delivery-results.csv) | 그래프의 원본 데이터 |
| 4 | [결과 그래프](./order-event-delivery-comparison.svg) | `sync`와 `outbox` p95 비교 시각화 |

관련 설계 배경은 [외부 API 장애 처리와 Mock API 전략](../wiki/EXTERNAL_ORDER_EVENT_AND_MOCK_API.md),
[Transactional Outbox 설계](../wiki/TRANSACTIONAL_OUTBOX.md),
[Kafka Consumer 멱등성 전략](../wiki/KAFKA_CONSUMER_IDEMPOTENCY.md)에서 확인한다.

---

## 3. 실행 흐름 한눈에 보기

```text
1. Docker Compose로 MySQL, Redis, Kafka 실행
2. 애플리케이션을 sync 또는 outbox 모드로 실행
3. k6로 주문 API 부하 실행
4. k6 summary JSON 저장
5. JSON에서 주문 API 지표만 CSV에 누적
6. CSV를 SVG 그래프로 변환
7. sync와 outbox p95, 처리량, 오류율을 비교
```

중요한 점은 같은 장비, 같은 k6 조건, 같은 Mock API 지연 조건에서
전송 방식만 바꾸어 비교해야 한다는 것이다.

---

## 4. 측정 대상

| 구분 | 포함 | 제외 |
| --- | --- | --- |
| 주문 API 응답시간 | `POST /api/v1/orders` 처리 시간 | 회원가입, 로그인, 포인트 충전, 장바구니 준비 |
| `sync` 모드 | 주문 DB Commit 후 외부 Mock API 동기 호출 시간 | Kafka Publisher/Consumer 처리 |
| `outbox` 모드 | 주문 트랜잭션과 Outbox 저장 시간 | Consumer가 외부 Mock API를 호출하는 시간 |

k6 스크립트는 테스트 준비를 위해 회원가입, 로그인, 포인트 충전, 장바구니 담기를 수행하지만,
그래프에 사용하는 핵심 지표는 주문 요청 전용 `order_api_duration`이다.

---

## 5. 결과 산출물

| 파일 | 생성 주체 | 설명 |
| --- | --- | --- |
| `build/performance/*.json` | k6 | `--summary-export`로 저장한 원본 실행 결과 |
| `docs/performance/order-event-delivery-results.csv` | `record_k6_order_event_result.py` | 모드, 지연, VU, 평균, p95, p99, 오류율 누적 |
| `docs/performance/order-event-delivery-comparison.svg` | `plot_order_event_delivery.py` | p95 비교 그래프 |
| `docs/performance/ORDER_EVENT_DELIVERY_PERFORMANCE_RESULT_*.md` | 수동 문서화 | 테스트 환경, 조건, 결과 해석 기록 |

CSV에는 다음 컬럼을 기록한다.

```text
recordedAt,mode,externalDelayMillis,vus,duration,requests,rps,avgMs,p95Ms,p99Ms,errorRate
```

---

## 6. 해석 기준

| 관찰 결과 | 의미 |
| --- | --- |
| `sync` p95가 `delayMillis`와 함께 증가 | 주문 응답이 외부 API 지연을 기다린다. |
| `outbox` p95가 `delayMillis` 변화에 둔감 | 주문 응답과 외부 이벤트 처리가 분리됐다. |
| `outbox` 처리량은 높지만 Outbox PENDING이 증가 | 주문 유입보다 Publisher 또는 Consumer 처리량이 부족하다. |
| Dead Letter 수 증가 | Consumer가 최종 실패 이벤트를 DLT로 보냈으므로 원인 확인과 재처리가 필요하다. |
| 오류율 증가 | 성능 결과보다 먼저 주문 정합성, Timeout, 데이터 준비 실패를 확인해야 한다. |

`outbox`가 항상 모든 지표에서 더 빠르다는 의미로 해석하면 안 된다.
비동기 구조는 사용자 응답 경로를 짧게 만드는 대신 Kafka, Consumer, Outbox 적체를 운영 지표로 함께 관리해야 한다.

---

## 7. 결과 기록 체크리스트

성능 테스트 결과 문서에는 최소한 다음 항목을 남긴다.

- 테스트 일시와 시간대
- OS, CPU, RAM
- Java, Gradle, Docker, MySQL, Redis, Kafka 버전
- 애플리케이션 인스턴스 수
- k6 VU, 사용자 수, 지속 시간
- Mock API 지연 조건
- 실행 모드별 환경변수
- 평균, p95, p99, 처리량, 오류율
- 그래프 이미지
- 결과 해석과 제한 사항

같은 수치라도 노트북 상태, Docker 리소스, 백그라운드 작업에 따라 달라질 수 있으므로
로컬 결과는 운영 절대 성능이 아니라 같은 조건 안에서의 상대 비교로 해석한다.

---

## 8. 다음에 확장할 수 있는 테스트

| 확장 방향 | 확인하고 싶은 것 |
| --- | --- |
| VU 단계 증가 | 어느 지점에서 DB, Kafka, 애플리케이션 Thread가 병목이 되는지 |
| Mock API 실패율 추가 | 외부 장애가 `sync` 주문 성공률과 `outbox` 이벤트 처리 상태에 미치는 차이 |
| Consumer Lag 수집 | 주문 API 응답은 빠르지만 후속 이벤트 처리가 밀리는지 |
| 다중 인스턴스 실행 | 여러 애플리케이션 인스턴스에서도 주문 정합성과 토큰 상태가 유지되는지 |
| Grafana 대시보드 | p95, Outbox 적체, Dead Letter, Consumer 처리량을 한 화면에서 관찰 |
