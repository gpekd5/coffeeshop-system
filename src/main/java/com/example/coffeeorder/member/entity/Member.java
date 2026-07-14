package com.example.coffeeorder.member.entity;

import java.time.LocalDateTime;

import com.example.coffeeorder.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "members",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_members_email",
                columnNames = "email"
        )
)
public class Member extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            nullable = false,
            length = 255
    )
    private String email;

    @Column(
            nullable = false,
            length = 255
    )
    private String password;

    @Column(
            nullable = false,
            length = 100
    )
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 20
    )
    private MemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 20
    )
    private MemberStatus status;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected Member() {
    }

    private Member(
            String email,
            String password,
            String name,
            MemberRole role,
            MemberStatus status
    ) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.role = role;
        this.status = status;
    }

    public static Member create(
            String email,
            String encryptedPassword,
            String name
    ) {
        return new Member(
                email,
                encryptedPassword,
                name,
                MemberRole.USER,
                MemberStatus.ACTIVE
        );
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public String getName() {
        return name;
    }

    public MemberRole getRole() {
        return role;
    }

    public MemberStatus getStatus() {
        return status;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }
}
