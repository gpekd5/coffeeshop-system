package com.example.coffeeorder.cart.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.coffeeorder.common.exception.BusinessException;
import com.example.coffeeorder.common.exception.ErrorCode;
import com.example.coffeeorder.member.entity.Member;
import com.example.coffeeorder.menu.entity.Menu;
import com.example.coffeeorder.menu.entity.MenuCategory;
import org.junit.jupiter.api.Test;

class CartItemTest {

    @Test
    void 장바구니_항목의_금액은_현재_메뉴_가격과_수량으로_계산한다() {
        Cart cart = Cart.create(Member.create(
                "test@example.com",
                "encrypted-password",
                "홍길동"
        ));
        Menu menu = Menu.create(
                "아메리카노",
                "진한 에스프레소와 물로 만든 커피",
                MenuCategory.COFFEE,
                4500L
        );
        CartItem cartItem = CartItem.create(
                cart,
                menu,
                2
        );

        assertThat(cartItem.calculateLineAmount()).isEqualTo(9000L);
    }

    @Test
    void 동일_메뉴를_추가하면_수량이_증가한다() {
        Cart cart = Cart.create(Member.create(
                "test@example.com",
                "encrypted-password",
                "홍길동"
        ));
        Menu menu = Menu.create(
                "아메리카노",
                "진한 에스프레소와 물로 만든 커피",
                MenuCategory.COFFEE,
                4500L
        );
        CartItem cartItem = CartItem.create(
                cart,
                menu,
                2
        );

        cartItem.increaseQuantity(3);

        assertThat(cartItem.getQuantity()).isEqualTo(5);
        assertThat(cartItem.calculateLineAmount()).isEqualTo(22500L);
    }

    @Test
    void 수량은_1_이상이어야_한다() {
        Cart cart = Cart.create(Member.create(
                "test@example.com",
                "encrypted-password",
                "홍길동"
        ));
        Menu menu = Menu.create(
                "아메리카노",
                "진한 에스프레소와 물로 만든 커피",
                MenuCategory.COFFEE,
                4500L
        );

        assertThatThrownBy(() -> CartItem.create(
                cart,
                menu,
                0
        ))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_QUANTITY)
                );
    }

    @Test
    void 수량을_0으로_변경할_수_없다() {
        Cart cart = Cart.create(Member.create(
                "test@example.com",
                "encrypted-password",
                "홍길동"
        ));
        Menu menu = Menu.create(
                "아메리카노",
                "진한 에스프레소와 물로 만든 커피",
                MenuCategory.COFFEE,
                4500L
        );
        CartItem cartItem = CartItem.create(
                cart,
                menu,
                1
        );

        assertThatThrownBy(() -> cartItem.changeQuantity(0))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_QUANTITY)
                );
    }
}
