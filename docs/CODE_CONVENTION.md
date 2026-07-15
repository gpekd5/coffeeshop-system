# Code Convention

이 문서는 커피 주문 및 포인트 결제 시스템의 코드 작성 규칙을 정의한다.

패키지 구조와 시스템 흐름은 `ARCHITECTURE.md`,  
API 요청과 응답 형식은 `API_SPEC.md`,  
데이터 구조는 `ERD.md`를 기준으로 한다.

AI를 이용해 코드를 생성하더라도 이 문서의 규칙을 우선 적용한다.

---

# 1. 기본 원칙

- 기능별 도메인 패키지 구조를 사용한다.
- Controller는 HTTP 요청과 응답만 담당한다.
- Controller에서 Repository를 직접 호출하지 않는다.
- Entity를 Request 또는 Response로 직접 사용하지 않는다.
- Request DTO와 Response DTO를 분리한다.
- Entity의 상태는 의미 있는 도메인 메서드로 변경한다.
- 외부 네트워크 호출은 DB 트랜잭션 안에서 실행하지 않는다.
- 하나의 클래스가 너무 많은 책임을 가지지 않도록 한다.
- 실제로 여러 도메인에서 공유되는 코드만 `common` 패키지에 둔다.
- 모든 REST API 응답은 `ResponseEntity<ApiResponse<T>>` 형식으로 통일한다.

---

# 2. 네이밍 규칙

## 2.1 패키지

패키지 이름은 모두 소문자로 작성한다.

```text
auth
member
menu
cart
point
order
payment
event
common
```

여러 단어가 필요한 경우에도 언더스코어를 사용하지 않는다.

```text
비권장
point_history
order_event

권장
point.history
event.outbox
event.kafka
```

---

## 2.2 클래스와 인터페이스

클래스와 인터페이스는 `PascalCase`를 사용한다.

```text
Member
MenuController
PointService
OrderRepository
OrderCreateRequest
OrderCreateResponse
```

---

## 2.3 메서드와 변수

메서드와 변수는 `camelCase`를 사용한다.

```java
createOrder();
findMenuById();
calculateTotalAmount();

memberId;
totalAmount;
idempotencyKey;
```

---

## 2.4 상수

상수는 대문자와 언더스코어를 사용한다.

```java
private static final int DEFAULT_PAGE_SIZE = 20;
private static final long MIN_POINT_CHARGE_AMOUNT = 1_000L;
```

---

## 2.5 Boolean

Boolean 변수와 메서드는 상태가 명확하게 드러나도록 작성한다.

```java
boolean active;
boolean deleted;
boolean onSale;

isActive();
isDeleted();
isOnSale();
```

다음과 같이 의미가 불분명한 이름은 사용하지 않는다.

```java
boolean check;
boolean flag;
boolean result;
```

---

# 3. 클래스별 네이밍

| 역할 | 형식 | 예시 |
|---|---|---|
| 일반 Controller | `{Domain}Controller` | `MenuController` |
| 관리자 Controller | `Admin{Domain}Controller` | `AdminMenuController` |
| Service | `{Domain}Service` | `PointService` |
| 트랜잭션 Service | `{UseCase}TransactionService` | `OrderTransactionService` |
| Facade | `{UseCase}Facade` | `OrderFacade` |
| Repository | `{Entity}Repository` | `OrderRepository` |
| QueryDSL Repository | `{Domain}QueryRepository` | `OrderQueryRepository` |
| QueryDSL 구현체 | `{Domain}QueryRepositoryImpl` | `OrderQueryRepositoryImpl` |
| 외부 API Client | `{Target}Client` | `ExternalOrderEventClient` |
| Request DTO | `{Action}Request` | `MenuCreateRequest` |
| Response DTO | `{Action}Response` | `OrderDetailResponse` |
| 비즈니스 예외 | `BusinessException` | `BusinessException` |
| Enum | 역할이 드러나는 명사 | `OrderStatus` |

