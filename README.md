# Coffee Order System

커피 메뉴를 조회하고, 포인트를 충전한 뒤, 보유 포인트로 주문할 수 있는
Spring Boot 기반 커피 주문 시스템입니다.

단순 CRUD보다 주문과 결제 과정에서 발생할 수 있는 정합성 문제를 다루는 데 초점을 둡니다.
동일 회원의 동시 주문, 주문 재시도, 외부 데이터 수집 플랫폼 장애, Kafka 이벤트 중복 소비처럼
실제 서비스에서 자주 만나는 문제를 작은 도메인 안에서 단계적으로 구현했습니다.

---

## 프로젝트 개요

사용자는 다음 흐름으로 서비스를 이용합니다.

```text
회원가입/로그인
→ 메뉴 조회
→ 장바구니 담기
→ 포인트 충전
→ 포인트 결제 주문
→ 주문 완료 이벤트 전송
→ 최근 7일 인기 메뉴 조회
```

관리자는 메뉴를 등록/수정/판매 중지할 수 있고, 주문 및 이벤트 처리 상태를 조회할 수 있습니다.

외부 데이터 수집 플랫폼은 주문 완료 이벤트를 전달받는 시스템으로 가정합니다.
초기에는 Mock API로 외부 장애와 지연을 재현하고, 이후 Transactional Outbox와 Kafka를 이용해
주문 처리와 외부 이벤트 처리를 분리합니다.

---

## 주요 기능

| 영역 | 기능 |
| --- | --- |
| 인증/회원 | 회원가입, 로그인, 로그아웃, JWT Access Token/Refresh Token 관리 |
| 메뉴 | 메뉴 목록/상세 조회, 관리자 메뉴 등록/수정/판매 상태 변경 |
| 장바구니 | 메뉴 추가, 목록 조회, 수량 변경, 항목 삭제, 전체 삭제 |
| 포인트 | 포인트 잔액 조회, 포인트 충전, 충전/사용 이력 |
| 주문/결제 | 단일 메뉴 주문, 장바구니 주문, 포인트 결제, 주문 내역 조회 |
| 인기 메뉴 | 최근 7일 완료 주문 기준 인기 메뉴 Top 3 조회 |
| 외부 연동 | 주문 완료 이벤트 Mock API 전송, 실패/Timeout 로그 기록 |
| 이벤트 고도화 | Transactional Outbox, Kafka 발행, Consumer 멱등성, Dead Letter 기록 |
| 운영 조회 | Outbox 이벤트, Kafka 처리 이력, Dead Letter 이벤트, Prometheus 메트릭 |

---

## 핵심 기술 포인트

| 주제 | 구현 전략 |
| --- | --- |
| 포인트 동시성 제어 | MySQL 비관적 쓰기 락으로 동일 회원 포인트 초과 사용 방지 |
| 주문 멱등성 | `Idempotency-Key` 선조회와 `(member_id, idempotency_key)` Unique 제약 병행 |
| 주문 트랜잭션 경계 | 주문, 결제, 포인트 차감, 포인트 이력, 장바구니 삭제, Outbox 저장을 하나의 DB 트랜잭션으로 처리 |
| Redis 토큰 상태 관리 | Access Token Blacklist, Refresh Token Whitelist, Lua Script 기반 Rotation/Logout 원자성 |
| 외부 API 장애 격리 | DB Commit 이후 외부 호출, 실패/Timeout은 로그로 남기고 주문은 유지 |
| Outbox + Kafka | 주문 완료 이벤트를 DB에 먼저 저장한 뒤 별도 Publisher가 Kafka로 발행 |
| Kafka Consumer 멱등성 | `processed_kafka_events`와 처리 lease로 중복 소비와 Consumer 중단 상황 대응 |
| 운영 모니터링 | Outbox 적체, Dead Letter 수, Consumer 처리 결과를 Actuator/Prometheus로 노출 |

상세 기술 문서는 [기술 학습 문서](./docs/wiki/Home.md)를 참고합니다.

---

## 프로그램 환경사항

### 필수

| 항목 | 버전/기준 | 용도 |
| --- | --- | --- |
| Java | 21 | 애플리케이션 실행 및 테스트 |
| Gradle | Wrapper 사용 | `./gradlew`, `.\gradlew.bat` |
| Docker | Docker Compose 지원 버전 | 로컬 MySQL, Redis 실행 |
| MySQL | 8.4 | 운영/로컬 데이터베이스 |
| Redis | 7.4 | 토큰 상태 저장 |

### 선택

| 항목 | 용도 |
| --- | --- |
| Kafka | Outbox 이벤트 발행 및 Consumer 처리 검증 |
| Prometheus/Grafana | `/actuator/prometheus` 메트릭 수집 및 시각화 |

로컬 `compose.yaml`에는 MySQL과 Redis만 포함되어 있습니다.
Kafka Publisher/Consumer는 기본 비활성화이며, Kafka를 직접 구성한 뒤 환경변수로 활성화합니다.

---

## 로컬 실행

MySQL과 Redis를 실행합니다.

```shell
docker compose up -d
```

Compose v1 환경에서는 다음 명령을 사용할 수 있습니다.

```shell
docker-compose up -d
```

애플리케이션은 기본적으로 `local` 프로필로 실행됩니다.

```shell
./gradlew bootRun
```

Windows PowerShell에서는 다음 명령을 사용할 수 있습니다.

```powershell
.\gradlew.bat bootRun
```

