package com.example.coffeeorder.cart.repository;

import java.util.Optional;

import com.example.coffeeorder.cart.entity.Cart;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartRepository extends JpaRepository<Cart, Long> {

    Optional<Cart> findByMember_Id(Long memberId);
}
