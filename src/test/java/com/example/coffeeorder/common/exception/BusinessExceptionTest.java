package com.example.coffeeorder.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BusinessExceptionTest {

    @Test
    void ErrorCode의_메시지를_예외_메시지로_사용한다() {
        // given
        ErrorCode errorCode = ErrorCode.MENU_NOT_FOUND;

        // when
        BusinessException exception = new BusinessException(errorCode);

        // then
        assertThat(exception.getErrorCode()).isEqualTo(errorCode);
        assertThat(exception.getMessage()).isEqualTo("메뉴를 찾을 수 없습니다.");
    }
}
