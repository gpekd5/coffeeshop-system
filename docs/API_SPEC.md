# API Specification

이 문서는 커피 주문 및 포인트 결제 시스템에서 제공하는 API의  
요청 방식, 응답 형식, 인증 및 권한 정책을 정의한다.

구체적인 비즈니스 정책은 `BUSINESS_RULES.md`,  
데이터 구조는 `ERD.md`를 기준으로 한다.

---

# 0. 공통 규칙

## 0.1 Base URL

```text
/api/v1
```

---

## 0.2 Content-Type

JSON 요청과 응답은 다음 Content-Type을 사용한다.

```http
Content-Type: application/json
```

---

## 0.3 인증 헤더

인증이 필요한 API는 요청 Header에 JWT Access Token을 전달한다.

| Key | Value | 필수 | 설명 |
|---|---|---:|---|
| `Authorization` | `Bearer {accessToken}` | Y | JWT Access Token |

예시:

```http
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

---

## 0.4 사용자 식별 정책

- 인증이 필요한 API에서는 Access Token을 통해 회원을 식별한다.
- Request Body, Query Parameter 또는 Path Variable로 전달된 `memberId`를 인증 사용자로 신뢰하지 않는다.
- 자신의 장바구니, 포인트, 포인트 이력 및 주문 정보만 조회할 수 있다.
- 관리자 API는 `ADMIN` 권한을 가진 회원만 호출할 수 있다.

---

## 0.5 공통 성공 응답

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {}
}
```

`data`가 없는 경우 다음과 같이 반환한다.

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": null
}
```

---

## 0.6 공통 에러 응답

```json
{
  "success": false,
  "code": "ERROR_CODE",
  "message": "에러 메시지",
  "data": null
}
```

---

## 0.7 입력값 검증 실패 응답

```json
{
  "success": false,
  "code": "INVALID_INPUT",
  "message": "입력값이 올바르지 않습니다.",
  "data": {
    "errors": [
      {
        "field": "email",
        "message": "올바른 이메일 형식이어야 합니다."
      }
    ]
  }
}
```

---

## 0.8 페이징 응답

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "content": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0
  }
}
```

---

## 0.9 날짜와 시간 형식

날짜와 시간은 ISO-8601 형식을 사용한다.

```text
2026-07-13T14:30:00
```

타임존 정책은 서버 설정과 `ARCHITECTURE.md`에서 정의한다.

---

## 0.10 HTTP 상태 코드

| Status | 설명 |
|---:|---|
| `200 OK` | 조회, 수정 및 일반 요청 성공 |
| `201 Created` | 리소스 생성 성공 |
| `204 No Content` | 응답 본문 없는 삭제 성공 |
| `400 Bad Request` | 입력값 검증 또는 비즈니스 규칙 위반 |
| `401 Unauthorized` | 인증 실패 |
| `403 Forbidden` | 권한 부족 |
| `404 Not Found` | 리소스를 찾을 수 없음 |
| `409 Conflict` | 중복 데이터, 멱등성 또는 동시성 충돌 |
| `500 Internal Server Error` | 서버 내부 오류 |
| `502 Bad Gateway` | 외부 시스템 호출 실패 |
| `504 Gateway Timeout` | 외부 시스템 응답 지연 또는 타임아웃 |

---

## 0.11 주요 Enum

### MemberRole

```text
USER
ADMIN
```

### MemberStatus

```text
ACTIVE
INACTIVE
WITHDRAWN
```

### MenuCategory

```text
COFFEE
NON_COFFEE
DECAF
BAKERY
ETC
```

### MenuStatus

```text
ON_SALE
SOLD_OUT
INACTIVE
```

### PointHistoryType

```text
CHARGE
USE
CANCEL
ADJUST
```

초기 구현에서는 `CHARGE`, `USE`를 필수로 사용한다.

### OrderChannel

```text
WEB_CART
MOBILE
KIOSK
ADMIN
EXTERNAL
```

초기 구현에서는 `WEB_CART`만 사용한다.

### OrderStatus

```text
COMPLETED
CANCELLED
```

### PaymentMethod

```text
POINT
CARD
EASY_PAY
GIFT_CARD
MIXED
```

초기 구현에서는 `POINT`만 사용한다.

### PaymentStatus

```text
COMPLETED
CANCELLED
```

### OutboxStatus

```text
PENDING
PUBLISHED
FAILED
```

---

# 1. 인증 / 회원 API

## 1-1. 회원가입

- Method: `POST`
- Path: `/api/v1/auth/signup`
- Auth: 불필요

### Request

```json
{
  "email": "test@example.com",
  "password": "Password123!",
  "name": "홍길동"
}
```

### Request Fields

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `email` | String | Y | 로그인 ID로 사용하는 이메일 |
| `password` | String | Y | 로그인 비밀번호 |
| `name` | String | Y | 회원 이름 |

### Validation

| 필드 | 규칙 |
|---|---|
| `email` | 이메일 형식, 앞뒤 공백 제거, 소문자 정규화, 중복 불가 |
| `password` | 필수, 비밀번호 형식 정책 충족 |
| `name` | 필수, 공백 문자열 불가 |

### 처리 정책

- 이메일의 앞뒤 공백을 제거한다.
- 이메일을 소문자로 변환한 후 중복 여부를 확인한다.
- 비밀번호는 BCrypt 등 단방향 암호화 방식으로 저장한다.
- 회원 권한은 `USER`로 생성한다.
- 회원 상태는 `ACTIVE`로 생성한다.
- 회원가입과 동시에 포인트 계정을 생성한다.
- 초기 포인트 잔액은 `0P`이다.

### Response

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "회원가입이 완료되었습니다.",
  "data": {
    "memberId": 1,
    "email": "test@example.com",
    "name": "홍길동",
    "role": "USER",
    "status": "ACTIVE",
    "pointBalance": 0
  }
}
```

### Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_INPUT` | 요청값 검증 실패 |
| `409` | `DUPLICATED_EMAIL` | 이미 사용 중인 이메일 |

---

## 1-2. 로그인

- Method: `POST`
- Path: `/api/v1/auth/login`
- Auth: 불필요

### Request

```json
{
  "email": "test@example.com",
  "password": "Password123!"
}
```

### Request Fields

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `email` | String | Y | 로그인 이메일 |
| `password` | String | Y | 로그인 비밀번호 |

### 처리 정책

- 이메일을 소문자로 정규화한 후 회원을 조회한다.
- 이메일 또는 비밀번호가 일치하지 않으면 동일한 로그인 실패 응답을 반환한다.
- `ACTIVE` 상태의 회원만 로그인할 수 있다.
- 로그인 성공 시 Access Token과 Refresh Token을 발급한다.
- Refresh Token은 Redis Whitelist에 저장한다.
- Refresh Token TTL은 Refresh Token의 만료시간과 동일하게 설정한다.

### Response

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "로그인에 성공했습니다.",
  "data": {
    "accessToken": "access-token",
    "refreshToken": "refresh-token",
    "tokenType": "Bearer",
    "accessTokenExpiresIn": 1800,
    "refreshTokenExpiresIn": 1209600
  }
}
```

### Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_INPUT` | 요청값 검증 실패 |
| `401` | `INVALID_LOGIN` | 이메일 또는 비밀번호 불일치 |
| `403` | `MEMBER_INACTIVE` | 비활성화된 회원 |
| `403` | `MEMBER_WITHDRAWN` | 탈퇴한 회원 |

