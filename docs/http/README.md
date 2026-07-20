# Local HTTP Demo

이 디렉터리는 IntelliJ HTTP Client에서 로컬 API를 순서대로 눌러보기 위한 파일을 모아둔다.

## 실행 전 준비

1. MySQL, Redis, Kafka를 실행한다.

```powershell
docker-compose up -d
```

2. 애플리케이션을 `local` 프로필로 실행한다.

```powershell
.\gradlew.bat bootRun
```

`application-local.yaml`은 기본적으로 `localhost:3307/coffee_order`, `coffee/coffee`를 사용한다.
로컬 JPA DDL은 `SPRING_JPA_HIBERNATE_DDL_AUTO`로 덮어쓸 수 있으며, 기본값은 `update`다.

3. 스키마가 생성된 뒤 `00-local-demo-data.sql`을 MySQL에 실행한다.

직접 설치한 MySQL에 DB와 계정이 없다면 먼저 `00-local-database.sql`을 root 권한으로 한 번 실행한다.
Docker Compose로 띄운 MySQL은 `coffee_order` DB와 `coffee/coffee` 계정을 자동 생성한다.
기존에 3306 포트 매핑으로 만들어둔 컨테이너가 있다면 Compose 설정 변경을 반영하기 위해 컨테이너를 다시 생성한다.

## 데모 계정

| 권한 | 이메일 | 비밀번호 |
| --- | --- | --- |
| USER | `user.local@example.com` | `Password123!` |
| ADMIN | `admin.local@example.com` | `Password123!` |

## 실행 순서

| 순서 | 파일 | 확인 내용 |
| --- | --- | --- |
| 1 | `01-health-and-metrics.http` | Health, Prometheus 메트릭 |
| 2 | `02-auth-user.http` | 일반 사용자 로그인, 내 정보 |
| 3 | `03-auth-admin.http` | 관리자 로그인, 관리자 토큰 저장 |
| 4 | `04-public-menu.http` | 공개 메뉴 목록, 상세, 인기 메뉴 |
| 5 | `05-admin-menu.http` | 관리자 메뉴 등록, 수정, 상태 변경, 삭제 |
| 6 | `06-point.http` | 포인트 잔액, 충전, 이력 |
| 7 | `07-cart.http` | 장바구니 비우기, 추가, 수정, 삭제 |
| 8 | `08-order.http` | 장바구니 주문, 주문 목록/상세, 멱등 재요청 |
| 9 | `09-popular-menu.http` | 주문 후 인기 메뉴 |
| 10 | `10-mock-order-event.http` | local Mock 주문 이벤트 API |
| 11 | `11-admin-operation.http` | 관리자 주문/Outbox/Kafka 운영 조회 |
| 12 | `12-auth-token-lifecycle.http` | 토큰 재발급, 로그아웃 |

`08-order.http`는 고정 `Idempotency-Key`를 사용한다.
같은 키로 다시 실행하면 새 주문을 만들지 않고 기존 주문 결과를 반환한다.
새 주문을 만들고 싶으면 `08-order.http`의 `@orderIdempotencyKey` 값을 다른 UUID로 바꾼 뒤,
`07-cart.http`로 장바구니를 다시 채운다.
