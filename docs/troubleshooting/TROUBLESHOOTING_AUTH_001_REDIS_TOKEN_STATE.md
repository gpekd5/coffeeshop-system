# Redis 토큰 상태 문제

## 1. 증상

다음 상황이 발생하면 Redis 토큰 상태를 먼저 의심한다.

| 증상 | 예시 |
| --- | --- |
| 로그아웃 후에도 Access Token이 통과함 | 보호 API가 계속 200 응답 |
| Refresh Token 재발급이 실패함 | `/api/v1/auth/reissue`에서 인증 오류 |
| 동시에 재발급한 요청 중 여러 개가 성공함 | Refresh Token Rotation 원자성 문제 |
| 로그아웃 뒤 Refresh Token이 남아 있음 | 다시 재발급이 가능함 |

---

## 2. 영향

토큰 상태 저장은 인증 보안의 마지막 방어선이다.

Access Token Blacklist 저장과 Refresh Token 삭제가 일부만 성공하면 로그아웃 이후에도 인증 상태가 남을 수 있다.
Refresh Token Rotation이 원자적으로 처리되지 않으면 같은 Refresh Token으로 여러 Access Token을 발급받을 수 있다.

---

## 3. 먼저 확인할 것

| 확인 대상 | 정상 기준 |
| --- | --- |
| Redis 연결 | `SPRING_DATA_REDIS_HOST`, `SPRING_DATA_REDIS_PORT`로 접속 가능 |
| Refresh Token 키 | `auth:refresh:{memberId}`에 Refresh Token hash 저장 |
| Access Token Blacklist 키 | `auth:blacklist:access:{accessTokenHash}`에 TTL 포함 저장 |
| 로그아웃 처리 | Refresh Token 삭제와 Access Token Blacklist 저장이 Lua Script로 함께 실행 |
| 재발급 처리 | 기존 Refresh Token hash와 일치할 때만 새 hash로 교체 |

---

## 4. 원인별 확인 방법

### Redis 연결 문제

애플리케이션이 Redis에 연결하지 못하면 Refresh Token 검증, Rotation, Logout 상태 저장이 실패한다.

```text
SPRING_DATA_REDIS_HOST
SPRING_DATA_REDIS_PORT
SPRING_DATA_REDIS_PASSWORD
SPRING_DATA_REDIS_TIMEOUT
```

로컬에서는 `docker compose up -d`로 Redis 컨테이너가 떠 있는지 확인한다.

### Access Token Blacklist TTL 문제

Access Token 만료 시간이 이미 지났다면 Blacklist 키를 저장하지 않을 수 있다.

로그아웃 직후에도 토큰이 계속 통과한다면 다음을 확인한다.

```text
accessTokenTtlSeconds > 0
auth:blacklist:access:{accessTokenHash} 존재 여부
```

### Refresh Token Rotation 경쟁

동일 Refresh Token으로 동시 재발급 요청이 들어오면 하나만 성공해야 한다.

현재 구현은 Redis Lua Script에서 다음 순서로 원자 처리한다.

```text
GET auth:refresh:{memberId}
→ 현재 Refresh Token hash 비교
→ 일치할 때만 새 Refresh Token hash SET
```

여러 요청이 성공한다면 `RedisTokenStore.rotateRefreshToken()`의 Lua Script 실행 경로를 확인한다.

---

## 5. 복구 또는 대응

| 상황 | 대응 |
| --- | --- |
| Redis 장애 | Redis 복구 후 토큰 재발급/로그아웃 재시도 |
| 특정 회원 Refresh Token 이상 | `auth:refresh:{memberId}` 삭제 후 재로그인 유도 |
| Blacklist 키 누락 | Access Token 만료 전이면 로그아웃 요청을 다시 실행 |
| 동시 재발급 다중 성공 | Lua Script 결과와 `RedisTokenStoreIntegrationTest`를 우선 확인 |

토큰 원문은 로그나 문서에 남기지 않는다.

---

## 6. 관련 코드와 테스트

| 위치 | 설명 |
| --- | --- |
| [RedisTokenStore.java](../../src/main/java/com/example/coffeeorder/common/security/RedisTokenStore.java) | Redis 기반 TokenStore 구현 |
| [TokenStore.java](../../src/main/java/com/example/coffeeorder/common/security/TokenStore.java) | 토큰 상태 저장 계약 |
| [AuthService.java](../../src/main/java/com/example/coffeeorder/auth/service/AuthService.java) | 로그인, 재발급, 로그아웃 흐름 |
| [RedisTokenStoreTest.java](../../src/test/java/com/example/coffeeorder/common/security/RedisTokenStoreTest.java) | Lua Script 호출 단위 검증 |
| [RedisTokenStoreIntegrationTest.java](../../src/test/java/com/example/coffeeorder/common/security/RedisTokenStoreIntegrationTest.java) | Redis 통합, Rotation 동시성, Logout 원자성 검증 |

관련 기술 문서: [Redis 기반 토큰 상태 관리](../wiki/REDIS_TOKEN_STATE_MANAGEMENT.md)
