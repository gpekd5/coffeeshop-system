package com.example.coffeeorder.cart.controller;

import com.example.coffeeorder.cart.dto.request.AddCartItemRequest;
import com.example.coffeeorder.cart.dto.request.UpdateCartItemQuantityRequest;
import com.example.coffeeorder.cart.dto.response.CartItemMutationResponse;
import com.example.coffeeorder.cart.dto.response.CartResponse;
import com.example.coffeeorder.cart.service.CartService;
import com.example.coffeeorder.common.response.ApiResponse;
import com.example.coffeeorder.common.security.AuthMember;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<CartResponse>> getCart(
            @AuthenticationPrincipal AuthMember authMember
    ) {
        CartResponse response = cartService.getCart(authMember.memberId());

        return ResponseEntity.ok(
                ApiResponse.success(
                        "장바구니 조회에 성공했습니다.",
                        response
                )
        );
    }

    @PostMapping("/items")
    public ResponseEntity<ApiResponse<CartItemMutationResponse>> addCartItem(
            @AuthenticationPrincipal AuthMember authMember,
            @Valid @RequestBody AddCartItemRequest request
    ) {
        CartItemMutationResponse response = cartService.addCartItem(
                authMember.memberId(),
                request
        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "메뉴가 장바구니에 추가되었습니다.",
                        response
                )
        );
    }

    @PatchMapping("/items/{cartItemId}")
    public ResponseEntity<ApiResponse<CartItemMutationResponse>> updateQuantity(
            @AuthenticationPrincipal AuthMember authMember,
            @PathVariable Long cartItemId,
            @Valid @RequestBody UpdateCartItemQuantityRequest request
    ) {
        CartItemMutationResponse response = cartService.updateQuantity(
                authMember.memberId(),
                cartItemId,
                request
        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "장바구니 수량이 변경되었습니다.",
                        response
                )
        );
    }

    @DeleteMapping("/items/{cartItemId}")
    public ResponseEntity<ApiResponse<Void>> deleteCartItem(
            @AuthenticationPrincipal AuthMember authMember,
            @PathVariable Long cartItemId
    ) {
        cartService.deleteCartItem(
                authMember.memberId(),
                cartItemId
        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "장바구니 항목이 삭제되었습니다.",
                        null
                )
        );
    }

    @DeleteMapping("/items")
    public ResponseEntity<ApiResponse<Void>> clearCart(
            @AuthenticationPrincipal AuthMember authMember
    ) {
        cartService.clearCart(authMember.memberId());

        return ResponseEntity.ok(
                ApiResponse.success(
                        "장바구니를 비웠습니다.",
                        null
                )
        );
    }
}
