package com.example.coffeeorder.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.coffeeorder.common.exception.BusinessException;
import com.example.coffeeorder.common.exception.ErrorCode;
import com.example.coffeeorder.menu.entity.MenuStatus;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class EnumRequestParserTest {

    @Test
    void 필수_enum_요청값을_앞뒤_공백_제거_후_파싱한다() {
        // when
        MenuStatus status = EnumRequestParser.parseRequired(
                " ON_SALE ",
                MenuStatus.class,
                ErrorCode.INVALID_MENU_STATUS
        );

        // then
        assertThat(status).isEqualTo(MenuStatus.ON_SALE);
    }

    @Test
    void 선택_enum_요청값이_null이면_null을_반환한다() {
        // when
        MenuStatus status = EnumRequestParser.parseOptional(
                null,
                MenuStatus.class,
                ErrorCode.INVALID_MENU_STATUS
        );

        // then
        assertThat(status).isNull();
    }

    @Test
    void 선택_enum_요청값이_null이면_기본값을_반환할_수_있다() {
        // when
        MenuStatus status = EnumRequestParser.parseOptionalOrDefault(
                null,
                MenuStatus.class,
                MenuStatus.ON_SALE,
                ErrorCode.INVALID_MENU_STATUS
        );

        // then
        assertThat(status).isEqualTo(MenuStatus.ON_SALE);
    }

    @Test
    void 공백이나_유효하지_않은_enum_요청값이면_전달한_에러코드로_예외가_발생한다() {
        assertInvalidMenuStatus(() -> EnumRequestParser.parseRequired(
                " ",
                MenuStatus.class,
                ErrorCode.INVALID_MENU_STATUS
        ));
        assertInvalidMenuStatus(() -> EnumRequestParser.parseRequired(
                "INVALID",
                MenuStatus.class,
                ErrorCode.INVALID_MENU_STATUS
        ));
    }

    private void assertInvalidMenuStatus(ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_MENU_STATUS);
    }
}
