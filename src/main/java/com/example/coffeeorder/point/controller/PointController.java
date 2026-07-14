package com.example.coffeeorder.point.controller;

import com.example.coffeeorder.common.response.ApiResponse;
import com.example.coffeeorder.common.response.PageResponse;
import com.example.coffeeorder.common.security.AuthMember;
import com.example.coffeeorder.point.dto.request.PointChargeRequest;
import com.example.coffeeorder.point.dto.response.PointBalanceResponse;
import com.example.coffeeorder.point.dto.response.PointChargeResponse;
import com.example.coffeeorder.point.dto.response.PointHistoryResponse;
import com.example.coffeeorder.point.service.PointService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/points")
public class PointController {

    private final PointService pointService;

    public PointController(PointService pointService) {
        this.pointService = pointService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PointBalanceResponse>> getBalance(
            @AuthenticationPrincipal AuthMember authMember
    ) {
        PointBalanceResponse response =
                pointService.getBalance(authMember.memberId());

        return ResponseEntity.ok(
                ApiResponse.success(
                        "포인트 잔액 조회에 성공했습니다.",
                        response
                )
        );
    }

    @PostMapping("/charge")
    public ResponseEntity<ApiResponse<PointChargeResponse>> charge(
            @AuthenticationPrincipal AuthMember authMember,
            @Valid @RequestBody PointChargeRequest request
    ) {
        PointChargeResponse response = pointService.charge(
                authMember.memberId(),
                request
        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "포인트가 충전되었습니다.",
                        response
                )
        );
    }

    @GetMapping("/histories")
    public ResponseEntity<ApiResponse<PageResponse<PointHistoryResponse>>> getHistories(
            @AuthenticationPrincipal AuthMember authMember,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size,
            @RequestParam(required = false) String sort
    ) {
        PageResponse<PointHistoryResponse> response = pointService.getHistories(
                authMember.memberId(),
                type,
                page,
                size,
                sort
        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "포인트 이력 조회에 성공했습니다.",
                        response
                )
        );
    }
}
