package com.example.coffeeorder.menu.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import com.example.coffeeorder.menu.dto.response.PopularMenuResponse;
import com.example.coffeeorder.order.entity.OrderStatus;
import com.example.coffeeorder.order.repository.OrderItemRepository;
import com.example.coffeeorder.order.repository.projection.PopularMenuAggregation;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PopularMenuService {

    private static final int POPULAR_MENU_LIMIT = 3;
    private static final long POPULAR_MENU_DAYS = 7L;

    private final OrderItemRepository orderItemRepository;
    private final Clock clock;

    public PopularMenuService(
            OrderItemRepository orderItemRepository,
            Clock clock
    ) {
        this.orderItemRepository = orderItemRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PopularMenuResponse> getPopularMenus() {
        LocalDateTime endDateTime = LocalDateTime.now(clock);
        LocalDateTime startDateTime = endDateTime.minusDays(POPULAR_MENU_DAYS);

        List<PopularMenuAggregation> results =
                orderItemRepository.findPopularMenus(
                        OrderStatus.COMPLETED,
                        startDateTime,
                        endDateTime,
                        PageRequest.of(
                                0,
                                POPULAR_MENU_LIMIT
                        )
                );

        return IntStream.range(
                        0,
                        results.size()
                )
                .mapToObj(index -> PopularMenuResponse.of(
                        results.get(index),
                        index + 1
                ))
                .toList();
    }
}
