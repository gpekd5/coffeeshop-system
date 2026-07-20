# API 검증 오류 응답 설계

## 1. 문제 배경

API 요청값 검증에 실패했을 때 클라이언트는 어떤 필드가 왜 실패했는지 알아야 한다.

단순히 다음과 같이만 응답하면 프론트엔드가 구체적인 오류를 표시하기 어렵다.

```json
{
  "success": false,
  "code": "INVALID_INPUT",
  "message": "입력값이 올바르지 않습니다.",
  "data": null
}
```

예를 들어 회원가입 요청에서 이메일과 비밀번호가 동시에 잘못됐을 때, 어떤 필드가 실패했는지 알 수 없다.

---

## 2. 선택한 응답 구조

검증 오류는 공통 응답의 `data.errors[]`에 상세 목록을 담는다.

```json
{
  "success": false,
  "code": "INVALID_INPUT",
  "message": "입력값이 올바르지 않습니다.",
  "data": {
    "errors": [
      {
        "field": "email",
        "message": "이메일 형식이 올바르지 않습니다."
      }
    ]
  }
}
```

이 구조를 사용하면 클라이언트는 필드별 오류 메시지를 화면에 표시할 수 있다.

---

## 3. 처리해야 하는 검증 실패 종류

Spring MVC에서는 요청 검증 실패가 여러 예외로 나뉜다.

| 예외 | 발생 상황 |
| --- | --- |
| `MethodArgumentNotValidException` | `@RequestBody @Valid` 검증 실패 |
| `BindException` | ModelAttribute, query binding 검증 실패 |
| `ConstraintViolationException` | 메서드 파라미터 제약 위반 |
| `HandlerMethodValidationException` | Spring 6 방식의 메서드 파라미터 검증 실패 |
| `MissingServletRequestParameterException` | 필수 Query Parameter 누락 |

처음에는 Body 검증만 처리해도 충분해 보이지만, Query Parameter와 Path Variable 검증까지 고려하면 구조가 분리될 수 있다.

따라서 공통 변환 DTO를 따로 둔다.

---

## 4. 구현 전략

검증 오류 상세는 `ValidationErrorResponse`가 담당한다.

```text
GlobalExceptionHandler
→ 예외 종류별 handler
→ ValidationErrorResponse 생성
→ ApiResponse.error(errorCode, response)
```

Controller는 검증 실패 응답 형식을 직접 알 필요가 없다.

모든 검증 실패 응답은 `GlobalExceptionHandler`에서 공통 처리한다.

---

## 5. 핵심 코드 위치

| 코드 | 역할 |
| --- | --- |
| `src/main/java/com/example/coffeeorder/common/response/ApiResponse.java` | 공통 성공/오류 응답 형식 |
| `src/main/java/com/example/coffeeorder/common/response/ValidationErrorResponse.java` | `errors[]` 상세 구조 |
| `src/main/java/com/example/coffeeorder/common/exception/GlobalExceptionHandler.java` | 검증 예외를 공통 응답으로 변환 |
| `docs/API_SPEC.md` | 검증 오류 응답 명세 |

---

## 6. 응답 구조의 장점

| 장점 | 설명 |
| --- | --- |
| 일관성 | Body, Query, Path 검증 실패가 같은 구조를 사용 |
| 클라이언트 친화적 | 필드별 오류 메시지를 화면에 바로 매핑 가능 |
| 확장성 | field, message 외에 rejectedValue 등을 나중에 추가 가능 |
| 보안 | 내부 예외 정보나 Stack Trace를 노출하지 않음 |

---

## 7. 테스트로 검증한 내용

관련 테스트에서는 다음을 확인한다.

| 테스트 관점 | 검증 내용 |
| --- | --- |
| Request Body 검증 실패 | `data.errors[]` 반환 |
| Query Parameter 검증 실패 | 필드명과 메시지 반환 |
| 필수 파라미터 누락 | 누락된 파라미터명이 errors에 포함 |
| 공통 응답 구조 | `success`, `code`, `message`, `data` 형식 유지 |

---

## 실제 적용 코드

### 적용 파일 경로

| 파일 | 역할 |
| --- | --- |
| `src/main/java/com/example/coffeeorder/common/exception/GlobalExceptionHandler.java` | 검증 예외를 `ApiResponse<ValidationErrorResponse>`로 변환 |
| `src/main/java/com/example/coffeeorder/common/response/ValidationErrorResponse.java` | `data.errors[]` 응답 DTO와 필드명 추출 |
| `src/main/java/com/example/coffeeorder/common/response/ApiResponse.java` | 공통 성공/실패 응답 래퍼 |

### 호출 흐름

```text
Controller 요청 검증 실패
→ MethodArgumentNotValidException / BindException
→ ConstraintViolationException / HandlerMethodValidationException
→ MissingServletRequestParameterException
→ GlobalExceptionHandler
→ ValidationErrorResponse
→ ApiResponse.error(errorCode, response)
```

### 핵심 코드만 짧게 발췌

```java
@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<ApiResponse<ValidationErrorResponse>> handleMethodArgumentNotValidException(...) {
    ValidationErrorResponse response =
            ValidationErrorResponse.from(exception.getBindingResult());

    return ResponseEntity.status(errorCode.getStatus())
            .body(ApiResponse.error(errorCode, response));
}
```

```java
@ExceptionHandler(HandlerMethodValidationException.class)
public ResponseEntity<ApiResponse<ValidationErrorResponse>> handleHandlerMethodValidationException(...) {
    ValidationErrorResponse response =
            ValidationErrorResponse.fromParameterValidationResults(
                    exception.getParameterValidationResults()
            );
    return ResponseEntity.status(errorCode.getStatus())
            .body(ApiResponse.error(errorCode, response));
}
```

```java
public record ValidationErrorResponse(List<FieldErrorResponse> errors) {
    public record FieldErrorResponse(String field, String message) { }
}
```

### 이 코드가 해당 개념을 구현하는 이유

요청 Body 검증 실패뿐 아니라 Query Parameter, Path Variable, 필수 파라미터 누락도 같은 `ValidationErrorResponse`로 모은다. `ApiResponse.error(errorCode, response)`를 사용하므로 공통 에러 응답의 `data`에 항상 `errors[]`가 들어가고, 클라이언트는 검증 실패 위치와 메시지를 일관된 구조로 처리할 수 있다.

---

## 8. 한계와 개선 방향

현재 구조는 검증 오류 메시지를 서버에서 문자열로 내려준다.

후속 개선으로 다음을 고려할 수 있다.

| 개선 | 설명 |
| --- | --- |
| 메시지 코드 분리 | 다국어 처리를 위해 message code를 함께 반환 |
| rejectedValue 제한 | 민감 정보는 오류 응답에 포함하지 않도록 필터링 |
| 오류 정렬 | 클라이언트 표시 순서를 위해 필드 순서 보장 |

검증 오류 응답은 작은 기능처럼 보이지만, API 사용성과 클라이언트 개발 경험에 직접 영향을 준다.
