package com.example.coffeeorder.menu.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.example.coffeeorder.menu.entity.Menu;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MenuRepository
        extends JpaRepository<Menu, Long>, JpaSpecificationExecutor<Menu> {

    Optional<Menu> findByIdAndDeletedAtIsNull(Long menuId);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            select m
            from Menu m
            where m.id in :menuIds
              and m.deletedAt is null
            order by m.id asc
            """)
    List<Menu> findAllByIdInForOrder(
            @Param("menuIds") Collection<Long> menuIds
    );
}