---

## 1-3. 로그아웃

- Method: `POST`
- Path: `/api/v1/auth/logout`
- Auth: 필요

### Header

| Key | 필수 | 설명 |
|---|---:|---|
| `Authorization` | Y | Bearer Access Token |

### 처리 정책

- 요청 Header의 Access Token을 추출한다.
- Access Token의 남은 유효시간을 계산한다.
- 아직 만료되지 않은 Access Token을 Redis Blacklist에 저장한다.
- Blacklist TTL은 Access Token의 남은 유효시간과 동일하게 설정한다.
- Redis Whitelist에 저장된 Refresh Token을 삭제한다.
- 로그아웃 이후 해당 Refresh Token으로 토큰을 재발급할 수 없다.
- Blacklist에 등록된 Access Token으로 인증 요청 시 `401 Unauthorized`를 반환한다.

### Response

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "로그아웃이 완료되었습니다.",
  "data": null
}
```

### Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `401` | `INVALID_TOKEN` | 유효하지 않은 Access Token |
| `401` | `EXPIRED_TOKEN` | 만료된 Access Token |
| `401` | `BLACKLISTED_TOKEN` | 이미 로그아웃된 Access Token |

---

## 1-4. 토큰 재발급

- Method: `POST`
- Path: `/api/v1/auth/reissue`
- Auth: 불필요

### Request

```json
{
  "refreshToken": "refresh-token"
}
```

### Request Fields

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `refreshToken` | String | Y | Access Token 재발급용 Refresh Token |

### 처리 정책

- Refresh Token 자체의 서명과 만료 여부를 검증한다.
- Redis Whitelist에 저장된 Refresh Token과 요청값을 비교한다.
- 일치하면 새로운 Access Token과 Refresh Token을 발급한다.
- Refresh Token Rotation 전략을 사용한다.
- 기존 Refresh Token은 삭제하고 새로운 Refresh Token으로 교체한다.
- 만료되었거나 Redis에 존재하지 않는 Refresh Token은 사용할 수 없다.

### Response

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "토큰이 재발급되었습니다.",
  "data": {
    "accessToken": "new-access-token",
    "refreshToken": "new-refresh-token",
    "tokenType": "Bearer",
    "accessTokenExpiresIn": 1800,
    "refreshTokenExpiresIn": 1209600
  }
}
```

### Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_INPUT` | Refresh Token 누락 |
| `401` | `INVALID_REFRESH_TOKEN` | 유효하지 않은 Refresh Token |
| `401` | `EXPIRED_REFRESH_TOKEN` | 만료된 Refresh Token |
| `401` | `REFRESH_TOKEN_NOT_FOUND` | Redis에 존재하지 않는 Refresh Token |
| `403` | `MEMBER_WITHDRAWN` | 탈퇴한 회원 |

---

## 1-5. 내 정보 조회

- Method: `GET`
- Path: `/api/v1/members/me`
- Auth: 필요

### Response

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "내 정보 조회에 성공했습니다.",
  "data": {
    "memberId": 1,
    "email": "test@example.com",
    "name": "홍길동",
    "role": "USER",
    "status": "ACTIVE",
    "createdAt": "2026-07-13T10:00:00"
  }
}
```

### Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `404` | `MEMBER_NOT_FOUND` | 회원을 찾을 수 없음 |

---

## 1-6. 회원 탈퇴

- Method: `DELETE`
- Path: `/api/v1/members/me`
- Auth: 필요

### 처리 정책

- 회원 데이터는 물리 삭제하지 않는다.
- 회원 상태를 `WITHDRAWN`으로 변경한다.
- `deleted_at`에 탈퇴 일시를 저장한다.
- Redis Whitelist에 저장된 Refresh Token을 삭제한다.
- 요청 Access Token을 Redis Blacklist에 저장한다.
- 과거 주문, 결제 및 포인트 이력은 유지한다.

### Response

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "회원 탈퇴가 완료되었습니다.",
  "data": null
}
```

### Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `404` | `MEMBER_NOT_FOUND` | 회원을 찾을 수 없음 |
| `409` | `MEMBER_ALREADY_WITHDRAWN` | 이미 탈퇴한 회원 |

---

# 2. 메뉴 API

## 2-1. 메뉴 목록 조회

- Method: `GET`
- Path: `/api/v1/menus`
- Auth: 불필요

### Query Parameters

| 필드 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---:|---:|---|
| `category` | String | N | 전체 | 메뉴 카테고리 |
| `status` | String | N | `ON_SALE` | 메뉴 판매 상태 |
| `keyword` | String | N | 없음 | 메뉴 이름 검색어 |
| `page` | Integer | N | `0` | 페이지 번호 |
| `size` | Integer | N | `20` | 페이지 크기 |
| `sort` | String | N | `createdAt,desc` | 정렬 기준 |

### 처리 정책

- 삭제된 메뉴는 공개 목록에서 조회하지 않는다.
- `status`를 생략하면 `ON_SALE` 메뉴만 조회한다.
- `keyword`는 메뉴 이름을 기준으로 검색한다.
- 카테고리와 판매 상태를 동시에 필터링할 수 있다.
- 페이지 크기의 최대값은 후속 정책에서 결정한다.

### Request Example

```http
GET /api/v1/menus?category=COFFEE&status=ON_SALE&page=0&size=20&sort=createdAt,desc
```