---

# 4. Controller 규칙

Controller는 HTTP 요청을 받고 Service 또는 Facade를 호출한 뒤  
`ResponseEntity<ApiResponse<T>>` 형식으로 응답한다.

## 4.1 기본 반환 형식

모든 REST Controller는 다음 반환 형식을 사용한다.

```java
ResponseEntity<ApiResponse<T>>
```

단건 응답:

```java
public ResponseEntity<ApiResponse<MenuResponse>> getMenu(...)
```

목록 응답:

```java
public ResponseEntity<ApiResponse<List<PopularMenuResponse>>> getPopularMenus(...)
```

페이지 응답:

```java
public ResponseEntity<ApiResponse<PageResponse<MenuResponse>>> getMenus(...)
```

응답 데이터가 없는 경우:

```java
public ResponseEntity<ApiResponse<Void>> deleteMenu(...)
```

---

## 4.2 조회 API

조회 성공 시 `ResponseEntity.ok()`를 사용한다.

```java
@GetMapping("/{menuId}")
public ResponseEntity<ApiResponse<MenuResponse>> getMenu(
        @PathVariable Long menuId
) {
    MenuResponse response = menuService.getMenu(menuId);

    return ResponseEntity.ok(
            ApiResponse.success(
                    "메뉴 상세 조회에 성공했습니다.",
                    response
            )
    );
}
```

---

## 4.3 목록 조회 API

목록 조회는 `List<Response>` 또는 공통 페이지 응답을 사용한다.

```java
@GetMapping("/popular")
public ResponseEntity<ApiResponse<List<PopularMenuResponse>>> getPopularMenus() {
    List<PopularMenuResponse> response =
            menuService.getPopularMenus();

    return ResponseEntity.ok(
            ApiResponse.success(
                    "인기 메뉴 조회에 성공했습니다.",
                    response
            )
    );
}
```

페이징 응답은 공통 `PageResponse<T>`로 감싼다.

```java
@GetMapping
public ResponseEntity<ApiResponse<PageResponse<MenuResponse>>> getMenus(
        @PageableDefault(
                size = 20,
                sort = "createdAt",
                direction = Sort.Direction.DESC
        ) Pageable pageable
) {
    PageResponse<MenuResponse> response =
            menuService.getMenus(pageable);

    return ResponseEntity.ok(
            ApiResponse.success(
                    "메뉴 목록 조회에 성공했습니다.",
                    response
            )
    );
}
```

---

## 4.4 생성 API

리소스 생성 성공 시 `201 Created`를 사용한다.

```java
@PostMapping
public ResponseEntity<ApiResponse<MenuResponse>> createMenu(
        @Valid @RequestBody MenuCreateRequest request
) {
    MenuResponse response =
            menuService.createMenu(request);

    return ResponseEntity.status(HttpStatus.CREATED)
            .body(
                    ApiResponse.success(
                            "메뉴가 등록되었습니다.",
                            response
                    )
            );
}
```

---

## 4.5 수정 API

수정 성공 시 기본적으로 `200 OK`를 사용한다.

```java
@PatchMapping("/{menuId}")
public ResponseEntity<ApiResponse<MenuResponse>> updateMenu(
        @PathVariable Long menuId,
        @Valid @RequestBody MenuUpdateRequest request
) {
    MenuResponse response =
            menuService.updateMenu(menuId, request);

    return ResponseEntity.ok(
            ApiResponse.success(
                    "메뉴가 수정되었습니다.",
                    response
            )
    );
}
```

---

## 4.6 삭제 API

삭제 후 반환 데이터가 없으면 `ApiResponse<Void>`를 사용한다.

```java
@DeleteMapping("/{menuId}")
public ResponseEntity<ApiResponse<Void>> deleteMenu(
        @PathVariable Long menuId
) {
    menuService.deleteMenu(menuId);

    return ResponseEntity.ok(
            ApiResponse.success(
                    "메뉴가 삭제되었습니다.",
                    null
            )
    );
}
```

