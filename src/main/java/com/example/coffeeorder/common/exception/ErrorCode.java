package com.example.coffeeorder.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    INVALID_INPUT(
            HttpStatus.BAD_REQUEST,
            "INVALID_INPUT",
            "입력값이 올바르지 않습니다."
    ),
    UNAUTHORIZED(
            HttpStatus.UNAUTHORIZED,
            "UNAUTHORIZED",
            "인증되지 않은 사용자입니다."
    ),
    INVALID_TOKEN(
            HttpStatus.UNAUTHORIZED,
            "INVALID_TOKEN",
            "유효하지 않은 Access Token입니다."
    ),
    EXPIRED_TOKEN(
            HttpStatus.UNAUTHORIZED,
            "EXPIRED_TOKEN",
            "만료된 Access Token입니다."
    ),
    BLACKLISTED_TOKEN(
            HttpStatus.UNAUTHORIZED,
            "BLACKLISTED_TOKEN",
            "로그아웃 처리된 Access Token입니다."
    ),
    INVALID_REFRESH_TOKEN(
            HttpStatus.UNAUTHORIZED,
            "INVALID_REFRESH_TOKEN",
            "유효하지 않은 Refresh Token입니다."
    ),
    EXPIRED_REFRESH_TOKEN(
            HttpStatus.UNAUTHORIZED,
            "EXPIRED_REFRESH_TOKEN",
            "만료된 Refresh Token입니다."
    ),
    REFRESH_TOKEN_NOT_FOUND(
            HttpStatus.UNAUTHORIZED,
            "REFRESH_TOKEN_NOT_FOUND",
            "Redis에 Refresh Token이 존재하지 않습니다."
    ),
    INVALID_LOGIN(
            HttpStatus.UNAUTHORIZED,
            "INVALID_LOGIN",
            "이메일 또는 비밀번호가 일치하지 않습니다."
    ),
    FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "FORBIDDEN",
            "접근 권한이 없습니다."
    ),
    MEMBER_INACTIVE(
            HttpStatus.FORBIDDEN,
            "MEMBER_INACTIVE",
            "비활성화된 회원입니다."
    ),
    MEMBER_WITHDRAWN(
            HttpStatus.FORBIDDEN,
            "MEMBER_WITHDRAWN",
            "탈퇴한 회원입니다."
    ),
    MEMBER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "MEMBER_NOT_FOUND",
            "회원을 찾을 수 없습니다."
    ),
    DUPLICATED_EMAIL(
            HttpStatus.CONFLICT,
            "DUPLICATED_EMAIL",
            "이미 사용 중인 이메일입니다."
    ),
    INVALID_MENU_CATEGORY(
            HttpStatus.BAD_REQUEST,
            "INVALID_MENU_CATEGORY",
            "유효하지 않은 메뉴 카테고리입니다."
    ),
    INVALID_MENU_PRICE(
            HttpStatus.BAD_REQUEST,
            "INVALID_MENU_PRICE",
            "메뉴 가격은 0보다 커야 합니다."
    ),
    INVALID_MENU_STATUS(
            HttpStatus.BAD_REQUEST,
            "INVALID_MENU_STATUS",
            "유효하지 않은 메뉴 상태입니다."
    ),
    MENU_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "MENU_NOT_FOUND",
            "메뉴를 찾을 수 없습니다."
    ),
    MENU_NOT_ON_SALE(
            HttpStatus.CONFLICT,
            "MENU_NOT_ON_SALE",
            "판매 중인 메뉴가 아닙니다."
    ),
    MENU_ALREADY_DELETED(
            HttpStatus.CONFLICT,
            "MENU_ALREADY_DELETED",
            "이미 삭제된 메뉴입니다."
    ),
    INVALID_QUANTITY(
            HttpStatus.BAD_REQUEST,
            "INVALID_QUANTITY",
            "수량은 1 이상이어야 합니다."
    ),
    CART_ITEM_QUANTITY_EXCEEDED(
            HttpStatus.BAD_REQUEST,
            "CART_ITEM_QUANTITY_EXCEEDED",
            "메뉴별 최대 수량을 초과했습니다."
    ),
    CART_EMPTY(
            HttpStatus.BAD_REQUEST,
            "CART_EMPTY",
            "장바구니가 비어 있습니다."
    ),
    CART_ITEM_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "CART_ITEM_FORBIDDEN",
            "본인의 장바구니 항목이 아닙니다."
    ),
    CART_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "CART_NOT_FOUND",
            "장바구니를 찾을 수 없습니다."
    ),
    CART_ITEM_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "CART_ITEM_NOT_FOUND",
            "장바구니 항목을 찾을 수 없습니다."
    ),
    INVALID_POINT_AMOUNT(
            HttpStatus.BAD_REQUEST,
            "INVALID_POINT_AMOUNT",
            "충전 금액은 0보다 커야 합니다."
    ),
    POINT_NOT_ENOUGH(
            HttpStatus.BAD_REQUEST,
            "POINT_NOT_ENOUGH",
            "보유 포인트가 부족합니다."
    ),
    POINT_CHARGE_LIMIT_EXCEEDED(
            HttpStatus.BAD_REQUEST,
            "POINT_CHARGE_LIMIT_EXCEEDED",
            "충전 한도를 초과했습니다."
    ),
    POINT_ACCOUNT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "POINT_ACCOUNT_NOT_FOUND",
            "포인트 계정을 찾을 수 없습니다."
    ),
    POINT_UPDATE_CONFLICT(
            HttpStatus.CONFLICT,
            "POINT_UPDATE_CONFLICT",
            "포인트 변경 중 동시성 충돌이 발생했습니다."
    ),
    IDEMPOTENCY_KEY_REQUIRED(
            HttpStatus.BAD_REQUEST,
            "IDEMPOTENCY_KEY_REQUIRED",
            "주문 멱등키가 필요합니다."
    ),
    INVALID_ORDER_STATUS(
            HttpStatus.BAD_REQUEST,
            "INVALID_ORDER_STATUS",
            "유효하지 않은 주문 상태입니다."
    ),
    INVALID_ORDER_CHANNEL(
            HttpStatus.BAD_REQUEST,
            "INVALID_ORDER_CHANNEL",
            "유효하지 않은 주문 채널입니다."
    ),
    ORDER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "ORDER_NOT_FOUND",
            "주문을 찾을 수 없습니다."
    ),
    ORDER_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "ORDER_FORBIDDEN",
            "본인의 주문이 아닙니다."
    ),
    ORDER_ALREADY_PROCESSED(
            HttpStatus.CONFLICT,
            "ORDER_ALREADY_PROCESSED",
            "이미 처리된 주문입니다."
    ),
    ORDER_PROCESSING_CONFLICT(
            HttpStatus.CONFLICT,
            "ORDER_PROCESSING_CONFLICT",
            "주문 처리 중 동시성 충돌이 발생했습니다."
    ),
    PAYMENT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "PAYMENT_NOT_FOUND",
            "결제를 찾을 수 없습니다."
    ),
    PAYMENT_ALREADY_COMPLETED(
            HttpStatus.CONFLICT,
            "PAYMENT_ALREADY_COMPLETED",
            "이미 완료된 결제입니다."
    ),
    DUPLICATED_PAYMENT(
            HttpStatus.CONFLICT,
            "DUPLICATED_PAYMENT",
            "중복 결제 요청입니다."
    ),
    EXTERNAL_DATA_COLLECTION_FAILED(
            HttpStatus.BAD_GATEWAY,
            "EXTERNAL_DATA_COLLECTION_FAILED",
            "외부 데이터 수집 API 호출에 실패했습니다."
    ),
    EXTERNAL_DATA_COLLECTION_TIMEOUT(
            HttpStatus.GATEWAY_TIMEOUT,
            "EXTERNAL_DATA_COLLECTION_TIMEOUT",
            "외부 데이터 수집 API 호출이 타임아웃되었습니다."
    ),
    OUTBOX_EVENT_SAVE_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "OUTBOX_EVENT_SAVE_FAILED",
            "Outbox Event 저장에 실패했습니다."
    ),
    KAFKA_PUBLISH_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "KAFKA_PUBLISH_FAILED",
            "Kafka 발행에 실패했습니다."
    ),
    DUPLICATED_EVENT(
            HttpStatus.CONFLICT,
            "DUPLICATED_EVENT",
            "중복 이벤트입니다."
    ),
    INTERNAL_SERVER_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "INTERNAL_SERVER_ERROR",
            "서버 내부 오류가 발생했습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(
            HttpStatus status,
            String code,
            String message
    ) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
