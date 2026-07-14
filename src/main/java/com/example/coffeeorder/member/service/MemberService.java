package com.example.coffeeorder.member.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;

import com.example.coffeeorder.common.exception.BusinessException;
import com.example.coffeeorder.common.exception.ErrorCode;
import com.example.coffeeorder.common.security.JwtTokenProvider;
import com.example.coffeeorder.common.security.TokenStore;
import com.example.coffeeorder.member.dto.request.SignupRequest;
import com.example.coffeeorder.member.dto.response.MyInfoResponse;
import com.example.coffeeorder.member.dto.response.SignupResponse;
import com.example.coffeeorder.member.entity.Member;
import com.example.coffeeorder.member.repository.MemberRepository;
import com.example.coffeeorder.point.entity.Point;
import com.example.coffeeorder.point.repository.PointRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {

    private static final String BEARER_PREFIX = "Bearer ";

    private final MemberRepository memberRepository;
    private final PointRepository pointRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenStore tokenStore;
    private final Clock clock;

    public MemberService(
            MemberRepository memberRepository,
            PointRepository pointRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            TokenStore tokenStore,
            Clock clock
    ) {
        this.memberRepository = memberRepository;
        this.pointRepository = pointRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.tokenStore = tokenStore;
        this.clock = clock;
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        String normalizedEmail = normalizeEmail(request.email());

        validateDuplicatedEmail(normalizedEmail);

        Member member = Member.create(
                normalizedEmail,
                passwordEncoder.encode(request.password()),
                request.name()
        );

        saveMember(member);

        Point point = pointRepository.save(Point.create(member));

        return SignupResponse.from(
                member,
                point
        );
    }

    @Transactional(readOnly = true)
    public MyInfoResponse getMyInfo(Long memberId) {
        Member member = findActiveMember(memberId);

        return MyInfoResponse.from(member);
    }

    @Transactional
    public void withdraw(
            Long memberId,
            String authorization
    ) {
        Member member = findMember(memberId);
        String accessToken = extractBearerToken(authorization);
        long remainingSeconds =
                jwtTokenProvider.getAccessTokenRemainingSeconds(accessToken);

        member.withdraw(LocalDateTime.now(clock));

        tokenStore.logoutTokens(
                member.getId(),
                accessToken,
                remainingSeconds
        );
    }

    private String normalizeEmail(String email) {
        return email.trim()
                .toLowerCase(Locale.ROOT);
    }

    private void validateDuplicatedEmail(String email) {
        if (memberRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATED_EMAIL);
        }
    }

    private void saveMember(Member member) {
        try {
            memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.DUPLICATED_EMAIL);
        }
    }

    private Member findActiveMember(Long memberId) {
        Member member = findMember(memberId);

        if (!member.isActive()) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }

        return member;
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MEMBER_NOT_FOUND
                ));
    }

    private String extractBearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        return authorization.substring(BEARER_PREFIX.length());
    }
}
