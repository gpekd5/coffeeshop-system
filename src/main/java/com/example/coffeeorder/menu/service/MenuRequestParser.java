package com.example.coffeeorder.menu.service;

import com.example.coffeeorder.common.exception.BusinessException;
import com.example.coffeeorder.common.exception.ErrorCode;
import com.example.coffeeorder.menu.entity.MenuCategory;
import com.example.coffeeorder.menu.entity.MenuStatus;

final class MenuRequestParser {

    private MenuRequestParser() {
    }

    static MenuCategory parseOptionalCategory(String category) {
        if (category == null) {
            return null;
        }

        return parseRequiredCategory(category);
    }

    static MenuCategory parseRequiredCategory(String category) {
        if (category.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_MENU_CATEGORY);
        }

        try {
            return MenuCategory.valueOf(category.trim());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_MENU_CATEGORY);
        }
    }

    static MenuStatus parseStatusOrDefault(String status) {
        if (status == null) {
            return MenuStatus.ON_SALE;
        }

        return parseRequiredStatus(status);
    }

    static MenuStatus parseRequiredStatus(String status) {
        if (status.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_MENU_STATUS);
        }

        try {
            return MenuStatus.valueOf(status.trim());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_MENU_STATUS);
        }
    }
}
