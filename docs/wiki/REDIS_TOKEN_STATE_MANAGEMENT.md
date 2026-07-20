# Redis 기반 토큰 상태 관리

## 1. 문제 배경

JWT Access Token은 기본적으로 서버가 토큰 상태를 저장하지 않아도 인증할 수 있다.

하지만 이 특성 때문에 로그아웃이 까다롭다.

```text
Access Token 발급
→ 사용자가 로그아웃
→ 토큰 자체는 만료 전까지 유효
```

서버가 로그아웃한 토큰을 기억하지 않으면, 로그아웃 후에도 만료 전 Access Token으로 API를 호출할 수 있다.

Refresh Token도 마찬가지다.

재발급 요청에서 제출된 Refresh Token이 서버가 발급하고 보관한 토큰인지 확인해야 한다.

---

## 2. 선택한 전략

현재 프로젝트는 Redis에 토큰 상태를 저장한다.

| 토큰 | Redis 전략 |
| --- | --- |
| Access Token | 로그아웃한 토큰을 Blacklist에 저장 |
| Refresh Token | 로그인한 회원의 유효 토큰을 Whitelist에 저장 |

Redis를 쓰는 이유는 다음과 같다.

| 이유 | 설명 |
| --- | --- |
| 다중 서버 공유 | 여러 애플리케이션 인스턴스가 같은 토큰 상태를 확인 |
| TTL 지원 | 토큰 남은 유효시간만큼 자동 만료 |
| 빠른 조회 | 매 인증 요청에서 Blacklist 확인 가능 |
| 원자 연산 | Lua Script로 Rotation과 Logout을 한 번에 처리 |

---

## 3. Access Token Blacklist

로그아웃 시 Access Token을 Redis Blacklist에 저장한다.

```text
Key: auth:blacklist:{accessTokenHash}
TTL: Access Token 남은 유효시간
```

인증 필터는 매 요청마다 Blacklist를 확인한다.

```text
요청
→ Authorization Header 추출
→ JWT 서명과 만료 검증
→ Redis Blacklist 확인
→ Blacklist면 BLACKLISTED_TOKEN
→ 아니면 SecurityContext 저장
```

토큰 원문을 Redis key로 직접 쓰지 않고 해시를 사용해 노출 위험을 줄인다.

---

## 4. Refresh Token Whitelist

로그인 성공 시 Refresh Token을 Redis Whitelist에 저장한다.

```text
Key: auth:refresh:{memberId}
Value: refreshTokenHash
TTL: Refresh Token 유효시간
```

재발급 요청에서는 다음을 확인한다.

1. Refresh Token의 서명과 만료 검증
2. 토큰 타입이 `REFRESH`인지 검증
3. Redis Whitelist의 해시와 일치하는지 확인

Redis에 없거나 일치하지 않으면 재발급을 거부한다.

---

## 5. Refresh Token Rotation

Refresh Token 재발급은 보안상 기존 Refresh Token을 새 Refresh Token으로 교체한다.

문제는 동시 요청이다.

```text
요청 A: 기존 Refresh Token으로 재발급
요청 B: 같은 Refresh Token으로 재발급
```

둘 다 성공하면 Refresh Token이 여러 개 살아남는 것과 비슷한 문제가 생긴다.

따라서 Rotation은 다음 조건을 원자적으로 처리해야 한다.

```text
현재 Redis 값이 요청 Refresh Token과 일치할 때만
새 Refresh Token으로 교체한다.
```

현재 구현은 Redis Lua Script를 사용해 비교와 교체를 한 번에 실행한다.

---

## 6. 로그아웃 원자성

로그아웃에서는 두 작업이 함께 일어나야 한다.

```text
1. Access Token Blacklist 저장
2. Refresh Token 삭제
```

둘 중 하나만 성공하면 상태가 어색해진다.

예를 들어 Access Token은 차단됐지만 Refresh Token이 남으면 다시 재발급할 수 있다.

반대로 Refresh Token은 삭제됐지만 Access Token Blacklist 저장이 실패하면 로그아웃한 Access Token이 계속 사용될 수 있다.

따라서 로그아웃도 Lua Script로 한 번에 처리한다.

---

## 7. 핵심 코드 위치

