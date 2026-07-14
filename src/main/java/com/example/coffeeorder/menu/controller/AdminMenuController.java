package com.example.coffeeorder.menu.controller;

import com.example.coffeeorder.common.response.ApiResponse;
import com.example.coffeeorder.menu.dto.request.MenuCreateRequest;
import com.example.coffeeorder.menu.dto.request.MenuStatusUpdateRequest;
import com.example.coffeeorder.menu.dto.request.MenuUpdateRequest;
import com.example.coffeeorder.menu.dto.response.MenuResponse;
import com.example.coffeeorder.menu.dto.response.MenuStatusResponse;
import com.example.coffeeorder.menu.service.MenuService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/menus")
public class AdminMenuController {

    private final MenuService menuService;

    public AdminMenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MenuResponse>> createMenu(
            @Valid @RequestBody MenuCreateRequest request
    ) {
        MenuResponse response = menuService.createMenu(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        ApiResponse.success(
                                "메뉴가 등록되었습니다.",
                                response
                        )
                );
    }

    @PatchMapping("/{menuId}")
    public ResponseEntity<ApiResponse<MenuResponse>> updateMenu(
            @PathVariable Long menuId,
            @Valid @RequestBody MenuUpdateRequest request
    ) {
        MenuResponse response = menuService.updateMenu(
                menuId,
                request
        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "메뉴가 수정되었습니다.",
                        response
                )
        );
    }

    @PatchMapping("/{menuId}/status")
    public ResponseEntity<ApiResponse<MenuStatusResponse>> updateMenuStatus(
            @PathVariable Long menuId,
            @Valid @RequestBody MenuStatusUpdateRequest request
    ) {
        MenuStatusResponse response = menuService.updateMenuStatus(
                menuId,
                request
        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "메뉴 상태가 변경되었습니다.",
                        response
                )
        );
    }

    @DeleteMapping("/{menuId}")
    public ResponseEntity<ApiResponse<Void>> deleteMenu(
            @PathVariable Long menuId
    ) {
        menuService.deleteMenu(menuId);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "메뉴가 삭제되었습니다.",
                        null
                )
        );
    }
}
