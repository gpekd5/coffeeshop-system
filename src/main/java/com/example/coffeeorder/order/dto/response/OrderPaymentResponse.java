package com.example.coffeeorder.order.dto.response;

import java.time.LocalDateTime;

import com.example.coffeeorder.payment.entity.Payment;
import com.example.coffeeorder.payment.entity.PaymentMethod;
import com.example.coffeeorder.payment.entity.PaymentStatus;

public record OrderPaymentResponse(
        Long paymentId,
        PaymentMethod paymentMethod,
        PaymentStatus paymentStatus,
        Long amount,
        LocalDateTime paidAt
) {

    public static OrderPaymentResponse from(Payment payment) {
        return new OrderPaymentResponse(
                payment.getId(),
                payment.getPaymentMethod(),
                payment.getStatus(),
                payment.getAmount(),
                payment.getPaidAt()
        );
    }
}
