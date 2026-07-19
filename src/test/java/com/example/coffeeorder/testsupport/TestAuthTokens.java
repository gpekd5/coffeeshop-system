package com.example.coffeeorder.testsupport;

import com.example.coffeeorder.common.security.JwtTokenProvider;
import com.example.coffeeorder.member.entity.Member;

public final class TestAuthTokens {

    private TestAuthTokens() {
    }

    public static String accessToken(
            JwtTokenProvider jwtTokenProvider,
            Member member
    ) {
        return jwtTokenProvider.createLoginTokens(member)
                .accessToken();
    }
}
