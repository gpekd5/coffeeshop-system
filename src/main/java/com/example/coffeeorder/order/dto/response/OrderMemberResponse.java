package com.example.coffeeorder.order.dto.response;

import com.example.coffeeorder.member.entity.Member;

public record OrderMemberResponse(
        Long memberId,
        String email,
        String name
) {

    public static OrderMemberResponse from(Member member) {
        return new OrderMemberResponse(
                member.getId(),
                member.getEmail(),
                member.getName()
        );
    }
}
