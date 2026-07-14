package com.example.coffeeorder.member.dto.response;

import com.example.coffeeorder.member.entity.Member;
import com.example.coffeeorder.member.entity.MemberRole;
import com.example.coffeeorder.member.entity.MemberStatus;
import com.example.coffeeorder.point.entity.Point;

public record SignupResponse(
        Long memberId,
        String email,
        String name,
        MemberRole role,
        MemberStatus status,
        long pointBalance
) {

    public static SignupResponse from(
            Member member,
            Point point
    ) {
        return new SignupResponse(
                member.getId(),
                member.getEmail(),
                member.getName(),
                member.getRole(),
                member.getStatus(),
                point.getBalance()
        );
    }
}