| 코드 | 역할 |
| --- | --- |
| `src/main/java/com/example/coffeeorder/common/security/RedisTokenStore.java` | Redis 토큰 저장소 구현 |
| `src/main/java/com/example/coffeeorder/common/security/TokenStore.java` | 토큰 저장소 인터페이스 |
| `src/main/java/com/example/coffeeorder/auth/service/AuthService.java` | 로그인, 재발급, 로그아웃에서 TokenStore 사용 |
| `src/main/java/com/example/coffeeorder/common/security/JwtAuthenticationFilter.java` | Access Token Blacklist 확인 |
| `src/main/java/com/example/coffeeorder/common/security/JwtTokenProvider.java` | JWT 생성과 검증 |

---

## 8. 테스트로 검증한 내용

| 테스트 | 검증 내용 |
| --- | --- |
| RedisTokenStoreTest | Refresh Token 저장, Rotation Lua Script, Logout Lua Script 호출 |
| RedisTokenStoreIntegrationTest | 실제 Redis에서 TTL, Blacklist, Whitelist 동작 검증 |
| AuthControllerIntegrationTest | 로그인, 재발급, 동시 재발급, 로그아웃 API 흐름 검증 |

특히 중요한 테스트는 다음이다.

```text
실제_Redis에서_동일_RefreshToken_동시_Rotation은_하나만_성공한다
동일_RefreshToken_동시_재발급은_하나만_성공한다
로그아웃하면_AccessToken을_차단하고_RefreshToken을_삭제한다
```

---

## 실제 적용 코드

### 적용 파일 경로

| 파일 | 역할 |
| --- | --- |
| `src/main/java/com/example/coffeeorder/auth/controller/AuthController.java` | 로그인, 재발급, 로그아웃 API 진입점 |
| `src/main/java/com/example/coffeeorder/auth/service/AuthService.java` | 토큰 발급, Refresh Token Rotation, 로그아웃 처리 |
| `src/main/java/com/example/coffeeorder/common/security/RedisTokenStore.java` | Refresh Token Whitelist, Access Token Blacklist, Lua Script 원자 처리 |
| `src/main/java/com/example/coffeeorder/common/security/JwtAuthenticationFilter.java` | Access Token Blacklist 인증 차단 |

### 호출 흐름

```text
로그인
→ AuthService.login()
→ JwtTokenProvider.createLoginTokens()
→ RedisTokenStore.saveRefreshToken()

재발급
→ AuthService.reissue()
→ RedisTokenStore.rotateRefreshToken()
→ Lua Script로 기존 Refresh 검증과 새 Refresh 저장을 원자 처리

로그아웃
→ AuthService.logout()
→ RedisTokenStore.logoutTokens()
→ Lua Script로 Access Blacklist 저장과 Refresh 삭제를 원자 처리
```

### 핵심 코드만 짧게 발췌

```java
boolean rotated = tokenStore.rotateRefreshToken(
        member.getId(),
        request.refreshToken(),
        tokenResponse.refreshToken(),
        tokenResponse.refreshTokenExpiresIn()
);
```

```lua
local current = redis.call('GET', KEYS[1])
if current ~= ARGV[1] then
    return 0
end
redis.call('SET', KEYS[1], ARGV[2], 'EX', tonumber(ARGV[3]))
```

```java
if (tokenStore.isAccessTokenBlacklisted(token)) {
    throw new JwtAuthenticationException(ErrorCode.BLACKLISTED_TOKEN);
}
```

### 이 코드가 해당 개념을 구현하는 이유

JWT 자체는 발급 후 서버가 상태를 갖지 않지만, 로그아웃과 Refresh Token Rotation은 서버 측 상태가 필요하다. Redis에 Refresh Token Hash를 저장해 Whitelist로 사용하고, 로그아웃된 Access Token Hash를 Blacklist로 저장한다. Rotation과 Logout은 Lua Script 한 번으로 검증/저장/삭제를 처리하므로 동시 요청이나 부분 성공 위험을 줄인다.

---

## 9. 한계와 개선 방향

현재 Refresh Token key는 회원 ID 기준이다.

따라서 한 회원이 여러 기기에서 동시에 로그인하는 멀티 세션 정책을 지원하려면 구조 변경이 필요하다.

후속 개선 후보는 다음과 같다.

| 개선 | 설명 |
| --- | --- |
| 세션 ID 도입 | 회원별 여러 Refresh Token을 구분 |
| Refresh Token family | Rotation 탈취 감지 |
| 기기별 로그아웃 | 특정 세션만 무효화 |
| Redis 장애 대응 | Redis 장애 시 인증 정책 결정 |
