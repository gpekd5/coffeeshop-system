package com.example.coffeeorder.payment.entity;

import java.time.LocalDateTime;

import com.example.coffeeorder.common.entity.BaseEntity;
import com.example.coffeeorder.common.exception.BusinessException;
import com.example.coffeeorder.common.exception.ErrorCode;
import com.example.coffeeorder.member.entity.Member;
import com.example.coffeeorder.order.entity.Order;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "payments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_payments_order_id",
                columnNames = "order_id"
        )
)
public class Payment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "order_id",
            nullable = false
    )
    private Order order;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "member_id",
            nullable = false
    )
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "payment_method",
            nullable = false,
            length = 20
    )
    private PaymentMethod paymentMethod;

    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 20
    )
    private PaymentStatus status;

    @Column(
            name = "external_payment_id",
            length = 100
    )
    private String externalPaymentId;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    protected Payment() {
    }

    private Payment(
            Order order,
            Member member,
            PaymentMethod paymentMethod,
            long amount,
            PaymentStatus status,
            String externalPaymentId,
            LocalDateTime paidAt,
            LocalDateTime cancelledAt
    ) {
        validateAmount(
                order,
                amount
        );

        this.order = order;
        this.member = member;
        this.paymentMethod = paymentMethod;
        this.amount = amount;
        this.status = status;
        this.externalPaymentId = externalPaymentId;
        this.paidAt = paidAt;
        this.cancelledAt = cancelledAt;
    }

    public static Payment completePointPayment(
            Order order,
            Member member,
            long amount,
            LocalDateTime paidAt
    ) {
        return new Payment(
                order,
                member,
                PaymentMethod.POINT,
                amount,
                PaymentStatus.COMPLETED,
                null,
                paidAt,
                null
        );
    }

    private static void validateAmount(
            Order order,
            long amount
    ) {
        if (amount <= 0 || amount != order.getTotalAmount()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    public Long getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public Member getMember() {
        return member;
    }

    public Long getMemberId() {
        return member.getId();
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public long getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getExternalPaymentId() {
        return externalPaymentId;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }
}