### Response

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "메뉴 목록 조회에 성공했습니다.",
  "data": {
    "content": [
      {
        "menuId": 1,
        "name": "아메리카노",
        "description": "진한 에스프레소와 물로 만든 커피",
        "category": "COFFEE",
        "price": 4500,
        "status": "ON_SALE",
        "createdAt": "2026-07-13T10:00:00",
        "updatedAt": "2026-07-13T10:00:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

### Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_MENU_CATEGORY` | 유효하지 않은 메뉴 카테고리 |
| `400` | `INVALID_MENU_STATUS` | 유효하지 않은 메뉴 상태 |
| `400` | `INVALID_PAGE_REQUEST` | 잘못된 페이징 요청 |

---

## 2-2. 메뉴 상세 조회

- Method: `GET`
- Path: `/api/v1/menus/{menuId}`
- Auth: 불필요

### Path Variable

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `menuId` | Long | Y | 메뉴 식별자 |

### 처리 정책

- 삭제된 메뉴는 일반 사용자에게 조회되지 않는다.
- 판매 중지 또는 품절 메뉴는 상세 조회할 수 있다.
- 판매 상태를 응답에 포함한다.

### Response

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "메뉴 상세 조회에 성공했습니다.",
  "data": {
    "menuId": 1,
    "name": "아메리카노",
    "description": "진한 에스프레소와 물로 만든 커피",
    "category": "COFFEE",
    "price": 4500,
    "status": "ON_SALE",
    "createdAt": "2026-07-13T10:00:00",
    "updatedAt": "2026-07-13T10:00:00"
  }
}
```

### Error

| Status | Code | 설명 |
|---:|---|---|
| `404` | `MENU_NOT_FOUND` | 메뉴를 찾을 수 없음 |

---

## 2-3. 메뉴 등록

- Method: `POST`
- Path: `/api/v1/admin/menus`
- Auth: 필요
- Role: `ADMIN`

### Request

```json
{
  "name": "카페라떼",
  "description": "에스프레소와 우유로 만든 커피",
  "category": "COFFEE",
  "price": 5000,
  "status": "ON_SALE"
}
```

### Request Fields

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `name` | String | Y | 메뉴 이름 |
| `description` | String | N | 메뉴 설명 |
| `category` | String | Y | 메뉴 카테고리 |
| `price` | Long | Y | 메뉴 가격 |
| `status` | String | N | 메뉴 판매 상태 |

### Validation

| 필드 | 규칙 |
|---|---|
| `name` | 필수, 공백 문자열 불가 |
| `description` | 최대 길이 제한 |
| `category` | 정의된 Enum만 허용 |
| `price` | 0보다 큰 정수 |
| `status` | 생략 시 `ON_SALE` |

### Response

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "메뉴가 등록되었습니다.",
  "data": {
    "menuId": 2,
    "name": "카페라떼",
    "description": "에스프레소와 우유로 만든 커피",
    "category": "COFFEE",
    "price": 5000,
    "status": "ON_SALE",
    "createdAt": "2026-07-13T11:00:00",
    "updatedAt": "2026-07-13T11:00:00"
  }
}
```

### Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_INPUT` | 요청값 검증 실패 |
| `400` | `INVALID_MENU_CATEGORY` | 유효하지 않은 카테고리 |
| `400` | `INVALID_MENU_PRICE` | 가격이 0 이하 |
| `400` | `INVALID_MENU_STATUS` | 유효하지 않은 상태 |
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `FORBIDDEN` | 관리자 권한 없음 |

---

## 2-4. 메뉴 수정

- Method: `PATCH`
- Path: `/api/v1/admin/menus/{menuId}`
- Auth: 필요
- Role: `ADMIN`

### Request

```json
{
  "name": "카페라떼",
  "description": "고소한 우유가 들어간 라떼",
  "category": "COFFEE",
  "price": 5500
}
```

### 처리 정책

- 전달된 필드만 수정한다.
- `null` 필드는 기존 값을 유지한다.
- 메뉴 가격 변경은 이후 주문부터 적용한다.
- 과거 주문 항목의 메뉴 이름과 가격은 변경되지 않는다.
- 삭제된 메뉴는 수정할 수 없다.

### Response

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "메뉴가 수정되었습니다.",
  "data": {
    "menuId": 2,
    "name": "카페라떼",
    "description": "고소한 우유가 들어간 라떼",
    "category": "COFFEE",
    "price": 5500,
    "status": "ON_SALE",
    "createdAt": "2026-07-13T11:00:00",
    "updatedAt": "2026-07-13T11:30:00"
  }
}
```

### Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_INPUT` | 요청값 검증 실패 |
| `400` | `INVALID_MENU_CATEGORY` | 유효하지 않은 카테고리 |
| `400` | `INVALID_MENU_PRICE` | 가격이 0 이하 |
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `FORBIDDEN` | 관리자 권한 없음 |
| `404` | `MENU_NOT_FOUND` | 메뉴를 찾을 수 없음 |
| `409` | `MENU_ALREADY_DELETED` | 이미 삭제된 메뉴 |

---

## 2-5. 메뉴 판매 상태 변경

- Method: `PATCH`
- Path: `/api/v1/admin/menus/{menuId}/status`
- Auth: 필요
- Role: `ADMIN`

### Request

```json
{
  "status": "SOLD_OUT"
}
```

### 처리 정책

- 메뉴 판매 상태만 변경한다.
- 삭제된 메뉴는 상태를 변경할 수 없다.
- `SOLD_OUT`, `INACTIVE` 메뉴는 장바구니에 추가하거나 주문할 수 없다.

### Response

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "메뉴 상태가 변경되었습니다.",
  "data": {
    "menuId": 2,
    "status": "SOLD_OUT",
    "updatedAt": "2026-07-13T12:00:00"
  }
}
```

### Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_MENU_STATUS` | 유효하지 않은 메뉴 상태 |
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `FORBIDDEN` | 관리자 권한 없음 |
| `404` | `MENU_NOT_FOUND` | 메뉴를 찾을 수 없음 |

---

## 2-6. 메뉴 삭제

- Method: `DELETE`
- Path: `/api/v1/admin/menus/{menuId}`
- Auth: 필요
- Role: `ADMIN`

### 처리 정책

- 메뉴는 물리 삭제하지 않는다.
- `deleted_at`에 삭제 시각을 저장한다.
- 삭제된 메뉴는 공개 메뉴 목록과 주문 대상에서 제외한다.
- 과거 주문 항목의 메뉴 정보는 유지한다.

### Response

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "메뉴가 삭제되었습니다.",
  "data": null
}
```

### Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `FORBIDDEN` | 관리자 권한 없음 |
| `404` | `MENU_NOT_FOUND` | 메뉴를 찾을 수 없음 |
| `409` | `MENU_ALREADY_DELETED` | 이미 삭제된 메뉴 |

---

# 3. 장바구니 API

## 3-1. 내 장바구니 조회

- Method: `GET`
- Path: `/api/v1/cart`
- Auth: 필요

### 처리 정책

- 인증된 사용자의 장바구니만 조회한다.
- 장바구니에는 메뉴의 현재 가격을 표시한다.
- 장바구니에 표시된 가격은 최종 결제 가격으로 보장하지 않는다.
- 주문 시점에 메뉴 가격과 상태를 다시 검증한다.
- 장바구니가 비어 있으면 빈 `items` 배열을 반환한다.

### Response

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "장바구니 조회에 성공했습니다.",
  "data": {
    "cartId": 1,
    "items": [
      {
        "cartItemId": 10,
        "menuId": 1,
        "menuName": "아메리카노",
        "category": "COFFEE",
        "unitPrice": 4500,
        "quantity": 2,
        "lineAmount": 9000,
        "menuStatus": "ON_SALE"
      },
      {
        "cartItemId": 11,
        "menuId": 3,
        "menuName": "크로와상",
        "category": "BAKERY",
        "unitPrice": 3500,
        "quantity": 1,
        "lineAmount": 3500,
        "menuStatus": "ON_SALE"
      }
    ],
    "expectedTotalAmount": 12500,
    "updatedAt": "2026-07-13T12:30:00"
  }
}
```

### Empty Response

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "장바구니 조회에 성공했습니다.",
  "data": {
    "cartId": 1,
    "items": [],
    "expectedTotalAmount": 0,
    "updatedAt": "2026-07-13T12:30:00"
  }
}
```

### Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `404` | `CART_NOT_FOUND` | 장바구니를 찾을 수 없음 |

---

## 3-2. 장바구니에 메뉴 추가

- Method: `POST`
- Path: `/api/v1/cart/items`
- Auth: 필요

### Request

```json
{
  "menuId": 1,
  "quantity": 2
}
```

### Request Fields

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `menuId` | Long | Y | 추가할 메뉴 식별자 |
| `quantity` | Integer | Y | 추가할 수량 |

### 처리 정책

- 판매 중인 메뉴만 장바구니에 추가할 수 있다.
- 수량은 1 이상이어야 한다.
- 동일 메뉴가 이미 장바구니에 존재하면 기존 수량에 요청 수량을 더한다.
- 동일 장바구니와 메뉴 조합은 한 행만 존재한다.
- 메뉴 가격은 장바구니 항목에 저장하지 않는다.
- 장바구니 메뉴별 최대 수량은 후속 정책에서 결정한다.

### Response

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "메뉴가 장바구니에 추가되었습니다.",
  "data": {
    "cartItemId": 10,
    "menuId": 1,
    "menuName": "아메리카노",
    "unitPrice": 4500,
    "quantity": 2,
    "lineAmount": 9000
  }
}
```

### Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_QUANTITY` | 수량이 1 미만 |
| `400` | `CART_ITEM_QUANTITY_EXCEEDED` | 메뉴별 최대 수량 초과 |
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `404` | `MENU_NOT_FOUND` | 메뉴를 찾을 수 없음 |
| `409` | `MENU_NOT_ON_SALE` | 판매 중인 메뉴가 아님 |

---

## 3-3. 장바구니 수량 변경

- Method: `PATCH`
- Path: `/api/v1/cart/items/{cartItemId}`
- Auth: 필요

### Request

```json
{
  "quantity": 3
}
```

### 처리 정책

- 자신의 장바구니 항목만 수정할 수 있다.
- 수량은 1 이상이어야 한다.
- 수량을 0으로 변경할 수 없다.
- 항목 삭제는 별도의 DELETE API를 사용한다.

### Response

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "장바구니 수량이 변경되었습니다.",
  "data": {
    "cartItemId": 10,
    "menuId": 1,
    "menuName": "아메리카노",
    "unitPrice": 4500,
    "quantity": 3,
    "lineAmount": 13500
  }
}
```

### Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_QUANTITY` | 수량이 1 미만 |
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `CART_ITEM_FORBIDDEN` | 본인의 장바구니 항목이 아님 |
| `404` | `CART_ITEM_NOT_FOUND` | 장바구니 항목을 찾을 수 없음 |

---

## 3-4. 장바구니 항목 삭제

- Method: `DELETE`
- Path: `/api/v1/cart/items/{cartItemId}`
- Auth: 필요

### 처리 정책

- 자신의 장바구니 항목만 삭제할 수 있다.
- 장바구니 항목은 Hard Delete 방식으로 삭제한다.

### Response

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "장바구니 항목이 삭제되었습니다.",
  "data": null
}
```

### Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `CART_ITEM_FORBIDDEN` | 본인의 장바구니 항목이 아님 |
| `404` | `CART_ITEM_NOT_FOUND` | 장바구니 항목을 찾을 수 없음 |

---

## 3-5. 장바구니 전체 비우기

- Method: `DELETE`
- Path: `/api/v1/cart/items`
- Auth: 필요

### 처리 정책

- 인증된 사용자의 모든 장바구니 항목을 Hard Delete한다.
- 이미 장바구니가 비어 있어도 성공으로 처리할 수 있다.

### Response

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "장바구니를 비웠습니다.",
  "data": null
}
```

### Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |

---

# 4. 포인트 API

## 4-1. 내 포인트 잔액 조회

- Method: `GET`
- Path: `/api/v1/points`
- Auth: 필요

### Response

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "포인트 잔액 조회에 성공했습니다.",
  "data": {
    "memberId": 1,
    "balance": 50000,
    "updatedAt": "2026-07-13T13:00:00"
  }
}
```

### Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `404` | `POINT_ACCOUNT_NOT_FOUND` | 포인트 계정을 찾을 수 없음 |

---

## 4-2. 포인트 충전

- Method: `POST`
- Path: `/api/v1/points/charge`
- Auth: 필요

### Request

```json
{
  "amount": 10000
}
```

### Request Fields

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `amount` | Long | Y | 충전할 포인트 |

### 처리 정책

- 충전 금액은 0보다 커야 한다.
- 1원당 1P를 충전한다.
- 기본 구현에서는 외부 결제 없이 요청 금액만큼 포인트를 증가시킨다.
- 포인트 잔액 증가와 포인트 이력 저장은 하나의 트랜잭션으로 처리한다.
- 충전 이력의 `type`은 `CHARGE`이다.
- 충전 이력의 `amount`는 양수로 저장한다.
- 동시 충전 및 주문 요청에서도 최종 잔액이 정확해야 한다.

### Response

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "포인트가 충전되었습니다.",
  "data": {
    "historyId": 101,
    "chargedAmount": 10000,
    "balanceBefore": 50000,
    "balanceAfter": 60000,
    "chargedAt": "2026-07-13T13:10:00"
  }
}
```

### Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_POINT_AMOUNT` | 충전 금액이 0 이하 |
| `400` | `POINT_CHARGE_LIMIT_EXCEEDED` | 충전 한도 초과 |
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `404` | `POINT_ACCOUNT_NOT_FOUND` | 포인트 계정을 찾을 수 없음 |
| `409` | `POINT_UPDATE_CONFLICT` | 포인트 변경 중 동시성 충돌 |

---

## 4-3. 포인트 이력 조회

- Method: `GET`
- Path: `/api/v1/points/histories`
- Auth: 필요

### Query Parameters

| 필드 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---:|---:|---|
| `type` | String | N | 전체 | 포인트 이력 유형 |
| `page` | Integer | N | `0` | 페이지 번호 |
| `size` | Integer | N | `20` | 페이지 크기 |
| `sort` | String | N | `createdAt,desc` | 정렬 기준 |

### 처리 정책

- 인증된 사용자의 포인트 이력만 조회한다.
- Request Parameter로 `memberId`를 받지 않는다.
- 최신 이력부터 조회한다.
- `type`을 이용해 충전 또는 사용 이력을 필터링할 수 있다.

