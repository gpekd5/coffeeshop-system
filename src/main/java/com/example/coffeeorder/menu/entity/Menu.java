package com.example.coffeeorder.menu.entity;

import java.time.LocalDateTime;

import com.example.coffeeorder.common.entity.BaseEntity;
import com.example.coffeeorder.common.exception.BusinessException;
import com.example.coffeeorder.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "menus")
public class Menu extends BaseEntity {

    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_DESCRIPTION_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            nullable = false,
            length = 100
    )
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 30
    )
    private MenuCategory category;

    @Column(nullable = false)
    private Long price;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 20
    )
    private MenuStatus status;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected Menu() {
    }

    private Menu(
            String name,
            String description,
            MenuCategory category,
            long price,
            MenuStatus status
    ) {
        validateName(name);
        validateDescription(description);
        validateCategory(category);
        validatePrice(price);
        validateStatus(status);

        this.name = name;
        this.description = description;
        this.category = category;
        this.price = price;
        this.status = status;
    }

    public static Menu create(
            String name,
            String description,
            MenuCategory category,
            long price
    ) {
        return create(
                name,
                description,
                category,
                price,
                MenuStatus.ON_SALE
        );
    }

    public static Menu create(
            String name,
            String description,
            MenuCategory category,
            long price,
            MenuStatus status
    ) {
        return new Menu(
                name,
                description,
                category,
                price,
                status
        );
    }

    public void delete(LocalDateTime deletedAt) {
        if (isDeleted()) {
            throw new BusinessException(ErrorCode.MENU_ALREADY_DELETED);
        }

        this.deletedAt = deletedAt;
    }

    public void update(
            String name,
            String description,
            MenuCategory category,
            Long price
    ) {
        validateNotDeleted();

        if (name != null) {
            validateName(name);
            this.name = name;
        }

        if (description != null) {
            validateDescription(description);
            this.description = description;
        }

        if (category != null) {
            validateCategory(category);
            this.category = category;
        }

        if (price != null) {
            validatePrice(price);
            this.price = price;
        }
    }

    public void changeStatus(MenuStatus status) {
        validateNotDeleted();
        validateStatus(status);

        this.status = status;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isOnSale() {
        return status == MenuStatus.ON_SALE && !isDeleted();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public MenuCategory getCategory() {
        return category;
    }

    public Long getPrice() {
        return price;
    }

    public MenuStatus getStatus() {
        return status;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank() || name.length() > MAX_NAME_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private static void validateDescription(String description) {
        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private static void validateCategory(MenuCategory category) {
        if (category == null) {
            throw new BusinessException(ErrorCode.INVALID_MENU_CATEGORY);
        }
    }

    private static void validatePrice(long price) {
        if (price <= 0) {
            throw new BusinessException(ErrorCode.INVALID_MENU_PRICE);
        }
    }

    private static void validateStatus(MenuStatus status) {
        if (status == null) {
            throw new BusinessException(ErrorCode.INVALID_MENU_STATUS);
        }
    }

    private void validateNotDeleted() {
        if (isDeleted()) {
            throw new BusinessException(ErrorCode.MENU_ALREADY_DELETED);
        }
    }
}
