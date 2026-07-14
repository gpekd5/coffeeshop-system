package com.example.coffeeorder.cart.entity;

import com.example.coffeeorder.common.entity.BaseEntity;
import com.example.coffeeorder.common.exception.BusinessException;
import com.example.coffeeorder.common.exception.ErrorCode;
import com.example.coffeeorder.menu.entity.Menu;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "cart_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_cart_items_cart_menu",
                columnNames = {
                        "cart_id",
                        "menu_id"
                }
        )
)
public class CartItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "cart_id",
            nullable = false
    )
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "menu_id",
            nullable = false
    )
    private Menu menu;

    @Column(nullable = false)
    private Integer quantity;

    protected CartItem() {
    }

    private CartItem(
            Cart cart,
            Menu menu,
            int quantity
    ) {
        validateQuantity(quantity);

        this.cart = cart;
        this.menu = menu;
        this.quantity = quantity;
    }

    public static CartItem create(
            Cart cart,
            Menu menu,
            int quantity
    ) {
        return new CartItem(
                cart,
                menu,
                quantity
        );
    }

    public void increaseQuantity(int quantity) {
        validateQuantity(quantity);

        this.quantity += quantity;
    }

    public void changeQuantity(int quantity) {
        validateQuantity(quantity);

        this.quantity = quantity;
    }

    public boolean isOwnedBy(Long memberId) {
        return cart.getMemberId()
                .equals(memberId);
    }

    public long calculateLineAmount() {
        return menu.getPrice() * quantity;
    }

    public Long getId() {
        return id;
    }

    public Cart getCart() {
        return cart;
    }

    public Menu getMenu() {
        return menu;
    }

    public Integer getQuantity() {
        return quantity;
    }

    private static void validateQuantity(int quantity) {
        if (quantity < 1) {
            throw new BusinessException(ErrorCode.INVALID_QUANTITY);
        }
    }
}
