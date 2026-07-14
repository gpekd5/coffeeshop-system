package com.example.coffeeorder.member.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import com.example.coffeeorder.common.exception.BusinessException;
import com.example.coffeeorder.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

class MemberTest {

    @Test
    void 회원은_탈퇴하면_WITHDRAWN_상태가_되고_deletedAt이_기록된다() {
        Member member = Member.create(
                "test@example.com",
                "encrypted-password",
                "홍길동"
        );
        LocalDateTime deletedAt = LocalDateTime.of(
                2026,
                7,
                14,
                12,
                0
        );

        member.withdraw(deletedAt);

        assertThat(member.getStatus()).isEqualTo(MemberStatus.WITHDRAWN);
        assertThat(member.getDeletedAt()).isEqualTo(deletedAt);
        assertThat(member.isWithdrawn()).isTrue();
    }

    @Test
    void 이미_탈퇴한_회원은_다시_탈퇴할_수_없다() {
        Member member = Member.create(
                "test@example.com",
                "encrypted-password",
                "홍길동"
        );
        member.withdraw(LocalDateTime.of(
                2026,
                7,
                14,
                12,
                0
        ));

        assertThatThrownBy(() -> member.withdraw(LocalDateTime.of(
                2026,
                7,
                14,
                13,
                0
        )))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEMBER_ALREADY_WITHDRAWN)
                );
    }
}
