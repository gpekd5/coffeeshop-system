package com.example.coffeeorder.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.coffeeorder.common.exception.BusinessException;
import com.example.coffeeorder.common.exception.ErrorCode;
import com.example.coffeeorder.common.security.JwtTokenProvider;
import com.example.coffeeorder.common.security.TokenStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberWithdrawalServiceTest {

    @Mock
    private MemberService memberService;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private TokenStore tokenStore;

    @InjectMocks
    private MemberWithdrawalService memberWithdrawalService;

    @Test
    void 회원_상태_변경_트랜잭션이_끝난_뒤_토큰을_정리한다() {
        given(jwtTokenProvider.getAccessTokenRemainingSeconds("access-token"))
                .willReturn(60L);

        memberWithdrawalService.withdraw(
                1L,
                "Bearer access-token"
        );

        InOrder inOrder = inOrder(
                memberService,
                tokenStore
        );
        inOrder.verify(memberService)
                .withdraw(1L);
        inOrder.verify(tokenStore)
                .logoutTokens(
                        1L,
                        "access-token",
                        60L
                );
    }

    @Test
    void 회원_상태_변경이_실패하면_토큰을_정리하지_않는다() {
        given(jwtTokenProvider.getAccessTokenRemainingSeconds("access-token"))
                .willReturn(60L);
        willThrow(new BusinessException(ErrorCode.MEMBER_ALREADY_WITHDRAWN))
                .given(memberService)
                .withdraw(1L);

        assertThatThrownBy(() -> memberWithdrawalService.withdraw(
                1L,
                "Bearer access-token"
        ))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEMBER_ALREADY_WITHDRAWN)
                );

        verify(tokenStore, never()).logoutTokens(
                anyLong(),
                anyString(),
                anyLong()
        );
    }
}
