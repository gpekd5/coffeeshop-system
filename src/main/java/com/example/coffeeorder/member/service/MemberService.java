package com.example.coffeeorder.member.service;

import java.util.Locale;

import com.example.coffeeorder.common.exception.BusinessException;
import com.example.coffeeorder.common.exception.ErrorCode;
import com.example.coffeeorder.member.dto.request.SignupRequest;
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

    private final MemberRepository memberRepository;
    private final PointRepository pointRepository;
    private final PasswordEncoder passwordEncoder;

    public MemberService(
            MemberRepository memberRepository,
            PointRepository pointRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.memberRepository = memberRepository;
        this.pointRepository = pointRepository;
        this.passwordEncoder = passwordEncoder;
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
}
