package com.example.coffeeorder.cart.repository;

import java.util.Optional;

import com.example.coffeeorder.cart.entity.Cart;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartRepository extends JpaRepository<Cart, Long> {

    Optional<Cart> findByMember_Id(Long memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c
            from Cart c
            join fetch c.member
            where c.member.id = :memberId
            """)
    Optional<Cart> findByMemberIdForUpdate(@Param("memberId") Long memberId);
}
