package com.example.coffeeorder.menu.service;

import java.util.Map;

import com.example.coffeeorder.common.exception.BusinessException;
import com.example.coffeeorder.common.exception.ErrorCode;
import com.example.coffeeorder.common.response.PageResponse;
import com.example.coffeeorder.common.util.EnumRequestParser;
import com.example.coffeeorder.common.util.PageRequestFactory;
import com.example.coffeeorder.menu.dto.response.MenuResponse;
import com.example.coffeeorder.menu.entity.Menu;
import com.example.coffeeorder.menu.entity.MenuCategory;
import com.example.coffeeorder.menu.entity.MenuStatus;
import com.example.coffeeorder.menu.repository.MenuRepository;
import com.example.coffeeorder.menu.repository.MenuSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MenuQueryService {

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

    public MenuQueryService(MenuRepository menuRepository) {
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
        MenuCategory menuCategory = EnumRequestParser.parseOptional(
                category,
                MenuCategory.class,
                ErrorCode.INVALID_MENU_CATEGORY
        );
        MenuStatus menuStatus = EnumRequestParser.parseOptionalOrDefault(
                status,
                MenuStatus.class,
                MenuStatus.ON_SALE,
                ErrorCode.INVALID_MENU_STATUS
        );
        PageRequest pageRequest = PageRequestFactory.create(
                page,
                size,
                sort,
                DEFAULT_SORT,
                SORT_PROPERTIES
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
}
