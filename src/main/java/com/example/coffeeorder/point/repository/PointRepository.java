package com.example.coffeeorder.point.repository;

import java.util.Optional;

import com.example.coffeeorder.point.entity.Point;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointRepository extends JpaRepository<Point, Long> {

    Optional<Point> findByMemberId(Long memberId);
}