### Response

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "포인트 이력 조회에 성공했습니다.",
  "data": {
    "content": [
      {
        "historyId": 101,
        "type": "CHARGE",
        "amount": 10000,
        "balanceAfter": 60000,
        "orderId": null,
        "createdAt": "2026-07-13T13:10:00"
      },
      {
        "historyId": 100,
        "type": "USE",
        "amount": -12000,
        "balanceAfter": 50000,
        "orderId": 50,
        "createdAt": "2026-07-13T12:00:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 2,
    "totalPages": 1
  }
}
```

### Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_POINT_HISTORY_TYPE` | 유효하지 않은 이력 유형 |
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |

---

# 5. 주문 / 결제 API

## 5-1. 장바구니 주문 및 포인트 결제

- Method: `POST`
- Path: `/api/v1/orders`
- Auth: 필요
- Idempotency: 필요

### Header

| Key | 필수 | 설명 |
|---|---:|---|
| `Authorization` | Y | Bearer Access Token |
| `Idempotency-Key` | Y | 중복 주문 및 결제 방지 키 |

예시:

```http
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
```

### Request

초기 장바구니 주문에서는 Request Body를 사용하지 않는다.

```json
{}
```

### 처리 정책

1. 인증된 회원을 확인한다.
2. `Idempotency-Key` Header를 확인한다.
3. 동일 회원과 동일 멱등키로 생성된 주문이 있는지 확인한다.
4. 회원의 장바구니와 장바구니 항목을 조회한다.
5. 장바구니가 비어 있지 않은지 확인한다.
6. 각 메뉴의 존재 여부와 판매 상태를 검증한다.
7. 각 메뉴의 현재 가격을 조회한다.
8. 서버가 주문 총금액을 계산한다.
9. 회원의 포인트 잔액을 확인한다.
10. 포인트 잔액이 충분하면 주문 금액만큼 차감한다.
11. 주문 정보를 저장한다.
12. 주문 당시 메뉴 정보를 주문 항목에 저장한다.
13. 포인트 결제 정보를 저장한다.
14. 포인트 사용 이력을 저장한다.
15. 주문 성공 후 장바구니 항목을 삭제한다.
16. 1차 구현에서는 외부 데이터 수집 API를 호출한다.
17. 2차 고도화에서는 Outbox Event를 동일한 DB 트랜잭션에 저장한다.
18. 주문 결과를 반환한다.

### 정합성 정책

다음 데이터는 하나의 논리적인 주문 처리 단위이다.

- 주문
- 주문 항목
- 결제
- 포인트 차감
- 포인트 이력
- 장바구니 항목 삭제
- 2차 고도화의 Outbox Event

다음 상태는 허용하지 않는다.

- 포인트만 차감되고 주문이 없는 상태
- 주문만 저장되고 포인트가 차감되지 않은 상태
- 주문 총금액과 결제 금액이 다른 상태
- 주문은 존재하지만 포인트 사용 이력이 없는 상태
- 동일한 멱등키로 주문이 여러 번 생성된 상태

### Response

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "주문과 결제가 완료되었습니다.",
  "data": {
    "orderId": 50,
    "orderNumber": "ORD-20260713-000050",
    "orderChannel": "WEB_CART",
    "status": "COMPLETED",
    "items": [
      {
        "orderItemId": 101,
        "menuId": 1,
        "menuName": "아메리카노",
        "menuCategory": "COFFEE",
        "unitPrice": 4500,
        "quantity": 2,
        "lineAmount": 9000
      },
      {
        "orderItemId": 102,
        "menuId": 3,
        "menuName": "크로와상",
        "menuCategory": "BAKERY",
        "unitPrice": 3500,
        "quantity": 1,
        "lineAmount": 3500
      }
    ],
    "totalAmount": 12500,
    "payment": {
      "paymentId": 70,
      "paymentMethod": "POINT",
      "paymentStatus": "COMPLETED",
      "amount": 12500,
      "paidAt": "2026-07-13T14:00:00"
    },
    "point": {
      "usedAmount": 12500,
      "balanceAfter": 47500
    },
    "orderedAt": "2026-07-13T14:00:00"
  }
}
```

### 동일 멱등키 재요청 응답

동일한 회원이 동일한 `Idempotency-Key`로 요청한 경우 기존 주문 결과를 반환한다.

```json
{
  "success": true,
  "code": "ORDER_ALREADY_PROCESSED",
  "message": "이미 처리된 주문입니다.",
  "data": {
    "orderId": 50,
    "orderNumber": "ORD-20260713-000050",
    "status": "COMPLETED",
    "totalAmount": 12500,
    "orderedAt": "2026-07-13T14:00:00"
  }
}
```

### Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `IDEMPOTENCY_KEY_REQUIRED` | 멱등키 누락 |
| `400` | `CART_EMPTY` | 장바구니가 비어 있음 |
| `400` | `INVALID_CART_ITEM_QUANTITY` | 잘못된 주문 수량 |
| `400` | `POINT_NOT_ENOUGH` | 보유 포인트 부족 |
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `404` | `CART_NOT_FOUND` | 장바구니를 찾을 수 없음 |
| `404` | `MENU_NOT_FOUND` | 메뉴를 찾을 수 없음 |
| `409` | `MENU_NOT_ON_SALE` | 판매 중이 아닌 메뉴 포함 |
| `409` | `POINT_UPDATE_CONFLICT` | 포인트 변경 동시성 충돌 |
| `409` | `ORDER_PROCESSING_CONFLICT` | 주문 처리 중 충돌 |
| `502` | `EXTERNAL_DATA_COLLECTION_FAILED` | 1차 외부 데이터 전송 실패 |
| `504` | `EXTERNAL_DATA_COLLECTION_TIMEOUT` | 외부 데이터 수집 API 타임아웃 |

---

## 5-2. 내 주문 목록 조회

- Method: `GET`
- Path: `/api/v1/orders`
- Auth: 필요

### Query Parameters

| 필드 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---:|---:|---|
| `status` | String | N | 전체 | 주문 상태 |
| `page` | Integer | N | `0` | 페이지 번호 |
| `size` | Integer | N | `20` | 페이지 크기 |
| `sort` | String | N | `orderedAt,desc` | 정렬 기준 |

### 처리 정책

- 인증된 사용자의 주문만 조회한다.
- Request Parameter로 `memberId`를 받지 않는다.
- 최신 주문부터 조회한다.

### Response

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "주문 목록 조회에 성공했습니다.",
  "data": {
    "content": [
      {
        "orderId": 50,
        "orderNumber": "ORD-20260713-000050",
        "status": "COMPLETED",
        "orderChannel": "WEB_CART",
        "representativeMenuName": "아메리카노",
        "additionalItemCount": 1,
        "totalAmount": 12500,
        "orderedAt": "2026-07-13T14:00:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

### Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_ORDER_STATUS` | 유효하지 않은 주문 상태 |
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |

---

## 5-3. 내 주문 상세 조회

- Method: `GET`
- Path: `/api/v1/orders/{orderId}`
- Auth: 필요

### 처리 정책

- 인증된 회원 본인의 주문만 조회할 수 있다.
- 주문 항목은 주문 당시 메뉴 이름과 가격을 반환한다.
- 현재 메뉴 정보가 변경되었더라도 주문 당시 정보를 사용한다.

### Response

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "주문 상세 조회에 성공했습니다.",
  "data": {
    "orderId": 50,
    "orderNumber": "ORD-20260713-000050",
    "memberId": 1,
    "orderChannel": "WEB_CART",
    "status": "COMPLETED",
    "items": [
      {
        "orderItemId": 101,
        "menuId": 1,
        "menuName": "아메리카노",
        "menuCategory": "COFFEE",
        "unitPrice": 4500,
        "quantity": 2,
        "lineAmount": 9000
      },
      {
        "orderItemId": 102,
        "menuId": 3,
        "menuName": "크로와상",
        "menuCategory": "BAKERY",
        "unitPrice": 3500,
        "quantity": 1,
        "lineAmount": 3500
      }
    ],
    "totalAmount": 12500,
    "payment": {
      "paymentId": 70,
      "paymentMethod": "POINT",
      "status": "COMPLETED",
      "amount": 12500,
      "paidAt": "2026-07-13T14:00:00",
      "cancelledAt": null
    },
    "orderedAt": "2026-07-13T14:00:00"
  }
}
```

### Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `ORDER_FORBIDDEN` | 본인의 주문이 아님 |
| `404` | `ORDER_NOT_FOUND` | 주문을 찾을 수 없음 |

---

## 5-4. 전체 주문 목록 조회

- Method: `GET`
- Path: `/api/v1/admin/orders`
- Auth: 필요
- Role: `ADMIN`

### Query Parameters

| 필드 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---:|---:|---|
| `memberId` | Long | N | 전체 | 특정 회원 주문 조회 |
| `status` | String | N | 전체 | 주문 상태 |
| `orderChannel` | String | N | 전체 | 주문 채널 |
| `startDateTime` | DateTime | N | 없음 | 조회 시작 시각 |
| `endDateTime` | DateTime | N | 없음 | 조회 종료 시각 |
| `page` | Integer | N | `0` | 페이지 번호 |
| `size` | Integer | N | `20` | 페이지 크기 |
| `sort` | String | N | `orderedAt,desc` | 정렬 기준 |

### Response

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "전체 주문 목록 조회에 성공했습니다.",
  "data": {
    "content": [
      {
        "orderId": 50,
        "orderNumber": "ORD-20260713-000050",
        "memberId": 1,
        "memberEmail": "test@example.com",
        "status": "COMPLETED",
        "orderChannel": "WEB_CART",
        "totalAmount": 12500,
        "orderedAt": "2026-07-13T14:00:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

### Error

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_ORDER_STATUS` | 유효하지 않은 주문 상태 |
| `400` | `INVALID_ORDER_CHANNEL` | 유효하지 않은 주문 채널 |
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `FORBIDDEN` | 관리자 권한 없음 |