본 프로젝트에서는 응답 형식을 통일하기 위해  
삭제 API도 `204 No Content`보다 `200 OK + ApiResponse<Void>`를 기본으로 사용한다.

---

## 4.7 인증 사용자 전달

인증 사용자는 `@AuthenticationPrincipal`을 통해 전달한다.

```java
@PostMapping
public ResponseEntity<ApiResponse<OrderCreateResponse>> createOrder(
        @AuthenticationPrincipal AuthMember authMember,
        @RequestHeader("Idempotency-Key") String idempotencyKey
) {
    OrderCreateResponse response =
            orderFacade.createOrder(
                    authMember.memberId(),
                    idempotencyKey
            );

    return ResponseEntity.status(HttpStatus.CREATED)
            .body(
                    ApiResponse.success(
                            "주문과 결제가 완료되었습니다.",
                            response
                    )
            );
}
```

인증이 필요한 API에서 회원 식별자를 Request로 받지 않는다.

```text
권장
@AuthenticationPrincipal AuthMember authMember

비권장
@RequestParam Long memberId
@RequestBody Long memberId
```

---

## 4.8 선택적 인증

비로그인 사용자도 호출할 수 있는 API는 인증 사용자가 없을 수 있음을 고려한다.

```java
@GetMapping("/{menuId}")
public ResponseEntity<ApiResponse<MenuResponse>> getMenu(
        @PathVariable Long menuId,
        @AuthenticationPrincipal AuthMember authMember
) {
    Long memberId =
            authMember == null ? null : authMember.memberId();

    MenuResponse response =
            menuService.getMenu(menuId, memberId);

    return ResponseEntity.ok(
            ApiResponse.success(
                    "메뉴 상세 조회에 성공했습니다.",
                    response
            )
    );
}
```

---

## 4.9 Controller 책임

Controller는 다음 역할만 담당한다.

- HTTP 요청 수신
- Header, Path Variable, Query Parameter, Body 추출
- `@Valid`를 이용한 요청값 검증
- 인증 사용자 정보 추출
- Service 또는 Facade 호출
- HTTP Status 결정
- `ResponseEntity<ApiResponse<T>>` 반환

Controller에서 다음 로직을 처리하지 않는다.

- Repository 직접 호출
- Entity 직접 생성 및 변경
- 주문 금액 계산
- 포인트 잔액 검증
- 메뉴 판매 여부 판단
- 트랜잭션 처리
- 외부 API 호출
- 복잡한 비즈니스 검증

---

## 4.10 Controller 예시

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/points")
public class PointController {

    private final PointService pointService;

    @GetMapping
    public ResponseEntity<ApiResponse<PointBalanceResponse>> getPointBalance(
            @AuthenticationPrincipal AuthMember authMember
    ) {
        PointBalanceResponse response =
                pointService.getPointBalance(
                        authMember.memberId()
                );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "포인트 잔액 조회에 성공했습니다.",
                        response
                )
        );
    }

    @PostMapping("/charge")
    public ResponseEntity<ApiResponse<PointChargeResponse>> chargePoint(
            @AuthenticationPrincipal AuthMember authMember,
            @Valid @RequestBody PointChargeRequest request
    ) {
        PointChargeResponse response =
                pointService.charge(
                        authMember.memberId(),
                        request
                );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "포인트가 충전되었습니다.",
                        response
                )
        );
    }

    @GetMapping("/histories")
    public ResponseEntity<ApiResponse<List<PointHistoryResponse>>> getHistories(
            @AuthenticationPrincipal AuthMember authMember
    ) {
        List<PointHistoryResponse> response =
                pointService.getHistories(
                        authMember.memberId()
                );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "포인트 이력 조회에 성공했습니다.",
                        response
                )
        );
    }
}
```

---

# 5. 공통 응답 규칙

모든 REST API는 `ApiResponse<T>` 형식을 사용한다.

## 5.1 성공 응답

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {}
}
```

