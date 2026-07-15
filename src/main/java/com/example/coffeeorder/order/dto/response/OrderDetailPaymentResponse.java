package com.example.coffeeorder.order.dto.response;

import java.time.LocalDateTime;

import com.example.coffeeorder.payment.entity.Payment;
import com.example.coffeeorder.payment.entity.PaymentMethod;
import com.example.coffeeorder.payment.entity.PaymentStatus;

public record OrderDetailPaymentResponse(
        Long paymentId,
        PaymentMethod paymentMethod,
        PaymentStatus status,
        Long amount,
        LocalDateTime paidAt,
        LocalDateTime cancelledAt
) {

    public static OrderDetailPaymentResponse from(Payment payment) {
        return new OrderDetailPaymentResponse(
                payment.getId(),
                payment.getPaymentMethod(),
                payment.getStatus(),
                payment.getAmount(),
                payment.getPaidAt(),
                payment.getCancelledAt()
        );
    }
}
