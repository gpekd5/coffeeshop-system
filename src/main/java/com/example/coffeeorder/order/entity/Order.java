package com.example.coffeeorder.order.entity;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

import com.example.coffeeorder.common.entity.BaseEntity;
import com.example.coffeeorder.common.exception.BusinessException;
import com.example.coffeeorder.common.exception.ErrorCode;
import com.example.coffeeorder.member.entity.Member;
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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "orders",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_orders_order_number",
                columnNames = "order_number"
        )
)
public class Order extends BaseEntity {

    private static final DateTimeFormatter ORDER_DATE_FORMAT =
            DateTimeFormatter.BASIC_ISO_DATE;

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

    @Column(
            name = "order_number",
            nullable = false,
            length = 40
    )
    private String orderNumber;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "order_channel",
            nullable = false,
            length = 20
    )
    private OrderChannel orderChannel;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 20
    )
    private OrderStatus status;

    @Column(
            name = "total_amount",
            nullable = false
    )
    private long totalAmount;

    @Column(
            name = "ordered_at",
            nullable = false
    )
    private LocalDateTime orderedAt;

    protected Order() {
    }

    private Order(
            Member member,
            String orderNumber,
            String idempotencyKey,
            OrderChannel orderChannel,
            OrderStatus status,
            long totalAmount,
            LocalDateTime orderedAt
    ) {
        validateTotalAmount(totalAmount);

        this.member = member;
        this.orderNumber = orderNumber;
        this.idempotencyKey = idempotencyKey;
        this.orderChannel = orderChannel;
        this.status = status;
        this.totalAmount = totalAmount;
        this.orderedAt = orderedAt;
    }

    public static Order completeWebCartOrder(
            Member member,
            long totalAmount,
            LocalDateTime orderedAt
    ) {
        return new Order(
                member,
                createOrderNumber(orderedAt),
                null,
                OrderChannel.WEB_CART,
                OrderStatus.COMPLETED,
                totalAmount,
                orderedAt
        );
    }

    private static String createOrderNumber(LocalDateTime orderedAt) {
        String datePart = orderedAt.format(ORDER_DATE_FORMAT);
        String randomPart = UUID.randomUUID()
                .toString()
                .replace(
                        "-",
                        ""
                )
                .substring(
                        0,
                        10
                )
                .toUpperCase(Locale.ROOT);

        return "ORD-" + datePart + "-" + randomPart;
    }

    private static void validateTotalAmount(long totalAmount) {
        if (totalAmount <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
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

    public String getOrderNumber() {
        return orderNumber;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public OrderChannel getOrderChannel() {
        return orderChannel;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public long getTotalAmount() {
        return totalAmount;
    }

    public LocalDateTime getOrderedAt() {
        return orderedAt;
    }
}
