package com.example.coffeeorder.point.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.coffeeorder.common.exception.BusinessException;
import com.example.coffeeorder.common.exception.ErrorCode;
import com.example.coffeeorder.member.entity.Member;
import org.junit.jupiter.api.Test;

class PointTest {

    @Test
    void 포인트를_충전하면_잔액이_증가한다() {
        Point point = Point.create(회원());

        point.charge(10_000L);

        assertThat(point.getBalance()).isEqualTo(10_000L);
    }

    @Test
    void 충전_금액이_0_이하이면_실패한다() {
        Point point = Point.create(회원());

        assertThatThrownBy(() -> point.charge(0L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_POINT_AMOUNT)
                );
    }

    @Test
    void 보유_포인트_안에서_사용하면_잔액이_감소한다() {
        Point point = Point.create(회원());
        point.charge(10_000L);

        point.use(3_000L);

        assertThat(point.getBalance()).isEqualTo(7_000L);
    }

    @Test
    void 보유_포인트보다_많이_사용하면_실패하고_잔액은_유지된다() {
        Point point = Point.create(회원());
        point.charge(10_000L);

        assertThatThrownBy(() -> point.use(11_000L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.POINT_NOT_ENOUGH)
                );
        assertThat(point.getBalance()).isEqualTo(10_000L);
    }

    private Member 회원() {
        return Member.create(
                "user@example.com",
                "encrypted-password",
                "테스트회원"
        );
    }
}