---

## 5-5. 관리자 주문 상세 조회

- Method: `GET`
- Path: `/api/v1/admin/orders/{orderId}`
- Auth: 필요
- Role: `ADMIN`

### 처리 정책

- 관리자는 모든 회원의 주문을 조회할 수 있다.
- 주문 회원 정보, 결제 정보 및 주문 항목을 포함한다.

### Response

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "주문 상세 조회에 성공했습니다.",
  "data": {
    "orderId": 50,
    "orderNumber": "ORD-20260713-000050",
    "member": {
      "memberId": 1,
      "email": "test@example.com",
      "name": "홍길동"
    },
    "orderChannel": "WEB_CART",
    "status": "COMPLETED",
    "items": [
      {
        "orderItemId": 101,
        "menuId": 1,
        "menuName": "아메리카노",
        "menuCategory": "COFFEE",
        "unitPrice": 4500,
        "quantity": 2,
        "lineAmount": 9000
      }
    ],
    "totalAmount": 9000,
    "payment": {
      "paymentId": 70,
      "paymentMethod": "POINT",
      "status": "COMPLETED",
      "amount": 9000,
      "paidAt": "2026-07-13T14:00:00"
    },
    "orderedAt": "2026-07-13T14:00:00"
  }
}
```

### Error

| Status | Code | 설명 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `403` | `FORBIDDEN` | 관리자 권한 없음 |
| `404` | `ORDER_NOT_FOUND` | 주문을 찾을 수 없음 |

---

# 6. 인기 메뉴 API

## 6-1. 최근 7일 인기 메뉴 Top 3 조회

- Method: `GET`
- Path: `/api/v1/menus/popular`
- Auth: 불필요

### 설명

조회 시점을 기준으로 최근 7일 동안 가장 많이 주문된 메뉴 상위 3개를 조회한다.

사용자 화면에서는 `금주의 인기 메뉴`로 표시할 수 있지만,  
집계 기준은 달력상의 이번 주가 아니라 연속된 최근 7일이다.

### 처리 정책

- `COMPLETED` 상태의 주문만 집계한다.
- 취소된 주문은 집계에서 제외한다.
- 주문 수량이 아니라 메뉴별 주문 포함 횟수를 기준으로 집계한다.
- 하나의 주문에서 동일 메뉴를 여러 개 주문해도 주문 횟수는 1회로 계산한다.
- 판매 중지 또는 삭제된 메뉴도 최근 7일 내 완료 주문에 포함되었다면 집계할 수 있다.
- 인기 메뉴는 최대 3개를 반환한다.
- 집계 결과가 없으면 빈 배열을 반환한다.

### 정렬 정책

1. 주문 횟수 내림차순
2. 최근 주문 시각 내림차순
3. 메뉴 식별자 오름차순

### Response

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "인기 메뉴 조회에 성공했습니다.",
  "data": [
    {
      "rank": 1,
      "menuId": 1,
      "menuName": "아메리카노",
      "category": "COFFEE",
      "currentPrice": 4500,
      "currentStatus": "ON_SALE",
      "orderCount": 120,
      "latestOrderedAt": "2026-07-13T14:00:00"
    },
    {
      "rank": 2,
      "menuId": 2,
      "menuName": "카페라떼",
      "category": "COFFEE",
      "currentPrice": 5500,
      "currentStatus": "ON_SALE",
      "orderCount": 98,
      "latestOrderedAt": "2026-07-13T13:30:00"
    },
    {
      "rank": 3,
      "menuId": 3,
      "menuName": "크로와상",
      "category": "BAKERY",
      "currentPrice": 3500,
      "currentStatus": "ON_SALE",
      "orderCount": 75,
      "latestOrderedAt": "2026-07-13T13:00:00"
    }
  ]
}
```

