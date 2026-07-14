package com.example.coffeeorder.order.repository;

import com.example.coffeeorder.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