---

## 주요 환경변수

### Local

`local` 프로필은 학습과 개발 편의를 위해 기본값을 제공합니다.

| 환경변수 | 기본값 | 설명 |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://localhost:3306/coffee_order...` | MySQL 접속 URL |
| `SPRING_DATASOURCE_USERNAME` | `coffee` | MySQL 사용자 |
| `SPRING_DATASOURCE_PASSWORD` | `coffee` | MySQL 비밀번호 |
| `SPRING_DATA_REDIS_HOST` | `localhost` | Redis Host |
| `SPRING_DATA_REDIS_PORT` | `6379` | Redis Port |
| `APP_JWT_SECRET` | 로컬 개발용 기본값 | JWT 서명 키 |
| `APP_MOCK_ORDER_EVENT_ENABLED` | `true` | Mock API 활성화 |
| `APP_EXTERNAL_ORDER_EVENT_BASE_URL` | `http://localhost:8080` | 외부 이벤트 전송 기본 URL |
| `APP_EXTERNAL_ORDER_EVENT_PATH` | `/mock/v1/order-events` | 외부 이벤트 전송 기본 Path |

### Kafka

Kafka 기능은 명시적으로 켤 때만 동작합니다.

| 환경변수 | 기본값 | 설명 |
| --- | --- | --- |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka Bootstrap Servers |
| `APP_KAFKA_ORDER_EVENT_PUBLISHER_ENABLED` | `false` | Outbox Publisher 활성화 |
| `APP_KAFKA_ORDER_EVENT_CONSUMER_ENABLED` | `false` | Kafka Consumer 활성화 |
| `APP_KAFKA_ORDER_EVENT_TOPIC` | `order.completed` | 주문 완료 Topic |
| `APP_KAFKA_ORDER_EVENT_DLT` | `order.completed.DLT` | Dead Letter Topic |

### Prod

`prod` 프로필은 로컬 기본값을 운영에서 암묵적으로 사용하지 않도록 주요 값을 환경변수로 요구합니다.

| 환경변수 | 설명 |
| --- | --- |
| `SPRING_DATASOURCE_URL` | 운영 MySQL 접속 URL |
| `SPRING_DATASOURCE_USERNAME` | 운영 MySQL 사용자 |
| `SPRING_DATASOURCE_PASSWORD` | 운영 MySQL 비밀번호 |
| `SPRING_DATA_REDIS_HOST` | 운영 Redis Host |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | 운영 Kafka Bootstrap Servers |
| `APP_JWT_SECRET` | 운영 JWT Secret |
| `APP_EXTERNAL_ORDER_EVENT_BASE_URL` | 운영 외부 데이터 수집 플랫폼 URL |
| `APP_EXTERNAL_ORDER_EVENT_PATH` | 운영 외부 데이터 수집 API Path |

`APP_KAFKA_ORDER_EVENT_CONSUMER_ENABLED=true`로 Consumer를 활성화하면
외부 주문 이벤트 URL이 비어 있거나 localhost 또는 `/mock/v1/order-events`이면
애플리케이션 시작 단계에서 실패합니다.

---

## 프로필별 동작

| 프로필 | Mock API | Kafka Publisher | Kafka Consumer |
| --- | --- | --- | --- |
| `local` | 기본 활성화. `APP_MOCK_ORDER_EVENT_ENABLED=false`로 비활성화 | 기본 비활성화. 환경변수로 활성화 | 기본 비활성화. 환경변수로 활성화 |
| `test` | 테스트용으로 명시 활성화 | 비활성화 | 비활성화 |
| `prod` | 비활성화. 컨트롤러도 등록하지 않음 | 기본 비활성화. 환경변수로 명시 활성화 | 기본 비활성화. 환경변수로 명시 활성화 |

---

## 테스트

전체 테스트와 기본 빌드 검증은 다음 명령으로 실행합니다.

```shell
./gradlew clean check
```

Windows PowerShell:

```powershell
.\gradlew.bat clean check
```

테스트는 `test` 프로필을 기준으로 실행합니다.
일반 API 통합 테스트는 InMemory TokenStore를 사용해 외부 Redis 의존을 줄이고,
Redis 통합 테스트는 실제 Redis가 없으면 JUnit Assumption으로 건너뜁니다.

---

## 문서

| 문서 | 설명 |
| --- | --- |
| [프로젝트 개요](./docs/PROJECT_CONTEXT.md) | 프로젝트 목표와 구현 범위 |
| [비즈니스 정책](./docs/BUSINESS_RULES.md) | 주문, 포인트, 외부 이벤트, 인기 메뉴 정책 |
| [API 명세](./docs/API_SPEC.md) | 요청/응답, 오류 코드, 권한 |
| [ERD](./docs/ERD.md) | 데이터 모델과 제약조건 |
| [아키텍처](./docs/ARCHITECTURE.md) | 트랜잭션, 동시성, 이벤트 구조 |
| [테스트 전략](./docs/TEST_STRATEGY.md) | 테스트 범위와 검증 기준 |
| [기술 학습 문서](./docs/wiki/Home.md) | 핵심 기술 주제별 설명 |

---

## 현재 제외 범위

- PortOne 등 실제 외부 결제 PG 연동
- 결제 웹훅, 결제 취소, 포인트 환불
- 소셜 로그인, 이메일 인증, 비밀번호 찾기
- 대규모 AWS 인프라 구성
- 복잡한 관리자 대시보드 UI
