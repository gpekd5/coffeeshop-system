package com.example.coffeeorder.member.controller;

import com.example.coffeeorder.common.response.ApiResponse;
import com.example.coffeeorder.common.security.AuthMember;
import com.example.coffeeorder.member.dto.response.MyInfoResponse;
import com.example.coffeeorder.member.service.MemberService;
import com.example.coffeeorder.member.service.MemberWithdrawalService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/members")
public class MemberController {

    private final MemberService memberService;
    private final MemberWithdrawalService memberWithdrawalService;

    public MemberController(
            MemberService memberService,
            MemberWithdrawalService memberWithdrawalService
    ) {
        this.memberService = memberService;
        this.memberWithdrawalService = memberWithdrawalService;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MyInfoResponse>> getMyInfo(
            @AuthenticationPrincipal AuthMember authMember
    ) {
        MyInfoResponse response =
                memberService.getMyInfo(authMember.memberId());

        return ResponseEntity.ok(
                ApiResponse.success(
                        "내 정보 조회에 성공했습니다.",
                        response
                )
        );
    }

    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> withdraw(
            @AuthenticationPrincipal AuthMember authMember,
            @RequestHeader("Authorization") String authorization
    ) {
        memberWithdrawalService.withdraw(
                authMember.memberId(),
                authorization
        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "회원 탈퇴가 완료되었습니다.",
                        null
                )
        );
    }
}
