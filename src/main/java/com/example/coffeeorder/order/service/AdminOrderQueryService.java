package com.example.coffeeorder.order.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

import com.example.coffeeorder.common.exception.BusinessException;
import com.example.coffeeorder.common.exception.ErrorCode;
import com.example.coffeeorder.common.response.PageResponse;
import com.example.coffeeorder.common.util.EnumRequestParser;
import com.example.coffeeorder.order.dto.response.AdminOrderDetailResponse;
import com.example.coffeeorder.order.dto.response.AdminOrderSummaryResponse;
import com.example.coffeeorder.order.entity.Order;
import com.example.coffeeorder.order.entity.OrderChannel;
import com.example.coffeeorder.order.entity.OrderStatus;
import com.example.coffeeorder.order.repository.OrderRepository;
import com.example.coffeeorder.order.repository.OrderSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminOrderQueryService {

    private final OrderRepository orderRepository;
    private final OrderQueryReader orderQueryReader;

    public AdminOrderQueryService(
            OrderRepository orderRepository,
            OrderQueryReader orderQueryReader
    ) {
        this.orderRepository = orderRepository;
        this.orderQueryReader = orderQueryReader;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminOrderSummaryResponse> getOrders(
            String memberId,
            String status,
            String orderChannel,
            String startDateTime,
            String endDateTime,
            String page,
            String size,
            String sort
    ) {
        LocalDateTime parsedStartDateTime = parseDateTime(startDateTime);
        LocalDateTime parsedEndDateTime = parseDateTime(endDateTime);
        validateDateTimeRange(
                parsedStartDateTime,
                parsedEndDateTime
        );
        PageRequest pageRequest = OrderQueryPageRequests.create(
                page,
                size,
                sort
        );

        Page<AdminOrderSummaryResponse> orders = orderRepository.findAll(
                        OrderSpecifications.adminSearch(
                                parseMemberId(memberId),
                                EnumRequestParser.parseOptional(
                                        status,
                                        OrderStatus.class,
                                        ErrorCode.INVALID_ORDER_STATUS
                                ),
                                EnumRequestParser.parseOptional(
                                        orderChannel,
                                        OrderChannel.class,
                                        ErrorCode.INVALID_ORDER_CHANNEL
                                ),
                                parsedStartDateTime,
                                parsedEndDateTime
                        ),
                        pageRequest
                )
                .map(AdminOrderSummaryResponse::from);

        return PageResponse.from(orders);
    }

    @Transactional(readOnly = true)
    public AdminOrderDetailResponse getOrder(Long orderId) {
        Order order = orderQueryReader.findOrderWithMember(orderId);

        return AdminOrderDetailResponse.of(
                order,
                orderQueryReader.findOrderItems(orderId),
                orderQueryReader.findPayment(orderId)
        );
    }

    private Long parseMemberId(String memberId) {
        if (memberId == null) {
            return null;
        }

        if (memberId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        try {
            long parsedMemberId = Long.parseLong(memberId.trim());

            if (parsedMemberId <= 0) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }

            return parsedMemberId;
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private LocalDateTime parseDateTime(String dateTime) {
        if (dateTime == null) {
            return null;
        }

        if (dateTime.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        try {
            return LocalDateTime.parse(dateTime.trim());
        } catch (DateTimeParseException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private void validateDateTimeRange(
            LocalDateTime startDateTime,
            LocalDateTime endDateTime
    ) {
        if (startDateTime != null
                && endDateTime != null
                && startDateTime.isAfter(endDateTime)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
