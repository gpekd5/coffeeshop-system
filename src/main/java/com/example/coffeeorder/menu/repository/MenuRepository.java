package com.example.coffeeorder.menu.repository;

import java.util.Optional;

import com.example.coffeeorder.menu.entity.Menu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MenuRepository
        extends JpaRepository<Menu, Long>, JpaSpecificationExecutor<Menu> {

    Optional<Menu> findByIdAndDeletedAtIsNull(Long menuId);
}
