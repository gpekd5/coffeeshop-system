package com.example.coffeeorder.cart.repository;

import java.util.List;
import java.util.Optional;

import com.example.coffeeorder.cart.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    Optional<CartItem> findByCart_IdAndMenu_Id(
            Long cartId,
            Long menuId
    );

    List<CartItem> findAllByCart_IdOrderByIdAsc(Long cartId);

    void deleteAllByCart_Id(Long cartId);
}
