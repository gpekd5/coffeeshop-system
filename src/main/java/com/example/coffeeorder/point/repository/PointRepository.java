package com.example.coffeeorder.point.repository;

import java.util.Optional;

import com.example.coffeeorder.point.entity.Point;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointRepository extends JpaRepository<Point, Long> {

    @Query("""
            select p
            from Point p
            join fetch p.member
            where p.member.id = :memberId
            """)
    Optional<Point> findByMemberId(@Param("memberId") Long memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p
            from Point p
            join fetch p.member
            where p.member.id = :memberId
            """)
    Optional<Point> findByMemberIdForUpdate(@Param("memberId") Long memberId);
}
