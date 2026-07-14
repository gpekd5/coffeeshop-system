package com.example.coffeeorder.menu.service;

import java.util.Map;

import com.example.coffeeorder.common.exception.BusinessException;
import com.example.coffeeorder.common.exception.ErrorCode;
import com.example.coffeeorder.common.response.PageResponse;
import com.example.coffeeorder.menu.dto.response.MenuResponse;
import com.example.coffeeorder.menu.entity.Menu;
import com.example.coffeeorder.menu.entity.MenuCategory;
import com.example.coffeeorder.menu.entity.MenuStatus;
import com.example.coffeeorder.menu.repository.MenuRepository;
import com.example.coffeeorder.menu.repository.MenuSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MenuService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final String DEFAULT_SORT = "createdAt,desc";
    private static final Map<String, String> SORT_PROPERTIES = Map.of(
            "menuId",
            "id",
            "name",
            "name",
            "category",
            "category",
            "price",
            "price",
            "status",
            "status",
            "createdAt",
            "createdAt",
            "updatedAt",
            "updatedAt"
    );

    private final MenuRepository menuRepository;

    public MenuService(MenuRepository menuRepository) {
        this.menuRepository = menuRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<MenuResponse> getMenus(
            String category,
            String status,
            String keyword,
            String page,
            String size,
            String sort
    ) {
        MenuCategory menuCategory = parseCategory(category);
        MenuStatus menuStatus = parseStatusOrDefault(status);
        PageRequest pageRequest = createPageRequest(
                page,
                size,
                sort
        );

        Page<MenuResponse> menus = menuRepository.findAll(
                        MenuSpecifications.publicSearch(
                                menuCategory,
                                menuStatus,
                                keyword
                        ),
                        pageRequest
                )
                .map(MenuResponse::from);

        return PageResponse.from(menus);
    }

    @Transactional(readOnly = true)
    public MenuResponse getMenu(Long menuId) {
        Menu menu = menuRepository.findByIdAndDeletedAtIsNull(menuId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MENU_NOT_FOUND
                ));

        return MenuResponse.from(menu);
    }

    private MenuCategory parseCategory(String category) {
        if (category == null) {
            return null;
        }

        if (category.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_MENU_CATEGORY);
        }

        try {
            return MenuCategory.valueOf(category.trim());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_MENU_CATEGORY);
        }
    }

    private MenuStatus parseStatusOrDefault(String status) {
        if (status == null) {
            return MenuStatus.ON_SALE;
        }

        if (status.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_MENU_STATUS);
        }

        try {
            return MenuStatus.valueOf(status.trim());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_MENU_STATUS);
        }
    }

    private PageRequest createPageRequest(
            String page,
            String size,
            String sort
    ) {
        return PageRequest.of(
                parsePage(page),
                parseSize(size),
                parseSort(sort)
        );
    }

    private int parsePage(String page) {
        int parsedPage = parseInteger(
                page,
                DEFAULT_PAGE
        );

        if (parsedPage < 0) {
            throw new BusinessException(ErrorCode.INVALID_PAGE_REQUEST);
        }

        return parsedPage;
    }

    private int parseSize(String size) {
        int parsedSize = parseInteger(
                size,
                DEFAULT_SIZE
        );

        if (parsedSize <= 0) {
            throw new BusinessException(ErrorCode.INVALID_PAGE_REQUEST);
        }

        return parsedSize;
    }

    private int parseInteger(
            String value,
            int defaultValue
    ) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_PAGE_REQUEST);
        }
    }

    private Sort parseSort(String sort) {
        String sortExpression =
                sort == null || sort.isBlank() ? DEFAULT_SORT : sort.trim();
        String[] parts = sortExpression.split(",");

        if (parts.length > 2 || parts[0].isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PAGE_REQUEST);
        }

        String property = SORT_PROPERTIES.get(parts[0].trim());

        if (property == null) {
            throw new BusinessException(ErrorCode.INVALID_PAGE_REQUEST);
        }

        Sort.Direction direction = parseDirection(parts);

        return Sort.by(
                direction,
                property
        );
    }

    private Sort.Direction parseDirection(String[] parts) {
        if (parts.length == 1 || parts[1].isBlank()) {
            return Sort.Direction.ASC;
        }

        try {
            return Sort.Direction.fromString(parts[1].trim());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_PAGE_REQUEST);
        }
    }
}
