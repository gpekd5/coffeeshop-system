package com.example.coffeeorder.point.service;

import java.util.Map;

import com.example.coffeeorder.common.exception.BusinessException;
import com.example.coffeeorder.common.exception.ErrorCode;
import com.example.coffeeorder.common.response.PageResponse;
import com.example.coffeeorder.common.util.PageRequestFactory;
import com.example.coffeeorder.point.dto.request.PointChargeRequest;
import com.example.coffeeorder.point.dto.response.PointBalanceResponse;
import com.example.coffeeorder.point.dto.response.PointChargeResponse;
import com.example.coffeeorder.point.dto.response.PointHistoryResponse;
import com.example.coffeeorder.point.entity.Point;
import com.example.coffeeorder.point.entity.PointHistory;
import com.example.coffeeorder.point.entity.PointHistoryType;
import com.example.coffeeorder.point.repository.PointHistoryRepository;
import com.example.coffeeorder.point.repository.PointRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PointService {

    private static final String DEFAULT_SORT = "createdAt,desc";
    private static final Map<String, String> SORT_PROPERTIES = Map.of(
            "historyId",
            "id",
            "type",
            "type",
            "amount",
            "amount",
            "balanceAfter",
            "balanceAfter",
            "orderId",
            "orderId",
            "createdAt",
            "createdAt"
    );

    private final PointRepository pointRepository;
    private final PointHistoryRepository pointHistoryRepository;

    public PointService(
            PointRepository pointRepository,
            PointHistoryRepository pointHistoryRepository
    ) {
        this.pointRepository = pointRepository;
        this.pointHistoryRepository = pointHistoryRepository;
    }

    @Transactional(readOnly = true)
    public PointBalanceResponse getBalance(Long memberId) {
        Point point = findPoint(memberId);

        return PointBalanceResponse.from(point);
    }

    @Transactional
    public PointChargeResponse charge(
            Long memberId,
            PointChargeRequest request
    ) {
        long amount = validateChargeAmount(request.amount());
        Point point = findPointForUpdate(memberId);
        long balanceBefore = point.getBalance();

        point.charge(amount);

        PointHistory history = pointHistoryRepository.saveAndFlush(
                PointHistory.charge(
                        point.getMember(),
                        amount,
                        point.getBalance()
                )
        );

        return PointChargeResponse.of(
                history,
                balanceBefore
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<PointHistoryResponse> getHistories(
            Long memberId,
            String type,
            String page,
            String size,
            String sort
    ) {
        PointHistoryType historyType = parseType(type);
        PageRequest pageRequest = PageRequestFactory.create(
                page,
                size,
                sort,
                DEFAULT_SORT,
                SORT_PROPERTIES
        );
        Page<PointHistoryResponse> histories = findHistories(
                memberId,
                historyType,
                pageRequest
        ).map(PointHistoryResponse::from);

        return PageResponse.from(histories);
    }

    private Point findPoint(Long memberId) {
        return pointRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.POINT_ACCOUNT_NOT_FOUND
                ));
    }

    private Point findPointForUpdate(Long memberId) {
        return pointRepository.findByMemberIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.POINT_ACCOUNT_NOT_FOUND
                ));
    }

    private Page<PointHistory> findHistories(
            Long memberId,
            PointHistoryType historyType,
            PageRequest pageRequest
    ) {
        if (historyType == null) {
            return pointHistoryRepository.findAllByMember_Id(
                    memberId,
                    pageRequest
            );
        }

        return pointHistoryRepository.findAllByMember_IdAndType(
                memberId,
                historyType,
                pageRequest
        );
    }

    private long validateChargeAmount(Long amount) {
        if (amount == null || amount <= 0) {
            throw new BusinessException(ErrorCode.INVALID_POINT_AMOUNT);
        }

        return amount;
    }

    private PointHistoryType parseType(String type) {
        if (type == null) {
            return null;
        }

        if (type.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_POINT_HISTORY_TYPE);
        }

        try {
            return PointHistoryType.valueOf(type.trim());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_POINT_HISTORY_TYPE);
        }
    }

}
