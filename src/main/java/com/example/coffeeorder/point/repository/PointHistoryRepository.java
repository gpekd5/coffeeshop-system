package com.example.coffeeorder.point.repository;

import java.util.Optional;

import com.example.coffeeorder.point.entity.PointHistory;
import com.example.coffeeorder.point.entity.PointHistoryType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointHistoryRepository
        extends JpaRepository<PointHistory, Long> {

    Page<PointHistory> findAllByMember_Id(
            Long memberId,
            Pageable pageable
    );

    Page<PointHistory> findAllByMember_IdAndType(
            Long memberId,
            PointHistoryType type,
            Pageable pageable
    );

    Optional<PointHistory> findByOrderIdAndPaymentId(
            Long orderId,
            Long paymentId
    );
}
