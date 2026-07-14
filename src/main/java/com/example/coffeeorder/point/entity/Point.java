package com.example.coffeeorder.point.entity;

import com.example.coffeeorder.common.entity.BaseEntity;
import com.example.coffeeorder.member.entity.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

@Entity
@Table(
        name = "points",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_points_member_id",
                columnNames = "member_id"
        )
)
public class Point extends BaseEntity {

    private static final long INITIAL_BALANCE = 0L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "member_id",
            nullable = false
    )
    private Member member;

    @Column(nullable = false)
    private long balance;

    @Version
    @Column(nullable = false)
    private Long version;

    protected Point() {
    }

    private Point(
            Member member,
            long balance
    ) {
        this.member = member;
        this.balance = balance;
    }

    public static Point create(Member member) {
        return new Point(
                member,
                INITIAL_BALANCE
        );
    }

    public Long getId() {
        return id;
    }

    public Member getMember() {
        return member;
    }

    public long getBalance() {
        return balance;
    }

    public Long getVersion() {
        return version;
    }
}
