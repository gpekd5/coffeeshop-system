package com.example.coffeeorder.member.service;

import com.example.coffeeorder.common.exception.BusinessException;
import com.example.coffeeorder.common.exception.ErrorCode;
import com.example.coffeeorder.common.security.JwtTokenProvider;
import com.example.coffeeorder.common.security.TokenStore;
import org.springframework.stereotype.Service;

@Service
public class MemberWithdrawalService {

    private static final String BEARER_PREFIX = "Bearer ";

    private final MemberService memberService;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenStore tokenStore;

    public MemberWithdrawalService(
            MemberService memberService,
            JwtTokenProvider jwtTokenProvider,
            TokenStore tokenStore
    ) {
        this.memberService = memberService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.tokenStore = tokenStore;
    }

    public void withdraw(
            Long memberId,
            String authorization
    ) {
        String accessToken = extractBearerToken(authorization);
        long remainingSeconds =
                jwtTokenProvider.getAccessTokenRemainingSeconds(accessToken);

        memberService.withdraw(memberId);

        tokenStore.logoutTokens(
                memberId,
                accessToken,
                remainingSeconds
        );
    }

    private String extractBearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        return authorization.substring(BEARER_PREFIX.length());
    }
}