## 5.2 실패 응답

```json
{
  "success": false,
  "code": "POINT_NOT_ENOUGH",
  "message": "보유 포인트가 부족합니다.",
  "data": null
}
```

## 5.3 ApiResponse 예시

```java
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiResponse<T> {

    private final boolean success;
    private final String code;
    private final String message;
    private final T data;

    public static <T> ApiResponse<T> success(
            String message,
            T data
    ) {
        return new ApiResponse<>(
                true,
                "SUCCESS",
                message,
                data
        );
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(
                true,
                "SUCCESS",
                "요청이 성공했습니다.",
                data
        );
    }

    public static ApiResponse<Void> error(
            String code,
            String message
    ) {
        return new ApiResponse<>(
                false,
                code,
                message,
                null
        );
    }
}
```

---

# 6. 페이지 응답 규칙

JPA의 `Page` 객체를 API 응답에 직접 노출하지 않는다.

공통 `PageResponse<T>`로 변환한다.

```java
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
```

Controller 반환형은 다음과 같다.

```java
ResponseEntity<ApiResponse<PageResponse<MenuResponse>>>
```

---

# 7. DTO 규칙

## 7.1 Request와 Response 분리

```text
dto
├─ request
└─ response
```

하나의 DTO를 요청과 응답에 동시에 사용하지 않는다.

```text
비권장
MenuDto

권장
MenuCreateRequest
MenuUpdateRequest
MenuResponse
```

---

## 7.2 Request DTO

Request DTO는 외부 요청값과 형식 검증만 담당한다.

```java
public record PointChargeRequest(

        @NotNull
        @Positive
        Long amount

) {
}
```

DTO에서 처리할 검증:

- Null 여부
- 문자열 길이
- 이메일 형식
- 숫자 범위
- 필수값 확인

Service 또는 Entity에서 처리할 검증:

- 포인트 잔액 부족
- 메뉴 판매 상태
- 주문 가능 여부
- 상태 전이 가능 여부
- 회원 권한 및 소유권

---

## 7.3 Response DTO

Response DTO는 API 응답에 필요한 데이터만 가진다.

Entity를 그대로 반환하지 않는다.

```java
public record MenuResponse(
        Long menuId,
        String name,
        String description,
        MenuCategory category,
        Long price,
        MenuStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
```

---

## 7.4 DTO 변환

단순 변환은 Response DTO의 정적 팩토리 메서드를 사용할 수 있다.

```java
public static MenuResponse from(Menu menu) {
    return new MenuResponse(
            menu.getId(),
            menu.getName(),
            menu.getDescription(),
            menu.getCategory(),
            menu.getPrice(),
            menu.getStatus(),
            menu.getCreatedAt(),
            menu.getUpdatedAt()
    );
}
```

여러 Entity를 조합하는 복잡한 응답은 Service 또는 별도 Mapper에서 생성할 수 있다.

---

# 8. Entity 규칙

## 8.1 기본 형태

```java
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Menu extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
}
```

---

## 8.2 Setter 사용 금지

Entity에 공개 Setter를 생성하지 않는다.

```java
비권장

menu.setPrice(5_000L);
menu.setStatus(MenuStatus.SOLD_OUT);
```

의도가 드러나는 상태 변경 메서드를 사용한다.

```java
menu.changePrice(5_000L);
menu.changeStatus(MenuStatus.SOLD_OUT);
menu.delete();
```

---

## 8.3 생성 방식

외부에서 모든 필드를 전달하는 공개 생성자보다 정적 팩토리 메서드를 우선한다.

```java
public static Menu create(
        String name,
        String description,
        MenuCategory category,
        long price
) {
    validatePrice(price);

    return new Menu(
            name,
            description,
            category,
            price,
            MenuStatus.ON_SALE
    );
}
```

