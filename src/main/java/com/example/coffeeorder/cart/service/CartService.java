package com.example.coffeeorder.cart.service;

import java.util.List;

import com.example.coffeeorder.cart.dto.request.AddCartItemRequest;
import com.example.coffeeorder.cart.dto.request.UpdateCartItemQuantityRequest;
import com.example.coffeeorder.cart.dto.response.CartItemMutationResponse;
import com.example.coffeeorder.cart.dto.response.CartResponse;
import com.example.coffeeorder.cart.entity.Cart;
import com.example.coffeeorder.cart.entity.CartItem;
import com.example.coffeeorder.cart.repository.CartItemRepository;
import com.example.coffeeorder.cart.repository.CartRepository;
import com.example.coffeeorder.common.exception.BusinessException;
import com.example.coffeeorder.common.exception.ErrorCode;
import com.example.coffeeorder.member.entity.Member;
import com.example.coffeeorder.member.repository.MemberRepository;
import com.example.coffeeorder.menu.entity.Menu;
import com.example.coffeeorder.menu.repository.MenuRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final MemberRepository memberRepository;
    private final MenuRepository menuRepository;

    public CartService(
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            MemberRepository memberRepository,
            MenuRepository menuRepository
    ) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.memberRepository = memberRepository;
        this.menuRepository = menuRepository;
    }

    @Transactional
    public CartResponse getCart(Long memberId) {
        Cart cart = getOrCreateCart(memberId);

        return CartResponse.of(
                cart,
                findCartItems(cart)
        );
    }

    @Transactional
    public CartItemMutationResponse addCartItem(
            Long memberId,
            AddCartItemRequest request
    ) {
        validateQuantity(request.quantity());

        Cart cart = getOrCreateCart(memberId);
        Menu menu = findOrderableMenu(request.menuId());
        CartItem cartItem = cartItemRepository.findByCart_IdAndMenu_Id(
                        cart.getId(),
                        menu.getId()
                )
                .map(existingCartItem -> {
                    existingCartItem.increaseQuantity(request.quantity());
                    return existingCartItem;
                })
                .orElseGet(() -> cartItemRepository.save(CartItem.create(
                        cart,
                        menu,
                        request.quantity()
                )));

        cartItemRepository.flush();

        return CartItemMutationResponse.from(cartItem);
    }

    @Transactional
    public CartItemMutationResponse updateQuantity(
            Long memberId,
            Long cartItemId,
            UpdateCartItemQuantityRequest request
    ) {
        validateQuantity(request.quantity());

        CartItem cartItem = findCartItem(cartItemId);
        validateOwner(
                cartItem,
                memberId
        );

        cartItem.changeQuantity(request.quantity());
        cartItemRepository.flush();

        return CartItemMutationResponse.from(cartItem);
    }

    @Transactional
    public void deleteCartItem(
            Long memberId,
            Long cartItemId
    ) {
        CartItem cartItem = findCartItem(cartItemId);
        validateOwner(
                cartItem,
                memberId
        );

        cartItemRepository.delete(cartItem);
    }

    @Transactional
    public void clearCart(Long memberId) {
        Cart cart = getOrCreateCart(memberId);

        cartItemRepository.deleteAllByCart_Id(cart.getId());
    }

    private Cart getOrCreateCart(Long memberId) {
        return cartRepository.findByMember_Id(memberId)
                .orElseGet(() -> cartRepository.saveAndFlush(Cart.create(
                        findMember(memberId)
                )));
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MEMBER_NOT_FOUND
                ));
    }

    private Menu findOrderableMenu(Long menuId) {
        Menu menu = menuRepository.findByIdAndDeletedAtIsNull(menuId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MENU_NOT_FOUND
                ));

        if (!menu.isOnSale()) {
            throw new BusinessException(ErrorCode.MENU_NOT_ON_SALE);
        }

        return menu;
    }

    private CartItem findCartItem(Long cartItemId) {
        return cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.CART_ITEM_NOT_FOUND
                ));
    }

    private List<CartItem> findCartItems(Cart cart) {
        return cartItemRepository.findAllByCart_IdOrderByIdAsc(cart.getId());
    }

    private void validateOwner(
            CartItem cartItem,
            Long memberId
    ) {
        if (!cartItem.isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.CART_ITEM_FORBIDDEN);
        }
    }

    private void validateQuantity(Integer quantity) {
        if (quantity == null || quantity < 1) {
            throw new BusinessException(ErrorCode.INVALID_QUANTITY);
        }
    }
}
