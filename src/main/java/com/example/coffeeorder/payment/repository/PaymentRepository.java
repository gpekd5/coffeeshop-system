package com.example.coffeeorder.payment.repository;

import java.util.Optional;

import com.example.coffeeorder.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrder_Id(Long orderId);
}