생성 시점에 필수값과 기본 상태를 보장한다.

---

## 8.4 도메인 규칙 위치

Entity 자신의 상태만으로 판단할 수 있는 규칙은 Entity에 둔다.

```java
public void use(long amount) {
    validateAmount(amount);

    if (balance < amount) {
        throw new BusinessException(
                ErrorCode.POINT_NOT_ENOUGH
        );
    }

    balance -= amount;
}
```

Repository 조회나 외부 시스템이 필요한 규칙은 Service에서 처리한다.

---

## 8.5 연관관계

연관관계는 기본적으로 지연 로딩을 사용한다.

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "member_id", nullable = false)
private Member member;
```

다음 연관관계는 반드시 `LAZY`를 명시한다.

```text
@ManyToOne
@OneToOne
```

무분별한 양방향 연관관계를 만들지 않는다.

한쪽 방향으로 충분하면 단방향을 우선한다.

---

## 8.6 금액 타입

포인트와 금액은 원 단위 정수로 처리한다.

```java
Long price;
Long balance;
Long totalAmount;
```

소수점 계산이 없으므로 `double`, `float`을 사용하지 않는다.

---

# 9. Service 규칙

Service는 하나의 도메인 기능을 처리한다.

```text
MemberService
MenuService
CartService
PointService
PaymentService
```

예시:

```text
PointService
- 포인트 잔액 조회
- 포인트 충전
- 포인트 이력 조회

CartService
- 장바구니 조회
- 메뉴 추가
- 수량 변경
- 항목 삭제
```

하나의 Service에 모든 기능을 넣지 않는다.

```text
비권장

CoffeeService
- 회원가입
- 메뉴 조회
- 장바구니 처리
- 포인트 차감
- 주문 생성
- Kafka 발행
```

---

# 10. Facade 규칙

Facade는 여러 도메인을 조합하는 Use Case에 사용한다.

주문 처리 구조는 다음과 같다.

```text
OrderController
→ OrderFacade
→ OrderTransactionService
→ OrderEventDeliveryService
→ ExternalOrderEventClient
```

각 책임은 다음과 같다.

| 구성요소 | 책임 |
|---|---|
| `OrderController` | 요청과 응답 처리 |
| `OrderFacade` | 주문 처리와 외부 전송 순서 제어 |
| `OrderTransactionService` | 주문 관련 DB 작업 처리 |
| `OrderEventDeliveryService` | 주문 완료 이벤트 생성, 외부 전송 및 전송 로그 기록 |
| `ExternalOrderEventClient` | 외부 데이터 수집 API 호출 |

Facade에 직접 모든 비즈니스 로직을 작성하지 않는다.

---

# 11. Repository 규칙

## 11.1 기본 CRUD

단순 CRUD는 `JpaRepository`를 사용한다.

```java
public interface MenuRepository
        extends JpaRepository<Menu, Long> {
}
```

---

## 11.2 복잡한 조회

동적 조건, 조인, 집계가 필요한 조회는 QueryDSL Repository로 분리한다.

```text
MenuQueryRepository
MenuQueryRepositoryImpl

OrderQueryRepository
OrderQueryRepositoryImpl
```

---

## 11.3 잠금 조회

락이 필요한 메서드는 이름에 목적을 표시한다.

```java
findByMemberIdForUpdate();
findAllByIdWithReadLock();
findCartForUpdate();
```

일반 조회와 잠금 조회를 구분한다.

```java
findByMemberId();
findByMemberIdForUpdate();
```

---

## 11.4 Repository 예외

Repository에서 비즈니스 예외 메시지를 직접 구성하지 않는다.

Service에서 조회 결과를 확인하고 `ErrorCode`를 사용한다.

```java
Menu menu = menuRepository.findById(menuId)
        .orElseThrow(() -> new BusinessException(
                ErrorCode.MENU_NOT_FOUND
        ));