### Empty Response

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "인기 메뉴 조회에 성공했습니다.",
  "data": []
}
```

---

# 7. 외부 데이터 수집 API

## 7-1. 주문 완료 이벤트 전송

- Method: `POST`
- Target: 외부 데이터 수집 플랫폼
- Auth: 외부 연동 정책에 따라 결정
- 1차 구현: 동기 HTTP 호출
- 2차 고도화: Outbox + Kafka 비동기 전송

### Request

```json
{
  "eventId": "4fb6a592-65e8-4e5d-8833-77a4ca1f661a",
  "eventType": "ORDER_COMPLETED",
  "orderId": 50,
  "orderNumber": "ORD-20260713-000050",
  "memberId": 1,
  "orderChannel": "WEB_CART",
  "items": [
    {
      "menuId": 1,
      "menuName": "아메리카노",
      "menuCategory": "COFFEE",
      "unitPrice": 4500,
      "quantity": 2,
      "lineAmount": 9000
    },
    {
      "menuId": 3,
      "menuName": "크로와상",
      "menuCategory": "BAKERY",
      "unitPrice": 3500,
      "quantity": 1,
      "lineAmount": 3500
    }
  ],
  "totalAmount": 12500,
  "paymentMethod": "POINT",
  "orderedAt": "2026-07-13T14:00:00"
}
```

### 처리 정책

- 결제가 완료된 주문만 전송한다.
- `eventId`는 이벤트 중복 처리 방지를 위한 고유 식별자이다.
- 동일 이벤트가 여러 번 전달될 수 있음을 고려한다.
- 수신 시스템은 `eventId`를 기준으로 멱등 처리한다.
- 외부 플랫폼 장애가 인기 메뉴 집계 결과에 영향을 주지 않아야 한다.
- 인기 메뉴는 내부 완료 주문 데이터를 기준으로 집계한다.

### Response Example

```json
{
  "success": true,
  "message": "주문 이벤트를 수신했습니다."
}
```

---

## 7-2. Mock 데이터 수집 API

- Method: `POST`
- Path: `/mock/v1/order-events`
- Auth: 불필요 또는 테스트용 인증
- 사용 목적: 외부 데이터 수집 플랫폼 모의 테스트

### 설명

실제 외부 데이터 수집 플랫폼 대신 주문 완료 이벤트를 수신하는 테스트용 API이다.

1차 구현에서 주문 서버는 주문 완료 후 Mock API를 동기 호출한다.

### Request

```json
{
  "eventId": "4fb6a592-65e8-4e5d-8833-77a4ca1f661a",
  "eventType": "ORDER_COMPLETED",
  "orderId": 50,
  "memberId": 1,
  "totalAmount": 12500,
  "orderedAt": "2026-07-13T14:00:00"
}
```

### 정상 Response

```json
{
  "success": true,
  "message": "주문 이벤트를 수신했습니다."
}
```

### 테스트 시나리오

#### 정상 응답

```text
즉시 200 OK 반환
```

#### 지연 응답

```text
300ms 또는 3초 지연 후 200 OK 반환
```

#### 서버 오류

```text
500 Internal Server Error 반환
```

#### 타임아웃

```text
설정된 타임아웃보다 늦게 응답하거나 응답하지 않음
```

### 목적

- 외부 시스템 지연이 주문 응답시간에 미치는 영향 확인
- 외부 시스템 장애가 주문 성공률에 미치는 영향 확인
- 동기 HTTP 호출과 Kafka 비동기 처리 성능 비교
- 주문 시스템과 외부 데이터 수집 시스템의 장애 격리 효과 검증

---

# 8. 2차 Kafka 고도화

## 8.1 주문 완료 이벤트 생성

주문 완료 시 다음 데이터를 동일한 DB 트랜잭션에서 처리한다.

```text
주문 저장
→ 주문 항목 저장
→ 결제 저장
→ 포인트 차감
→ 포인트 이력 저장
→ 장바구니 항목 삭제
→ Outbox Event 저장
→ Transaction Commit
```

---

## 8.2 Kafka Topic

초기 Topic은 다음과 같이 사용한다.

```text
order.completed
```

### Event Type

```text
ORDER_COMPLETED
```

---

## 8.3 Kafka Event Payload

```json
{
  "eventId": "4fb6a592-65e8-4e5d-8833-77a4ca1f661a",
  "eventType": "ORDER_COMPLETED",
  "aggregateType": "ORDER",
  "aggregateId": 50,
  "orderId": 50,
  "orderNumber": "ORD-20260713-000050",
  "memberId": 1,
  "orderChannel": "WEB_CART",
  "items": [
    {
      "menuId": 1,
      "menuName": "아메리카노",
      "menuCategory": "COFFEE",
      "unitPrice": 4500,
      "quantity": 2,
      "lineAmount": 9000
    }
  ],
  "totalAmount": 9000,
  "paymentMethod": "POINT",
  "orderedAt": "2026-07-13T14:00:00",
  "occurredAt": "2026-07-13T14:00:00"
}
```

---

## 8.4 Kafka 처리 정책

- 주문 트랜잭션 안에서 Kafka에 직접 발행하지 않는다.
- 주문 데이터와 Outbox Event를 동일한 DB 트랜잭션에서 저장한다.
- 별도의 Outbox Publisher가 `PENDING` 이벤트를 조회한다.
- Publisher가 Kafka 발행에 성공하면 상태를 `PUBLISHED`로 변경한다.
- 발행 실패 시 `retry_count`, `next_retry_at`, `last_error`를 갱신한다.
- 재시도 한도를 초과하면 상태를 `FAILED`로 변경한다.
- Kafka 발행 실패가 완료된 주문 상태를 변경하지 않는다.
- Consumer는 `eventId`를 기준으로 중복 소비를 방지한다.
- Consumer 처리 실패 시 재시도 또는 Dead Letter Topic을 사용한다.
- Topic, Partition, Event Key, Consumer Group 세부 정책은 `ARCHITECTURE.md`에서 정의한다.

---

# 9. 멱등성 정책

## 9.1 주문 멱등키

주문 API는 중복 주문 및 중복 결제를 방지하기 위해 멱등키를 사용한다.

```http
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
```

### 처리 정책

- 클라이언트는 주문 시 고유한 멱등키를 생성한다.
- 서버는 `(memberId, idempotencyKey)` 조합을 기준으로 중복 여부를 판단한다.
- 동일 회원이 동일 멱등키로 요청하면 새로운 주문과 결제를 생성하지 않는다.
- 이미 완료된 주문이 있으면 기존 주문 결과를 반환한다.
- 동일 멱등키로 서로 다른 주문 내용을 요청한 경우 충돌로 처리할 수 있다.
- 멱등키 저장 및 만료 정책은 `ARCHITECTURE.md`에서 정의한다.

---

# 10. 권한 정책

| API 영역 | USER | ADMIN | 비로그인 |
|---|---:|---:|---:|
| 회원가입 | O | O | O |
| 로그인 | O | O | O |
| 메뉴 목록 조회 | O | O | O |
| 메뉴 상세 조회 | O | O | O |
| 메뉴 등록 | X | O | X |
| 메뉴 수정 | X | O | X |
| 메뉴 상태 변경 | X | O | X |
| 메뉴 삭제 | X | O | X |
| 장바구니 관리 | O | O | X |
| 포인트 충전 및 조회 | O | O | X |
| 주문 및 결제 | O | O | X |
| 내 주문 조회 | O | O | X |
| 전체 주문 조회 | X | O | X |
| 인기 메뉴 조회 | O | O | O |

---

# 11. 공통 에러 코드

## 11.1 인증 및 회원

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_INPUT` | 입력값 검증 실패 |
| `401` | `UNAUTHORIZED` | 인증되지 않은 사용자 |
| `401` | `INVALID_TOKEN` | 유효하지 않은 Access Token |
| `401` | `EXPIRED_TOKEN` | 만료된 Access Token |
| `401` | `BLACKLISTED_TOKEN` | 로그아웃 처리된 Access Token |
| `401` | `INVALID_REFRESH_TOKEN` | 유효하지 않은 Refresh Token |
| `401` | `EXPIRED_REFRESH_TOKEN` | 만료된 Refresh Token |
| `401` | `REFRESH_TOKEN_NOT_FOUND` | Redis에 없는 Refresh Token |
| `401` | `INVALID_LOGIN` | 이메일 또는 비밀번호 불일치 |
| `403` | `FORBIDDEN` | 접근 권한 부족 |
| `403` | `MEMBER_INACTIVE` | 비활성화된 회원 |
| `403` | `MEMBER_WITHDRAWN` | 탈퇴한 회원 |
| `404` | `MEMBER_NOT_FOUND` | 회원을 찾을 수 없음 |
| `409` | `MEMBER_ALREADY_WITHDRAWN` | 이미 탈퇴한 회원 |
| `409` | `DUPLICATED_EMAIL` | 이미 사용 중인 이메일 |

---

## 11.2 메뉴

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_MENU_CATEGORY` | 유효하지 않은 메뉴 카테고리 |
| `400` | `INVALID_MENU_PRICE` | 메뉴 가격이 0 이하 |
| `400` | `INVALID_MENU_STATUS` | 유효하지 않은 메뉴 상태 |
| `404` | `MENU_NOT_FOUND` | 메뉴를 찾을 수 없음 |
| `409` | `MENU_NOT_ON_SALE` | 판매 중인 메뉴가 아님 |
| `409` | `MENU_ALREADY_DELETED` | 이미 삭제된 메뉴 |

---

## 11.3 장바구니

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_QUANTITY` | 수량이 1 미만 |
| `400` | `CART_ITEM_QUANTITY_EXCEEDED` | 메뉴별 최대 수량 초과 |
| `400` | `CART_EMPTY` | 장바구니가 비어 있음 |
| `403` | `CART_ITEM_FORBIDDEN` | 본인의 장바구니 항목이 아님 |
| `404` | `CART_NOT_FOUND` | 장바구니를 찾을 수 없음 |
| `404` | `CART_ITEM_NOT_FOUND` | 장바구니 항목을 찾을 수 없음 |

