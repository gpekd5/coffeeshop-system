package com.example.coffeeorder.point.service;

import java.util.Map;

import com.example.coffeeorder.common.exception.BusinessException;
import com.example.coffeeorder.common.exception.ErrorCode;
import com.example.coffeeorder.common.response.PageResponse;
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
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PointService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
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
        PageRequest pageRequest = createPageRequest(
                page,
                size,
                sort
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

    private PageRequest createPageRequest(
            String page,
            String size,
            String sort
    ) {
        return PageRequest.of(
                parsePage(page),
                parseSize(size),
                parseSort(sort)
        );
    }

    private int parsePage(String page) {
        int parsedPage = parseInteger(
                page,
                DEFAULT_PAGE
        );

        if (parsedPage < 0) {
            throw new BusinessException(ErrorCode.INVALID_PAGE_REQUEST);
        }

        return parsedPage;
    }

    private int parseSize(String size) {
        int parsedSize = parseInteger(
                size,
                DEFAULT_SIZE
        );

        if (parsedSize <= 0) {
            throw new BusinessException(ErrorCode.INVALID_PAGE_REQUEST);
        }

        return parsedSize;
    }

    private int parseInteger(
            String value,
            int defaultValue
    ) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_PAGE_REQUEST);
        }
    }

    private Sort parseSort(String sort) {
        String sortExpression =
                sort == null || sort.isBlank() ? DEFAULT_SORT : sort.trim();
        String[] parts = sortExpression.split(",");

        if (parts.length > 2 || parts[0].isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PAGE_REQUEST);
        }

        String property = SORT_PROPERTIES.get(parts[0].trim());

        if (property == null) {
            throw new BusinessException(ErrorCode.INVALID_PAGE_REQUEST);
        }

        return Sort.by(
                parseDirection(parts),
                property
        );
    }

    private Sort.Direction parseDirection(String[] parts) {
        if (parts.length == 1 || parts[1].isBlank()) {
            return Sort.Direction.ASC;
        }

        try {
            return Sort.Direction.fromString(parts[1].trim());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_PAGE_REQUEST);
        }
    }
}
