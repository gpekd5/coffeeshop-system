# coffeeshop-system


## 주요 기능

- 회원가입, 로그인, 로그아웃
- 메뉴 조회 및 관리자 메뉴 관리
- 장바구니
- 포인트 충전 및 사용
- 주문 및 결제
- 주문 데이터 외부 전송
- 최근 7일 인기 메뉴 조회

상세 기능 정의와 정책은 GitHub Wiki를 참고합니다.

## 설계 문서

- [프로젝트 개요](./docs/PROJECT_CONTEXT.md)
- [비즈니스 정책](./docs/BUSINESS_RULES.md)
- [와이어프레임](./docs/WIREFRAME.md)

## 로컬 개발 환경

MySQL과 Redis는 Docker Compose로 실행합니다.

```shell
docker compose up -d
```

Compose v1 환경에서는 다음 명령을 사용할 수 있습니다.

```shell
docker-compose up -d
```

애플리케이션은 기본적으로 `local` 프로필을 사용합니다.

```shell
./gradlew bootRun
```

Windows PowerShell에서는 다음 명령을 사용할 수 있습니다.

```powershell
.\gradlew.bat bootRun
```

## 프로필별 설정

| 프로필 | Mock API | Kafka Publisher | Kafka Consumer |
|---|---|---|---|
| `local` | 기본 활성화. `APP_MOCK_ORDER_EVENT_ENABLED=false`로 비활성화 | 기본 비활성화. `APP_KAFKA_ORDER_EVENT_PUBLISHER_ENABLED=true`로 활성화 | 기본 비활성화. `APP_KAFKA_ORDER_EVENT_CONSUMER_ENABLED=true`로 활성화 |
| `test` | 테스트용으로 명시 활성화 | 비활성화 | 비활성화 |
| `prod` | 비활성화. 컨트롤러도 등록하지 않음 | 기본 비활성화. 환경변수로 명시 활성화 | 기본 비활성화. 환경변수로 명시 활성화 |

`prod` 프로필은 MySQL, Redis, Kafka, JWT Secret을 운영 환경변수로 주입받습니다.
로컬 기본값을 운영에서 그대로 사용하지 않도록 `SPRING_DATASOURCE_URL`,
`SPRING_DATA_REDIS_HOST`, `SPRING_KAFKA_BOOTSTRAP_SERVERS`,
`APP_JWT_SECRET`, `APP_EXTERNAL_ORDER_EVENT_BASE_URL`,
`APP_EXTERNAL_ORDER_EVENT_PATH`를 설정해야 합니다.
`APP_KAFKA_ORDER_EVENT_CONSUMER_ENABLED=true`로 Consumer를 활성화하면
외부 주문 이벤트 URL이 비어 있거나 localhost 또는 `/mock/v1/order-events`이면
애플리케이션 시작 단계에서 실패합니다.

테스트와 기본 빌드 검증은 다음 명령으로 실행합니다.

```shell
./gradlew clean check
```