---

## 11.4 포인트

| Status | Code | 설명 |
|---:|---|---|
| `400` | `INVALID_POINT_AMOUNT` | 충전 금액이 0 이하 |
| `400` | `POINT_NOT_ENOUGH` | 보유 포인트 부족 |
| `400` | `POINT_CHARGE_LIMIT_EXCEEDED` | 충전 한도 초과 |
| `404` | `POINT_ACCOUNT_NOT_FOUND` | 포인트 계정을 찾을 수 없음 |
| `409` | `POINT_UPDATE_CONFLICT` | 포인트 변경 중 동시성 충돌 |

---

## 11.5 주문 및 결제

| Status | Code | 설명 |
|---:|---|---|
| `400` | `IDEMPOTENCY_KEY_REQUIRED` | 주문 멱등키 누락 |
| `400` | `INVALID_ORDER_STATUS` | 유효하지 않은 주문 상태 |
| `400` | `INVALID_ORDER_CHANNEL` | 유효하지 않은 주문 채널 |
| `404` | `ORDER_NOT_FOUND` | 주문을 찾을 수 없음 |
| `403` | `ORDER_FORBIDDEN` | 본인의 주문이 아님 |
| `409` | `ORDER_ALREADY_PROCESSED` | 이미 처리된 주문 |
| `409` | `ORDER_PROCESSING_CONFLICT` | 주문 처리 중 동시성 충돌 |
| `404` | `PAYMENT_NOT_FOUND` | 결제를 찾을 수 없음 |
| `409` | `PAYMENT_ALREADY_COMPLETED` | 이미 완료된 결제 |
| `409` | `DUPLICATED_PAYMENT` | 중복 결제 요청 |

---

## 11.6 외부 데이터 전송

| Status | Code | 설명 |
|---:|---|---|
| `502` | `EXTERNAL_DATA_COLLECTION_FAILED` | 외부 데이터 수집 API 호출 실패 |
| `504` | `EXTERNAL_DATA_COLLECTION_TIMEOUT` | 외부 데이터 수집 API 타임아웃 |
| `500` | `OUTBOX_EVENT_SAVE_FAILED` | Outbox Event 저장 실패 |
| `500` | `KAFKA_PUBLISH_FAILED` | Kafka 발행 실패 |
| `409` | `DUPLICATED_EVENT` | 중복 이벤트 처리 |

---

# 12. API 목록 요약

## 12.1 인증 / 회원

| Method | Path | 설명 | Auth |
|---|---|---|---|
| `POST` | `/api/v1/auth/signup` | 회원가입 | 불필요 |
| `POST` | `/api/v1/auth/login` | 로그인 | 불필요 |
| `POST` | `/api/v1/auth/logout` | 로그아웃 | 필요 |
| `POST` | `/api/v1/auth/reissue` | 토큰 재발급 | Refresh Token |
| `GET` | `/api/v1/members/me` | 내 정보 조회 | 필요 |
| `DELETE` | `/api/v1/members/me` | 회원 탈퇴 | 필요 |

---

## 12.2 메뉴

| Method | Path | 설명 | 권한 |
|---|---|---|---|
| `GET` | `/api/v1/menus` | 메뉴 목록 조회 | 공개 |
| `GET` | `/api/v1/menus/{menuId}` | 메뉴 상세 조회 | 공개 |
| `POST` | `/api/v1/admin/menus` | 메뉴 등록 | ADMIN |
| `PATCH` | `/api/v1/admin/menus/{menuId}` | 메뉴 수정 | ADMIN |
| `PATCH` | `/api/v1/admin/menus/{menuId}/status` | 메뉴 상태 변경 | ADMIN |
| `DELETE` | `/api/v1/admin/menus/{menuId}` | 메뉴 삭제 | ADMIN |

---

## 12.3 장바구니

| Method | Path | 설명 | 권한 |
|---|---|---|---|
| `GET` | `/api/v1/cart` | 장바구니 조회 | USER |
| `POST` | `/api/v1/cart/items` | 메뉴 추가 | USER |
| `PATCH` | `/api/v1/cart/items/{cartItemId}` | 수량 변경 | USER |
| `DELETE` | `/api/v1/cart/items/{cartItemId}` | 항목 삭제 | USER |
| `DELETE` | `/api/v1/cart/items` | 전체 비우기 | USER |

---

## 12.4 포인트

| Method | Path | 설명 | 권한 |
|---|---|---|---|
| `GET` | `/api/v1/points` | 포인트 잔액 조회 | USER |
| `POST` | `/api/v1/points/charge` | 포인트 충전 | USER |
| `GET` | `/api/v1/points/histories` | 포인트 이력 조회 | USER |

---

## 12.5 주문 / 결제

| Method | Path | 설명 | 권한 |
|---|---|---|---|
| `POST` | `/api/v1/orders` | 장바구니 주문 및 포인트 결제 | USER |
| `GET` | `/api/v1/orders` | 내 주문 목록 조회 | USER |
| `GET` | `/api/v1/orders/{orderId}` | 내 주문 상세 조회 | USER |
| `GET` | `/api/v1/admin/orders` | 전체 주문 목록 조회 | ADMIN |
| `GET` | `/api/v1/admin/orders/{orderId}` | 관리자 주문 상세 조회 | ADMIN |

---

## 12.6 인기 메뉴

| Method | Path | 설명 | Auth |
|---|---|---|---|
| `GET` | `/api/v1/menus/popular` | 최근 7일 인기 메뉴 Top 3 | 불필요 |

---

## 12.7 외부 데이터 수집

| Method | Path | 설명 | 용도 |
|---|---|---|---|
| `POST` | `/mock/v1/order-events` | 주문 완료 이벤트 수신 | 1차 Mock API |
| Kafka | `order.completed` | 주문 완료 이벤트 발행 | 2차 고도화 |

---

# 13. 후속 결정 사항

다음 내용은 구현 전 또는 구현 과정에서 확정한다.

- 비밀번호 길이 및 복잡도 정책
- Access Token 유효기간
- Refresh Token 유효기간
- Refresh Token Rotation 정책
- 장바구니 메뉴별 최대 수량
- 포인트 1회 충전 최소 금액
- 포인트 1회 충전 최대 금액
- 사용자 보유 가능 최대 포인트
- 주문 멱등키 보관 기간
- 동일 멱등키와 다른 요청 내용 충돌 처리 방식
- 외부 Mock API 주소와 타임아웃 설정
- 외부 API 재시도 여부
- Outbox Publisher 실행 주기
- Kafka Topic 이름
- Kafka Partition 수
- Kafka Event Key
- Kafka Producer 재시도 정책
- Kafka Consumer Group 구성
- Consumer 멱등성 처리 방식
- Dead Letter Topic 운영 정책
- 주문 취소 및 포인트 환불 기능 구현 여부
- 카드 및 간편결제 연동 여부