```

---

# 12. 트랜잭션 규칙

## 12.1 적용 위치

`@Transactional`은 Controller가 아닌 Service 또는 TransactionService에 적용한다.

```text
Controller
→ Service
→ Repository
```

---

## 12.2 조회 전용

조회만 수행하는 Service 메서드는 다음을 사용한다.

```java
@Transactional(readOnly = true)
```

---

## 12.3 변경 작업

데이터 생성, 수정, 삭제에는 일반 트랜잭션을 사용한다.

```java
@Transactional
```

---

## 12.4 주문 트랜잭션

다음 작업은 하나의 트랜잭션으로 처리한다.

```text
장바구니 조회 및 검증
메뉴 상태와 가격 검증
포인트 조회 및 차감
주문 저장
주문 항목 저장
결제 저장
포인트 이력 저장
장바구니 항목 삭제
Outbox Event 저장
```

---

## 12.5 외부 호출 금지

다음 작업은 DB 트랜잭션 안에 넣지 않는다.

```text
외부 Mock API 호출
Kafka Consumer의 외부 API 호출
느린 파일 처리
장시간 대기 작업
```

외부 네트워크 호출은 Commit 이후 실행한다.

---

## 12.6 Self Invocation 금지

같은 클래스 내부에서 `@Transactional` 메서드를 직접 호출해 트랜잭션을 분리하지 않는다.

```java
비권장

public void order() {
    createOrderTransaction();
}

@Transactional
public void createOrderTransaction() {
}
```

필요한 경우 클래스를 분리한다.

```text
OrderFacade
→ OrderTransactionService
```

---

## 12.7 예외 처리

트랜잭션 안에서 예외를 잡고 무시하지 않는다.

```java
비권장

