package com.example.coffeeorder.cart.repository;

import java.util.List;
import java.util.Optional;

import com.example.coffeeorder.cart.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    Optional<CartItem> findByCart_IdAndMenu_Id(
            Long cartId,
            Long menuId
    );

    List<CartItem> findAllByCart_IdOrderByIdAsc(Long cartId);

    @Query("""
            select ci
            from CartItem ci
            join fetch ci.menu
            where ci.cart.id = :cartId
            order by ci.menu.id asc
            """)
    List<CartItem> findAllByCartIdWithMenuOrderByMenuIdAsc(
            @Param("cartId") Long cartId
    );

    void deleteAllByCart_Id(Long cartId);
}
