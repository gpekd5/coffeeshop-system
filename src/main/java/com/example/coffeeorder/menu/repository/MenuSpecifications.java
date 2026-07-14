package com.example.coffeeorder.menu.repository;

import java.util.Locale;

import com.example.coffeeorder.menu.entity.Menu;
import com.example.coffeeorder.menu.entity.MenuCategory;
import com.example.coffeeorder.menu.entity.MenuStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

public final class MenuSpecifications {

    private MenuSpecifications() {
    }

    public static Specification<Menu> publicSearch(
            MenuCategory category,
            MenuStatus status,
            String keyword
    ) {
        return (root, query, criteriaBuilder) -> {
            Predicate predicate = criteriaBuilder.isNull(root.get("deletedAt"));

            if (category != null) {
                predicate = criteriaBuilder.and(
                        predicate,
                        criteriaBuilder.equal(
                                root.get("category"),
                                category
                        )
                );
            }

            predicate = criteriaBuilder.and(
                    predicate,
                    criteriaBuilder.equal(
                            root.get("status"),
                            status
                    )
            );

            if (keyword != null && !keyword.isBlank()) {
                predicate = criteriaBuilder.and(
                        predicate,
                        criteriaBuilder.like(
                                criteriaBuilder.lower(root.get("name")),
                                "%" + keyword.trim()
                                        .toLowerCase(Locale.ROOT) + "%"
                        )
                );
            }

            return predicate;
        };
    }
}
