package com.example.coffeeorder.order.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.example.coffeeorder.common.exception.BusinessException;
import com.example.coffeeorder.common.exception.ErrorCode;
import com.example.coffeeorder.common.response.PageResponse;
import com.example.coffeeorder.order.dto.response.AdminOrderDetailResponse;
import com.example.coffeeorder.order.dto.response.AdminOrderSummaryResponse;
import com.example.coffeeorder.order.dto.response.OrderDetailResponse;
import com.example.coffeeorder.order.dto.response.OrderSummaryResponse;
import com.example.coffeeorder.order.entity.Order;
import com.example.coffeeorder.order.entity.OrderChannel;
import com.example.coffeeorder.order.entity.OrderItem;
import com.example.coffeeorder.order.entity.OrderStatus;
import com.example.coffeeorder.order.repository.OrderItemRepository;
import com.example.coffeeorder.order.repository.OrderRepository;
import com.example.coffeeorder.order.repository.OrderSpecifications;
import com.example.coffeeorder.payment.entity.Payment;
import com.example.coffeeorder.payment.repository.PaymentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderQueryService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final String DEFAULT_SORT = "orderedAt,desc";
    private static final Map<String, String> SORT_PROPERTIES = Map.of(
            "orderId",
            "id",
            "orderNumber",
            "orderNumber",
            "status",
            "status",
            "orderChannel",
            "orderChannel",
            "totalAmount",
            "totalAmount",
            "orderedAt",
            "orderedAt",
            "memberId",
            "member.id",
            "memberEmail",
            "member.email"
    );

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;

    public OrderQueryService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            PaymentRepository paymentRepository
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentRepository = paymentRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> getMyOrders(
            Long memberId,
            String status,
            String page,
            String size,
            String sort
    ) {
        OrderStatus orderStatus = parseStatus(status);
        PageRequest pageRequest = createPageRequest(
                page,
                size,
                sort
        );
        Page<Order> orders = orderRepository.findAll(
                OrderSpecifications.userSearch(
                        memberId,
                        orderStatus
                ),
                pageRequest
        );
        Map<Long, List<OrderItem>> itemsByOrderId =
                findItemsByOrderId(orders.getContent());

        return PageResponse.from(orders.map(order -> OrderSummaryResponse.of(
                order,
                itemsByOrderId.getOrDefault(
                        order.getId(),
                        List.of()
                )
        )));
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse getMyOrder(
            Long memberId,
            Long orderId
    ) {
        Order order = findOrderWithMember(orderId);

        if (!memberId.equals(order.getMemberId())) {
            throw new BusinessException(ErrorCode.ORDER_FORBIDDEN);
        }

        return OrderDetailResponse.of(
                order,
                findOrderItems(orderId),
                findPayment(orderId)
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminOrderSummaryResponse> getAdminOrders(
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
        PageRequest pageRequest = createPageRequest(
                page,
                size,
                sort
        );

        Page<AdminOrderSummaryResponse> orders = orderRepository.findAll(
                        OrderSpecifications.adminSearch(
                                parseMemberId(memberId),
                                parseStatus(status),
                                parseOrderChannel(orderChannel),
                                parsedStartDateTime,
                                parsedEndDateTime
                        ),
                        pageRequest
                )
                .map(AdminOrderSummaryResponse::from);

        return PageResponse.from(orders);
    }

    @Transactional(readOnly = true)
    public AdminOrderDetailResponse getAdminOrder(Long orderId) {
        Order order = findOrderWithMember(orderId);

        return AdminOrderDetailResponse.of(
                order,
                findOrderItems(orderId),
                findPayment(orderId)
        );
    }

    private Order findOrderWithMember(Long orderId) {
        return orderRepository.findByIdWithMember(orderId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ORDER_NOT_FOUND
                ));
    }

    private List<OrderItem> findOrderItems(Long orderId) {
        return orderItemRepository.findAllByOrder_IdOrderByIdAsc(orderId);
    }

    private Payment findPayment(Long orderId) {
        return paymentRepository.findByOrder_Id(orderId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.PAYMENT_NOT_FOUND
                ));
    }

    private Map<Long, List<OrderItem>> findItemsByOrderId(List<Order> orders) {
        if (orders.isEmpty()) {
            return Map.of();
        }

        List<Long> orderIds = orders.stream()
                .map(Order::getId)
                .toList();

        return orderItemRepository.findAllByOrderIds(orderIds)
                .stream()
                .collect(Collectors.groupingBy(
                        orderItem -> orderItem.getOrder()
                                .getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    private OrderStatus parseStatus(String status) {
        if (status == null) {
            return null;
        }

        if (status.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
        }

        try {
            return OrderStatus.valueOf(status.trim());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
        }
    }

    private OrderChannel parseOrderChannel(String orderChannel) {
        if (orderChannel == null) {
            return null;
        }

        if (orderChannel.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_CHANNEL);
        }

        try {
            return OrderChannel.valueOf(orderChannel.trim());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_CHANNEL);
        }
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
