package com.example.coffeeorder.auth.service;

import java.util.Locale;

import com.example.coffeeorder.auth.dto.request.LoginRequest;
import com.example.coffeeorder.auth.dto.request.ReissueTokenRequest;
import com.example.coffeeorder.auth.dto.response.LoginResponse;
import com.example.coffeeorder.common.exception.BusinessException;
import com.example.coffeeorder.common.exception.ErrorCode;
import com.example.coffeeorder.common.security.AuthMember;
import com.example.coffeeorder.common.security.JwtTokenProvider;
import com.example.coffeeorder.common.security.TokenResponse;
import com.example.coffeeorder.common.security.TokenStore;
import com.example.coffeeorder.member.entity.Member;
import com.example.coffeeorder.member.repository.MemberRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final String BEARER_PREFIX = "Bearer ";

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenStore tokenStore;

    public AuthService(
            MemberRepository memberRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            TokenStore tokenStore
    ) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.tokenStore = tokenStore;
    }

    public LoginResponse login(LoginRequest request) {
        Member member = memberRepository.findByEmail(
                        normalizeEmail(request.email())
                )
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_LOGIN
                ));

        validatePassword(
                request.password(),
                member.getPassword()
        );
        validateMemberStatus(member);

        TokenResponse tokenResponse = jwtTokenProvider.createLoginTokens(member);

        tokenStore.saveRefreshToken(
                member.getId(),
                tokenResponse.refreshToken(),
                tokenResponse.refreshTokenExpiresIn()
        );

        return LoginResponse.from(tokenResponse);
    }

    public LoginResponse reissue(ReissueTokenRequest request) {
        AuthMember authMember =
                jwtTokenProvider.getRefreshAuthMember(request.refreshToken());

        if (!tokenStore.matchesRefreshToken(
                authMember.memberId(),
                request.refreshToken()
        )) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }

        Member member = memberRepository.findById(authMember.memberId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MEMBER_NOT_FOUND
                ));
        validateMemberStatus(member);

        TokenResponse tokenResponse = jwtTokenProvider.createLoginTokens(member);

        tokenStore.saveRefreshToken(
                member.getId(),
                tokenResponse.refreshToken(),
                tokenResponse.refreshTokenExpiresIn()
        );

        return LoginResponse.from(tokenResponse);
    }

    public void logout(
            AuthMember authMember,
            String authorization
    ) {
        String accessToken = extractBearerToken(authorization);
        long remainingSeconds =
                jwtTokenProvider.getAccessTokenRemainingSeconds(accessToken);

        tokenStore.blacklistAccessToken(
                accessToken,
                remainingSeconds
        );
        tokenStore.deleteRefreshToken(authMember.memberId());
    }

    private String normalizeEmail(String email) {
        return email.trim()
                .toLowerCase(Locale.ROOT);
    }

    private void validatePassword(
            String rawPassword,
            String encryptedPassword
    ) {
        if (!passwordEncoder.matches(
                rawPassword,
                encryptedPassword
        )) {
            throw new BusinessException(ErrorCode.INVALID_LOGIN);
        }
    }

    private void validateMemberStatus(Member member) {
        if (member.isInactive()) {
            throw new BusinessException(ErrorCode.MEMBER_INACTIVE);
        }

        if (member.isWithdrawn()) {
            throw new BusinessException(ErrorCode.MEMBER_WITHDRAWN);
        }

        if (!member.isActive()) {
            throw new BusinessException(ErrorCode.INVALID_LOGIN);
        }
    }

    private String extractBearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        return authorization.substring(BEARER_PREFIX.length());
    }
}
