package com.example.coffeeorder.order.service;

import java.util.List;
import java.util.Map;

import com.example.coffeeorder.common.exception.BusinessException;
import com.example.coffeeorder.common.exception.ErrorCode;
import com.example.coffeeorder.common.response.PageResponse;
import com.example.coffeeorder.common.util.EnumRequestParser;
import com.example.coffeeorder.order.dto.response.OrderDetailResponse;
import com.example.coffeeorder.order.dto.response.OrderSummaryResponse;
import com.example.coffeeorder.order.entity.Order;
import com.example.coffeeorder.order.entity.OrderItem;
import com.example.coffeeorder.order.entity.OrderStatus;
import com.example.coffeeorder.order.repository.OrderRepository;
import com.example.coffeeorder.order.repository.OrderSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserOrderQueryService {

    private final OrderRepository orderRepository;
    private final OrderQueryReader orderQueryReader;

    public UserOrderQueryService(
            OrderRepository orderRepository,
            OrderQueryReader orderQueryReader
    ) {
        this.orderRepository = orderRepository;
        this.orderQueryReader = orderQueryReader;
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> getMyOrders(
            Long memberId,
            String status,
            String page,
            String size,
            String sort
    ) {
        OrderStatus orderStatus = EnumRequestParser.parseOptional(
                status,
                OrderStatus.class,
                ErrorCode.INVALID_ORDER_STATUS
        );
        PageRequest pageRequest = OrderQueryPageRequests.create(
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
                orderQueryReader.findItemsByOrderId(orders.getContent());

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
        Order order = orderQueryReader.findOrderWithMember(orderId);

        if (!memberId.equals(order.getMemberId())) {
            throw new BusinessException(ErrorCode.ORDER_FORBIDDEN);
        }

        return OrderDetailResponse.of(
                order,
                orderQueryReader.findOrderItems(orderId),
                orderQueryReader.findPayment(orderId)
        );
    }
}
