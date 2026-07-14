package com.example.coffeeorder.menu.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import com.example.coffeeorder.common.exception.BusinessException;
import com.example.coffeeorder.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

class MenuTest {

    @Test
    void 메뉴를_생성하면_기본_상태는_ON_SALE이다() {
        Menu menu = Menu.create(
                "아메리카노",
                "진한 에스프레소와 물로 만든 커피",
                MenuCategory.COFFEE,
                4500L
        );

        assertThat(menu.getStatus()).isEqualTo(MenuStatus.ON_SALE);
        assertThat(menu.isDeleted()).isFalse();
    }

    @Test
    void 메뉴_가격은_0보다_커야_한다() {
        assertThatThrownBy(() -> Menu.create(
                "아메리카노",
                "진한 에스프레소와 물로 만든 커피",
                MenuCategory.COFFEE,
                0L
        ))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_MENU_PRICE)
                );
    }

    @Test
    void 메뉴를_삭제하면_deletedAt이_기록된다() {
        Menu menu = Menu.create(
                "아메리카노",
                "진한 에스프레소와 물로 만든 커피",
                MenuCategory.COFFEE,
                4500L
        );
        LocalDateTime deletedAt = LocalDateTime.of(
                2026,
                7,
                14,
                15,
                0
        );

        menu.delete(deletedAt);

        assertThat(menu.isDeleted()).isTrue();
        assertThat(menu.getDeletedAt()).isEqualTo(deletedAt);
    }
}
