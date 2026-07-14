package com.example.coffeeorder.point.entity;

import java.time.LocalDateTime;

import com.example.coffeeorder.member.entity.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "point_histories")
@EntityListeners(AuditingEntityListener.class)
public class PointHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "member_id",
            nullable = false
    )
    private Member member;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "payment_id")
    private Long paymentId;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 20
    )
    private PointHistoryType type;

    @Column(nullable = false)
    private long amount;

    @Column(
            name = "balance_after",
            nullable = false
    )
    private long balanceAfter;

    @CreatedDate
    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

    protected PointHistory() {
    }

    private PointHistory(
            Member member,
            Long orderId,
            Long paymentId,
            PointHistoryType type,
            long amount,
            long balanceAfter
    ) {
        this.member = member;
        this.orderId = orderId;
        this.paymentId = paymentId;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
    }

    public static PointHistory charge(
            Member member,
            long amount,
            long balanceAfter
    ) {
        return new PointHistory(
                member,
                null,
                null,
                PointHistoryType.CHARGE,
                amount,
                balanceAfter
        );
    }

    public static PointHistory use(
            Member member,
            Long orderId,
            Long paymentId,
            long amount,
            long balanceAfter
    ) {
        return new PointHistory(
                member,
                orderId,
                paymentId,
                PointHistoryType.USE,
                -amount,
                balanceAfter
        );
    }

    public Long getId() {
        return id;
    }

    public Member getMember() {
        return member;
    }

    public Long getMemberId() {
        return member.getId();
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public PointHistoryType getType() {
        return type;
    }

    public long getAmount() {
        return amount;
    }

    public long getBalanceAfter() {
        return balanceAfter;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
