package com.example.coffeeorder.order.repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.example.coffeeorder.order.entity.Order;
import com.example.coffeeorder.order.entity.OrderChannel;
import com.example.coffeeorder.order.entity.OrderStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

public final class OrderSpecifications {

    private OrderSpecifications() {
    }

    public static Specification<Order> userSearch(
            Long memberId,
            OrderStatus status
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(
                    root.get("member").get("id"),
                    memberId
            ));

            if (status != null) {
                predicates.add(criteriaBuilder.equal(
                        root.get("status"),
                        status
                ));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    public static Specification<Order> adminSearch(
            Long memberId,
            OrderStatus status,
            OrderChannel orderChannel,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (memberId != null) {
                predicates.add(criteriaBuilder.equal(
                        root.get("member").get("id"),
                        memberId
                ));
            }

            if (status != null) {
                predicates.add(criteriaBuilder.equal(
                        root.get("status"),
                        status
                ));
            }

            if (orderChannel != null) {
                predicates.add(criteriaBuilder.equal(
                        root.get("orderChannel"),
                        orderChannel
                ));
            }

            if (startDateTime != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                        root.get("orderedAt"),
                        startDateTime
                ));
            }

            if (endDateTime != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(
                        root.get("orderedAt"),
                        endDateTime
                ));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
