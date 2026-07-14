package com.example.coffeeorder.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import com.example.coffeeorder.common.exception.ErrorCode;
import com.example.coffeeorder.member.entity.Member;
import com.example.coffeeorder.member.entity.MemberRole;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class JwtTokenProviderTest {

    private static final String SECRET =
            "test-jwt-secret-key-must-be-longer-than-32";
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-14T00:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );

    private final JwtTokenProvider jwtTokenProvider =
            new JwtTokenProvider(
                    new ObjectMapper(),
                    CLOCK,
                    SECRET,
                    1800L,
                    1209600L
            );

    @Test
    void AccessToken에서_인증_사용자를_복원한다() {
        Member member = member();
        String token = jwtTokenProvider.createToken(
                member,
                "ACCESS",
                1800L
        );

        AuthMember authMember = jwtTokenProvider.getAuthMember(token);

        assertThat(authMember.memberId()).isEqualTo(1L);
        assertThat(authMember.email()).isEqualTo("test@example.com");
        assertThat(authMember.role()).isEqualTo(MemberRole.USER);
    }

    @Test
    void 만료된_토큰이면_EXPIRED_TOKEN_오류를_던진다() {
        Member member = member();
        String token = jwtTokenProvider.createToken(
                member,
                "ACCESS",
                -1L
        );

        assertThatThrownBy(() -> jwtTokenProvider.getAuthMember(token))
                .isInstanceOf(JwtAuthenticationException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EXPIRED_TOKEN);
    }

    @Test
    void RefreshToken은_인증_사용자_복원에_사용할_수_없다() {
        Member member = member();
        String token = jwtTokenProvider.createToken(
                member,
                "REFRESH",
                1800L
        );

        assertThatThrownBy(() -> jwtTokenProvider.getAuthMember(token))
                .isInstanceOf(JwtAuthenticationException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    void RefreshToken에서_인증_사용자를_복원한다() {
        Member member = member();
        String token = jwtTokenProvider.createToken(
                member,
                "REFRESH",
                1800L
        );

        AuthMember authMember = jwtTokenProvider.getRefreshAuthMember(token);

        assertThat(authMember.memberId()).isEqualTo(1L);
        assertThat(authMember.email()).isEqualTo("test@example.com");
        assertThat(authMember.role()).isEqualTo(MemberRole.USER);
    }

    @Test
    void 만료된_RefreshToken이면_EXPIRED_REFRESH_TOKEN_오류를_던진다() {
        Member member = member();
        String token = jwtTokenProvider.createToken(
                member,
                "REFRESH",
                -1L
        );

        assertThatThrownBy(() -> jwtTokenProvider.getRefreshAuthMember(token))
                .isInstanceOf(JwtAuthenticationException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EXPIRED_REFRESH_TOKEN);
    }

    @Test
    void AccessToken은_RefreshToken_재발급에_사용할_수_없다() {
        Member member = member();
        String token = jwtTokenProvider.createToken(
                member,
                "ACCESS",
                1800L
        );

        assertThatThrownBy(() -> jwtTokenProvider.getRefreshAuthMember(token))
                .isInstanceOf(JwtAuthenticationException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    void AccessToken의_남은_유효시간을_초_단위로_계산한다() {
        Member member = member();
        String token = jwtTokenProvider.createToken(
                member,
                "ACCESS",
                1800L
        );

        long remainingSeconds =
                jwtTokenProvider.getAccessTokenRemainingSeconds(token);

        assertThat(remainingSeconds).isEqualTo(1800L);
    }

    private Member member() {
        Member member = Member.create(
                "test@example.com",
                "encrypted-password",
                "홍길동"
        );
        ReflectionTestUtils.setField(
                member,
                "id",
                1L
        );

        return member;
    }
}
