package com.example.coffeeorder.auth.service;

import java.util.Locale;

import com.example.coffeeorder.auth.dto.request.LoginRequest;
import com.example.coffeeorder.auth.dto.response.LoginResponse;
import com.example.coffeeorder.common.exception.BusinessException;
import com.example.coffeeorder.common.exception.ErrorCode;
import com.example.coffeeorder.common.security.JwtTokenProvider;
import com.example.coffeeorder.member.entity.Member;
import com.example.coffeeorder.member.repository.MemberRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(
            MemberRepository memberRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider
    ) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Transactional(readOnly = true)
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

        return LoginResponse.from(jwtTokenProvider.createLoginTokens(member));
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
}
