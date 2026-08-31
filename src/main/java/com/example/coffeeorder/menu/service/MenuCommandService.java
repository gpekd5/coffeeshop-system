package com.example.coffeeorder.menu.service;

import java.time.Clock;
import java.time.LocalDateTime;

import com.example.coffeeorder.common.exception.BusinessException;
import com.example.coffeeorder.common.exception.ErrorCode;
import com.example.coffeeorder.menu.dto.request.MenuCreateRequest;
import com.example.coffeeorder.menu.dto.request.MenuStatusUpdateRequest;
import com.example.coffeeorder.menu.dto.request.MenuUpdateRequest;
import com.example.coffeeorder.menu.dto.response.MenuResponse;
import com.example.coffeeorder.menu.dto.response.MenuStatusResponse;
import com.example.coffeeorder.menu.entity.Menu;
import com.example.coffeeorder.menu.repository.MenuRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MenuCommandService {

    private final MenuRepository menuRepository;
    private final Clock clock;

    public MenuCommandService(
            MenuRepository menuRepository,
            Clock clock
    ) {
        this.menuRepository = menuRepository;
        this.clock = clock;
    }

    @Transactional
    public MenuResponse createMenu(MenuCreateRequest request) {
        Menu menu = Menu.create(
                request.name(),
                request.description(),
                MenuRequestParser.parseRequiredCategory(request.category()),
                request.price(),
                MenuRequestParser.parseStatusOrDefault(request.status())
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
                MenuRequestParser.parseOptionalCategory(request.category()),
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

        menu.changeStatus(MenuRequestParser.parseRequiredStatus(request.status()));
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
}