try {
    orderRepository.save(order);
} catch (Exception exception) {
    log.error("주문 저장 실패", exception);
}
```

Rollback이 필요하면 예외를 다시 던진다.

---

# 13. 예외 처리 규칙

## 13.1 BusinessException

비즈니스 예외는 공통 `BusinessException`으로 처리한다.

```java
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
```

---

## 13.2 ErrorCode

에러 코드는 Enum으로 관리한다.

```java
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    MENU_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "MENU_NOT_FOUND",
            "메뉴를 찾을 수 없습니다."
    ),

    POINT_NOT_ENOUGH(
            HttpStatus.BAD_REQUEST,
            "POINT_NOT_ENOUGH",
            "보유 포인트가 부족합니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
```

---

## 13.3 GlobalExceptionHandler

예외 응답은 `GlobalExceptionHandler`에서 공통 처리한다.

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException exception
    ) {
        ErrorCode errorCode = exception.getErrorCode();

        return ResponseEntity.status(errorCode.getStatus())
                .body(
                        ApiResponse.error(
                                errorCode.getCode(),
                                errorCode.getMessage()
                        )
                );
    }
}
```

예상하지 못한 예외의 Stack Trace는 응답에 포함하지 않는다.

---

## 13.4 예외 메시지

클라이언트가 이해할 수 있는 메시지를 사용한다.

```text
권장

메뉴를 찾을 수 없습니다.
보유 포인트가 부족합니다.
판매 중인 메뉴가 아닙니다.
```

내부 구현 정보는 노출하지 않는다.

```text
비권장

MenuRepository.findById 결과가 null입니다.
SQLIntegrityConstraintViolationException 발생
```

---

# 14. Lombok 사용 규칙

## 허용

```text
@Getter
@RequiredArgsConstructor
@NoArgsConstructor
@Builder
```

## 제한

Entity에 `@Data`를 사용하지 않는다.

```java
비권장

@Entity
@Data
public class Member {
}
```

`@Data`는 Setter, equals, hashCode, toString을 함께 생성하므로  
Entity 연관관계에서 예상하지 못한 문제를 만들 수 있다.

Entity의 `@Builder`는 필요한 경우에만 제한적으로 사용한다.

---

# 15. Optional 규칙

Repository의 단건 조회 결과에는 `Optional`을 사용할 수 있다.

```java
Optional<Menu> findById(Long menuId);
```

Service 메서드 파라미터와 Entity 필드에는 사용하지 않는다.

```java
비권장

public void createOrder(Optional<Long> memberId) {
}

private Optional<String> description;
```

---

# 16. 컬렉션 규칙

목록 결과가 없으면 빈 컬렉션을 반환한다.

```json
{
  "data": []
}
```

`null` 컬렉션을 반환하지 않는다.

```text
권장
items: []

비권장
items: null
```

---

# 17. 주석 규칙

코드만으로 의도가 드러나면 불필요한 주석을 작성하지 않는다.

```java
비권장

// 포인트를 차감한다.
point.use(totalAmount);
```

주석은 무엇을 하는지보다 왜 그렇게 했는지를 설명한다.

```java
// 동일 회원의 주문과 충전이 동시에 실행될 수 있으므로
// 잔액 변경 전 포인트 행에 쓰기 잠금을 획득한다.
```

주석이 필요한 경우:

- 동시성 제어 이유
- 일반적이지 않은 구현 방식
- 외부 시스템 제약
- 중요한 비즈니스 결정
- TODO와 후속 개선 사항

---

# 18. 로그 규칙

로그에 포함할 수 있는 정보:

```text
traceId
memberId
orderId
orderNumber
eventId
idempotencyKey
처리 결과
처리 시간
```

로그에 남기지 않는 정보:

```text
비밀번호
Access Token 원문
Refresh Token 원문
개인정보 전체
외부 결제 인증정보
```

로그 레벨은 다음 기준을 사용한다.

| Level | 사용 기준 |
|---|---|
| `DEBUG` | 개발 중 상세 흐름 |
| `INFO` | 정상적인 주요 비즈니스 처리 |
| `WARN` | 재시도 가능한 실패 또는 비정상 요청 |
| `ERROR` | 시스템 장애 및 복구가 필요한 실패 |

---

# 19. 테스트 코드 규칙

## 19.1 클래스 이름

```text
{Target}Test
{Target}IntegrationTest
{Target}ConcurrencyTest
```

예시:

```text
PointTest
OrderServiceTest
OrderIntegrationTest
PointConcurrencyTest
```

---

## 19.2 테스트 메서드 이름

테스트 목적이 드러나는 한글 이름을 사용한다.

```java
@Test
void 포인트가_부족하면_주문에_실패한다() {
}
```

프로젝트 내부에서는 한글 테스트명으로 통일한다.

---

## 19.3 Given-When-Then

```java
@Test
void 포인트가_부족하면_주문에_실패한다() {
    // given

    // when

    // then
}
```

---

## 19.4 우선 테스트 대상

- 정상 처리
- 입력값 검증 실패
- 인증 및 권한 오류
- 리소스 없음
- 메뉴 판매 불가
- 포인트 부족
- 중복 주문
- 트랜잭션 Rollback
- 동시성
- 외부 시스템 장애

---

# 20. 코드 포맷

- 들여쓰기는 공백 4칸을 사용한다.
- 한 줄에는 하나의 명령문을 작성한다.
- Wildcard Import를 사용하지 않는다.
- 사용하지 않는 Import는 제거한다.
- 파일 마지막에는 개행을 포함한다.
- 긴 메서드 호출은 파라미터 단위로 줄바꿈한다.

```java
OrderCreateResponse response =
        orderFacade.createOrder(
                memberId,
                idempotencyKey
        );
```

Controller 응답은 읽기 쉽게 줄바꿈한다.

```java
return ResponseEntity.status(HttpStatus.CREATED)
        .body(
                ApiResponse.success(
                        "주문과 결제가 완료되었습니다.",
                        response
                )
        );
```

---

# 21. Git 브랜치 규칙

```text
main
develop
feature/*
fix/*
refactor/*
docs/*
test/*
```

예시:

```text
feature/member-signup
feature/point-charge
feature/order-create
fix/order-idempotency
refactor/point-lock
docs/api-spec
test/order-concurrency
```

---

# 22. 커밋 메시지 규칙

커밋 메시지는 다음 형식을 사용한다.

```text
type: 작업 내용
```

| Type | 설명 |
|---|---|
| `feat` | 새로운 기능 |
| `fix` | 버그 수정 |
| `refactor` | 기능 변경 없는 구조 개선 |
| `test` | 테스트 추가 및 수정 |
| `docs` | 문서 변경 |
| `chore` | 설정 및 빌드 작업 |
| `perf` | 성능 개선 |
| `style` | 코드 포맷 변경 |

예시:

```text
feat: 포인트 충전 기능 구현
feat: 장바구니 주문 API 구현
fix: 동시 주문 시 포인트 초과 차감 방지
refactor: 주문 트랜잭션 서비스 분리
test: 동일 회원 동시 주문 테스트 추가
docs: 아키텍처 문서 작성
```

---

# 23. AI 생성 코드 검토 기준

AI가 생성한 코드는 바로 반영하지 않고 다음 항목을 확인한다.

## 구조

- Controller 반환형이 `ResponseEntity<ApiResponse<T>>`인가?
- Controller에서 Repository를 직접 호출하지 않는가?
- Entity를 API 응답으로 직접 반환하지 않는가?
- Request DTO와 Response DTO가 분리되었는가?
- 코드가 올바른 도메인 패키지에 위치했는가?
- 하나의 클래스에 책임이 몰려 있지 않은가?

## 트랜잭션

- `@Transactional`이 Service 또는 TransactionService에 적용되었는가?
- 외부 API 호출이 트랜잭션 안에 들어가지 않았는가?
- Self Invocation 문제가 없는가?
- 예외를 잡고 무시해 잘못 Commit하지 않는가?

## 정합성

- 포인트 잔액이 음수가 될 수 없는가?
- 주문 금액을 클라이언트가 전달한 값으로 결정하지 않는가?
- 주문 시 메뉴 상태와 가격을 다시 검증하는가?
- 포인트 차감과 주문 저장이 같은 트랜잭션인가?
- 중복 주문을 DB Unique 제약조건으로 최종 방어하는가?

## 보안

- 비밀번호를 평문으로 저장하지 않는가?
- Access Token과 Refresh Token 원문을 로그에 남기지 않는가?
- Request의 `memberId`를 인증 회원으로 신뢰하지 않는가?
- 관리자 API에 권한 검증이 적용되었는가?

## 테스트

- 정상 케이스만 작성되어 있지 않은가?
- 실패와 경계값 테스트가 있는가?
- 동시성 테스트가 실제 병렬 요청으로 수행되는가?
- 최종 DB 상태를 검증하는가?

---

# 24. 최종 요약

- 모든 Controller는 `ResponseEntity<ApiResponse<T>>`를 반환한다.
- 단건, 목록, 페이지, 데이터 없음 응답 형식을 통일한다.
- Controller는 요청과 응답만 담당한다.
- Controller에서 Repository를 직접 호출하지 않는다.
- Entity를 API 요청과 응답에 직접 사용하지 않는다.
- Request DTO와 Response DTO를 분리한다.
- Entity 상태는 의미 있는 도메인 메서드로 변경한다.
- 여러 도메인을 조합하는 주문은 Facade를 사용한다.
- DB 정합성 작업은 TransactionService에서 처리한다.
- 외부 네트워크 호출은 DB 트랜잭션 밖에서 수행한다.
- 비즈니스 예외는 `BusinessException`과 `ErrorCode`로 통일한다.
- AI 생성 코드는 구조, 트랜잭션, 정합성, 보안 및 테스트 기준으로 검토한다.
