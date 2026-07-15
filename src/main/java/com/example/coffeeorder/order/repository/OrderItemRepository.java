package com.example.coffeeorder.order.repository;

import java.util.Collection;
import java.util.List;

import com.example.coffeeorder.order.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findAllByOrder_IdOrderByIdAsc(Long orderId);

    @Query("""
            select oi
            from OrderItem oi
            where oi.order.id in :orderIds
            order by oi.order.id asc, oi.id asc
            """)
    List<OrderItem> findAllByOrderIds(@Param("orderIds") Collection<Long> orderIds);
}
