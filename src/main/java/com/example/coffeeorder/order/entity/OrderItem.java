package com.example.coffeeorder.order.entity;

import java.time.LocalDateTime;

import com.example.coffeeorder.common.exception.BusinessException;
import com.example.coffeeorder.common.exception.ErrorCode;
import com.example.coffeeorder.menu.entity.Menu;
import com.example.coffeeorder.menu.entity.MenuCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "order_items")
@EntityListeners(AuditingEntityListener.class)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "order_id",
            nullable = false
    )
    private Order order;

    @Column(
            name = "menu_id",
            nullable = false
    )
    private Long menuId;

    @Column(
            name = "menu_name",
            nullable = false,
            length = 100
    )
    private String menuName;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "menu_category",
            nullable = false,
            length = 30
    )
    private MenuCategory menuCategory;

    @Column(
            name = "unit_price",
            nullable = false
    )
    private long unitPrice;

    @Column(nullable = false)
    private int quantity;

    @Column(
            name = "line_amount",
            nullable = false
    )
    private long lineAmount;

    @CreatedDate
    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

    protected OrderItem() {
    }

    private OrderItem(
            Order order,
            Long menuId,
            String menuName,
            MenuCategory menuCategory,
            long unitPrice,
            int quantity,
            long lineAmount
    ) {
        validateQuantity(quantity);

        this.order = order;
        this.menuId = menuId;
        this.menuName = menuName;
        this.menuCategory = menuCategory;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.lineAmount = lineAmount;
    }

    public static OrderItem create(
            Order order,
            Menu menu,
            int quantity
    ) {
        validateQuantity(quantity);

        return new OrderItem(
                order,
                menu.getId(),
                menu.getName(),
                menu.getCategory(),
                menu.getPrice(),
                quantity,
                calculateLineAmount(
                        menu.getPrice(),
                        quantity
                )
        );
    }

    private static void validateQuantity(int quantity) {
        if (quantity < 1) {
            throw new BusinessException(ErrorCode.INVALID_CART_ITEM_QUANTITY);
        }
    }

    private static long calculateLineAmount(
            long unitPrice,
            int quantity
    ) {
        try {
            return Math.multiplyExact(
                    unitPrice,
                    quantity
            );
        } catch (ArithmeticException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    public Long getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public Long getMenuId() {
        return menuId;
    }

    public String getMenuName() {
        return menuName;
    }

    public MenuCategory getMenuCategory() {
        return menuCategory;
    }

    public long getUnitPrice() {
        return unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public long getLineAmount() {
        return lineAmount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
