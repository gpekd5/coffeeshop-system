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

테스트와 기본 빌드 검증은 다음 명령으로 실행합니다.

```shell
./gradlew clean check
```
