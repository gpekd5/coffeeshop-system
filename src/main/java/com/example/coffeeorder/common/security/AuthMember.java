package com.example.coffeeorder.common.security;

import com.example.coffeeorder.member.entity.MemberRole;

public record AuthMember(
        Long memberId,
        String email,
        MemberRole role
) {
}
