package com.example.coffeeorder.auth.controller;

import com.example.coffeeorder.auth.dto.request.LoginRequest;
import com.example.coffeeorder.auth.dto.response.LoginResponse;
import com.example.coffeeorder.auth.service.AuthService;
import com.example.coffeeorder.common.response.ApiResponse;
import com.example.coffeeorder.member.dto.request.SignupRequest;
import com.example.coffeeorder.member.dto.response.SignupResponse;
import com.example.coffeeorder.member.service.MemberService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final MemberService memberService;

    public AuthController(
            AuthService authService,
            MemberService memberService
    ) {
        this.authService = authService;
        this.memberService = memberService;
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(
            @Valid @RequestBody SignupRequest request
    ) {
        SignupResponse response = memberService.signup(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        ApiResponse.success(
                                "회원가입이 완료되었습니다.",
                                response
                        )
                );
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request
    ) {
        LoginResponse response = authService.login(request);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "로그인에 성공했습니다.",
                        response
                )
        );
    }
}
