package com.example.coffeeorder.menu.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import com.example.coffeeorder.common.exception.BusinessException;
import com.example.coffeeorder.common.exception.ErrorCode;
import com.example.coffeeorder.common.response.PageResponse;
import com.example.coffeeorder.common.util.PageRequestFactory;
import com.example.coffeeorder.menu.dto.request.MenuCreateRequest;
import com.example.coffeeorder.menu.dto.request.MenuStatusUpdateRequest;
import com.example.coffeeorder.menu.dto.request.MenuUpdateRequest;
import com.example.coffeeorder.menu.dto.response.MenuResponse;
import com.example.coffeeorder.menu.dto.response.MenuStatusResponse;
import com.example.coffeeorder.menu.dto.response.PopularMenuResponse;
import com.example.coffeeorder.menu.entity.Menu;
import com.example.coffeeorder.menu.entity.MenuCategory;
import com.example.coffeeorder.menu.entity.MenuStatus;
import com.example.coffeeorder.menu.repository.MenuRepository;
import com.example.coffeeorder.menu.repository.MenuSpecifications;
import com.example.coffeeorder.order.entity.OrderStatus;
import com.example.coffeeorder.order.repository.OrderItemRepository;
import com.example.coffeeorder.order.repository.projection.PopularMenuAggregation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MenuService {

    private static final int POPULAR_MENU_LIMIT = 3;
    private static final long POPULAR_MENU_DAYS = 7L;
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
    private final OrderItemRepository orderItemRepository;
    private final Clock clock;

    public MenuService(
            MenuRepository menuRepository,
            OrderItemRepository orderItemRepository,
            Clock clock
    ) {
        this.menuRepository = menuRepository;
        this.orderItemRepository = orderItemRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PopularMenuResponse> getPopularMenus() {
        LocalDateTime endDateTime = LocalDateTime.now(clock);
        LocalDateTime startDateTime = endDateTime.minusDays(POPULAR_MENU_DAYS);

        List<PopularMenuAggregation> results =
                orderItemRepository.findPopularMenus(
                        OrderStatus.COMPLETED,
                        startDateTime,
                        endDateTime,
                        PageRequest.of(
                                0,
                                POPULAR_MENU_LIMIT
                        )
                );

        return IntStream.range(
                        0,
                        results.size()
                )
                .mapToObj(index -> PopularMenuResponse.of(
                        results.get(index),
                        index + 1
                ))
                .toList();
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

    @Transactional
    public MenuResponse createMenu(MenuCreateRequest request) {
        Menu menu = Menu.create(
                request.name(),
                request.description(),
                parseRequiredCategory(request.category()),
                request.price(),
                parseStatusOrDefault(request.status())
        );

        Menu savedMenu = menuRepository.saveAndFlush(menu);

        return MenuResponse.from(savedMenu);
    }

    @Transactional
    public MenuResponse updateMenu(
            Long menuId,
            MenuUpdateRequest request
    ) {
        Menu menu = findMenu(menuId);

        menu.update(
                request.name(),
                request.description(),
                parseOptionalCategory(request.category()),
                request.price()
        );
        menuRepository.flush();

        return MenuResponse.from(menu);
    }

    @Transactional
    public MenuStatusResponse updateMenuStatus(
            Long menuId,
            MenuStatusUpdateRequest request
    ) {
        Menu menu = findMenu(menuId);

        menu.changeStatus(parseRequiredStatus(request.status()));
        menuRepository.flush();

        return MenuStatusResponse.from(menu);
    }

    @Transactional
    public void deleteMenu(Long menuId) {
        Menu menu = findMenu(menuId);

        menu.delete(LocalDateTime.now(clock));
    }

    private Menu findMenu(Long menuId) {
        return menuRepository.findById(menuId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MENU_NOT_FOUND
                ));
    }

    private MenuCategory parseCategory(String category) {
        if (category == null) {
            return null;
        }

        return parseRequiredCategory(category);
    }

    private MenuCategory parseOptionalCategory(String category) {
        if (category == null) {
            return null;
        }

        return parseRequiredCategory(category);
    }

    private MenuCategory parseRequiredCategory(String category) {
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

        return parseRequiredStatus(status);
    }

    private MenuStatus parseRequiredStatus(String status) {
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
