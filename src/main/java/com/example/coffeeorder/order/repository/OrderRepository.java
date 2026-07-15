package com.example.coffeeorder.order.repository;

import java.util.Optional;

import com.example.coffeeorder.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository
        extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    Optional<Order> findByMember_IdAndIdempotencyKey(
            Long memberId,
            String idempotencyKey
    );

    @Query("""
            select o
            from Order o
            join fetch o.member
            where o.id = :orderId
            """)
    Optional<Order> findByIdWithMember(@Param("orderId") Long orderId);
}
