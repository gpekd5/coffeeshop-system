package com.example.coffeeorder.common.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.coffeeorder.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

class ApiResponseTest {

    @Test
    void 기본_성공_응답을_생성한다() {
        // given
        String data = "data";

        // when
        ApiResponse<String> response = ApiResponse.success(data);

        // then
        assertThat(response.success()).isTrue();
        assertThat(response.code()).isEqualTo("SUCCESS");
        assertThat(response.message()).isEqualTo("요청이 성공했습니다.");
        assertThat(response.data()).isEqualTo(data);
    }

    @Test
    void 에러_응답을_생성한다() {
        // given
        ErrorCode errorCode = ErrorCode.POINT_NOT_ENOUGH;

        // when
        ApiResponse<Void> response = ApiResponse.error(errorCode);

        // then
        assertThat(response.success()).isFalse();
        assertThat(response.code()).isEqualTo("POINT_NOT_ENOUGH");
        assertThat(response.message()).isEqualTo("보유 포인트가 부족합니다.");
        assertThat(response.data()).isNull();
    }
}
