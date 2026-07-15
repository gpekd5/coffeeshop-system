package com.example.coffeeorder.order.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import com.example.coffeeorder.order.entity.OrderItem;
import com.example.coffeeorder.order.entity.OrderStatus;
import com.example.coffeeorder.order.repository.projection.PopularMenuAggregation;
import org.springframework.data.domain.Pageable;
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

    @Query("""
            select new com.example.coffeeorder.order.repository.projection.PopularMenuAggregation(
                m.id,
                m.name,
                m.category,
                m.price,
                m.status,
                count(distinct o.id),
                max(o.orderedAt)
            )
            from OrderItem oi
            join oi.order o
            join Menu m on m.id = oi.menuId
            where o.status = :status
              and o.orderedAt >= :startDateTime
              and o.orderedAt <= :endDateTime
            group by m.id, m.name, m.category, m.price, m.status
            order by count(distinct o.id) desc, max(o.orderedAt) desc, m.id asc
            """)
    List<PopularMenuAggregation> findPopularMenus(
            @Param("status") OrderStatus status,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime,
            Pageable pageable
    );
}
