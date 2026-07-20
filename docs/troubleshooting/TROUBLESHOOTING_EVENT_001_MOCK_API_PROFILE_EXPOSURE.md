# Mock API 프로필 노출 문제

## 1. 증상

다음 상황이 보이면 Mock API 노출 설정을 확인한다.

| 증상 | 예시 |
| --- | --- |
| 운영에서 `/mock/v1/order-events`가 응답함 | 인증 없이 Mock API 호출 가능 |
| 운영 Consumer가 local/mock URL로 외부 이벤트를 전송함 | `localhost` 또는 `/mock/v1/order-events`로 전송 |
| 큰 지연 요청으로 요청 처리 스레드가 오래 점유됨 | `delayMillis`가 과도하게 전달됨 |

---

## 2. 영향

Mock API는 외부 데이터 수집 플랫폼 장애를 재현하기 위한 로컬/테스트용 기능이다.

운영에서 노출되면 인증되지 않은 외부 사용자가 실패 응답이나 지연을 인위적으로 만들 수 있다.
특히 `delayMillis`가 제한되지 않으면 요청 처리 스레드가 오래 묶일 수 있다.

---

## 3. 먼저 확인할 것

| 확인 대상 | 정상 기준 |
| --- | --- |
| 활성 프로필 | 운영에서는 `prod`, 로컬에서는 `local` |
| Mock API 프로퍼티 | 운영에서는 `APP_MOCK_ORDER_EVENT_ENABLED=false` |
| 컨트롤러 프로필 | `MockOrderEventController`는 `local`, `test`에서만 등록 |
| 지연 파라미터 | `delayMillis`는 0 이상 3000 이하 |
| Consumer 외부 URL | 운영 Consumer는 local/mock URL 사용 금지 |

---

## 4. 원인별 확인 방법

### 운영에서 local 또는 test 프로필이 섞인 경우

운영 실행 옵션에 다음 값이 들어갔는지 확인한다.

```text
SPRING_PROFILES_ACTIVE=local
SPRING_PROFILES_ACTIVE=test
```

운영에서는 `prod`만 사용하고, Mock API 컨트롤러가 등록되지 않아야 한다.

### Mock API 활성화 값이 잘못된 경우

`application.yaml`의 기본값은 false이고, `application-local.yaml`에서만 기본 true다.

```text
app.mock-order-event.enabled=false
```

운영 환경변수로 `APP_MOCK_ORDER_EVENT_ENABLED=true`가 들어가면 제거한다.

### Consumer 외부 URL이 Mock으로 잡힌 경우

운영에서 Kafka Consumer를 켜는 경우 다음 값이 실제 외부 데이터 수집 플랫폼 주소여야 한다.

```text
APP_KAFKA_ORDER_EVENT_CONSUMER_ENABLED=true
APP_EXTERNAL_ORDER_EVENT_BASE_URL=https://...
APP_EXTERNAL_ORDER_EVENT_PATH=/...
```

`localhost`, `127.0.0.1`, `0.0.0.0`, `/mock/v1/order-events`는 운영 Consumer 시작 단계에서 거부해야 한다.

---

## 5. 복구 또는 대응

| 상황 | 대응 |
| --- | --- |
| 운영 Mock API 접근 가능 | 프로필과 `APP_MOCK_ORDER_EVENT_ENABLED`를 수정 후 재기동 |
| 운영 Consumer가 Mock URL 사용 | 외부 이벤트 URL을 실제 운영 URL로 변경 |
| 지연 파라미터 공격 우려 | `delayMillis` 상한과 Mock API 비활성화 상태를 재확인 |
| 로컬 테스트가 Mock API를 못 찾음 | `local` 또는 `test` 프로필과 `APP_MOCK_ORDER_EVENT_ENABLED=true` 확인 |

---

## 6. 관련 코드와 테스트

| 위치 | 설명 |
| --- | --- |
| [MockOrderEventController.java](../../src/main/java/com/example/coffeeorder/event/controller/MockOrderEventController.java) | Mock API 프로필, 활성화 조건, 파라미터 상한 |
| [SecurityConfig.java](../../src/main/java/com/example/coffeeorder/common/security/SecurityConfig.java) | Mock API 공개 경로 설정 |
| [ProdExternalOrderEventPropertiesValidator.java](../../src/main/java/com/example/coffeeorder/event/kafka/config/ProdExternalOrderEventPropertiesValidator.java) | 운영 Consumer 외부 URL 검증 |
| [MockOrderEventControllerIntegrationTest.java](../../src/test/java/com/example/coffeeorder/event/controller/MockOrderEventControllerIntegrationTest.java) | Mock API 동작과 파라미터 검증 |
| [MockOrderEventControllerDisabledIntegrationTest.java](../../src/test/java/com/example/coffeeorder/event/controller/MockOrderEventControllerDisabledIntegrationTest.java) | Mock API 비활성화 검증 |
| [ProdExternalOrderEventPropertiesValidatorTest.java](../../src/test/java/com/example/coffeeorder/event/kafka/config/ProdExternalOrderEventPropertiesValidatorTest.java) | 운영 local/mock URL 차단 검증 |

관련 기술 문서: [외부 API 장애 처리와 Mock API 전략](../wiki/EXTERNAL_ORDER_EVENT_AND_MOCK_API.md)
