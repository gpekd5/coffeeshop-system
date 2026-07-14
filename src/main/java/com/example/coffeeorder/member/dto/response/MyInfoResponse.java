package com.example.coffeeorder.member.dto.response;

import java.time.LocalDateTime;

import com.example.coffeeorder.member.entity.Member;
import com.example.coffeeorder.member.entity.MemberRole;
import com.example.coffeeorder.member.entity.MemberStatus;

public record MyInfoResponse(
        Long memberId,
        String email,
        String name,
        MemberRole role,
        MemberStatus status,
        LocalDateTime createdAt
) {

    public static MyInfoResponse from(Member member) {
        return new MyInfoResponse(
                member.getId(),
                member.getEmail(),
                member.getName(),
                member.getRole(),
                member.getStatus(),
                member.getCreatedAt()
        );
    }
}
