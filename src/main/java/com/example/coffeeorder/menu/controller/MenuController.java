package com.example.coffeeorder.menu.controller;

import java.util.List;

import com.example.coffeeorder.common.response.ApiResponse;
import com.example.coffeeorder.common.response.PageResponse;
import com.example.coffeeorder.menu.dto.response.MenuResponse;
import com.example.coffeeorder.menu.dto.response.PopularMenuResponse;
import com.example.coffeeorder.menu.service.MenuService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/menus")
public class MenuController {

    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping("/popular")
    public ResponseEntity<ApiResponse<List<PopularMenuResponse>>> getPopularMenus() {
        List<PopularMenuResponse> response = menuService.getPopularMenus();

        return ResponseEntity.ok(
                ApiResponse.success(
                        "인기 메뉴 조회에 성공했습니다.",
                        response
                )
        );
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<MenuResponse>>> getMenus(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size,
            @RequestParam(required = false) String sort
    ) {
        PageResponse<MenuResponse> response = menuService.getMenus(
                category,
                status,
                keyword,
                page,
                size,
                sort
        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "메뉴 목록 조회에 성공했습니다.",
                        response
                )
        );
    }

    @GetMapping("/{menuId}")
    public ResponseEntity<ApiResponse<MenuResponse>> getMenu(
            @PathVariable Long menuId
    ) {
        MenuResponse response = menuService.getMenu(menuId);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "메뉴 상세 조회에 성공했습니다.",
                        response
                )
        );
    }
}
