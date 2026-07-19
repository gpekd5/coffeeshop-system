package com.example.coffeeorder.testsupport;

import com.example.coffeeorder.cart.entity.Cart;
import com.example.coffeeorder.cart.entity.CartItem;
import com.example.coffeeorder.member.entity.Member;
import com.example.coffeeorder.member.entity.MemberRole;
import com.example.coffeeorder.member.entity.MemberStatus;
import com.example.coffeeorder.menu.entity.Menu;
import com.example.coffeeorder.menu.entity.MenuCategory;
import com.example.coffeeorder.menu.entity.MenuStatus;
import com.example.coffeeorder.point.entity.Point;
import org.springframework.test.util.ReflectionTestUtils;

public final class IntegrationTestFixtures {

    public static final String DEFAULT_ENCRYPTED_PASSWORD = "encrypted-password";

    private IntegrationTestFixtures() {
    }

    public static Member member(String email) {
        return member(
                email,
                email
        );
    }

    public static Member member(
            String email,
            String name
    ) {
        return Member.create(
                email,
                DEFAULT_ENCRYPTED_PASSWORD,
                name
        );
    }

    public static Member memberWithRole(
            String email,
            MemberRole role
    ) {
        return memberWithRole(
                email,
                role.name(),
                role
        );
    }

    public static Member memberWithRole(
            String email,
            String name,
            MemberRole role
    ) {
        Member member = member(
                email,
                name
        );
        ReflectionTestUtils.setField(
                member,
                "role",
                role
        );

        return member;
    }

    public static Member memberWithStatus(
            String email,
            MemberStatus status
    ) {
        Member member = member(email);
        ReflectionTestUtils.setField(
                member,
                "status",
                status
        );

        return member;
    }

    public static Point point(Member member) {
        return Point.create(member);
    }

    public static Point point(
            Member member,
            long balance
    ) {
        Point point = point(member);

        if (balance > 0) {
            point.charge(balance);
        }

        return point;
    }

    public static Menu menu(
            String name,
            MenuCategory category,
            long price,
            MenuStatus status
    ) {
        return menu(
                name,
                name + " 설명",
                category,
                price,
                status
        );
    }

    public static Menu menu(
            String name,
            String description,
            MenuCategory category,
            long price,
            MenuStatus status
    ) {
        return Menu.create(
                name,
                description,
                category,
                price,
                status
        );
    }

    public static Cart cart(Member member) {
        return Cart.create(member);
    }

    public static CartItem cartItem(
            Cart cart,
            Menu menu,
            int quantity
    ) {
        return CartItem.create(
                cart,
                menu,
                quantity
        );
    }
}
