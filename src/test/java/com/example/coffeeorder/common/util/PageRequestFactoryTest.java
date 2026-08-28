package com.example.coffeeorder.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import com.example.coffeeorder.common.exception.BusinessException;
import com.example.coffeeorder.common.exception.ErrorCode;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

class PageRequestFactoryTest {

    private static final String DEFAULT_SORT = "createdAt,desc";
    private static final Map<String, String> SORT_PROPERTIES = Map.of(
            "createdAt",
            "createdAt",
            "memberId",
            "member.id"
    );

    @Test
    void 페이지와_정렬이_없으면_기본값을_사용한다() {
        // when
        PageRequest pageRequest = PageRequestFactory.create(
                null,
                null,
                null,
                DEFAULT_SORT,
                SORT_PROPERTIES
        );

        // then
        assertThat(pageRequest.getPageNumber()).isZero();
        assertThat(pageRequest.getPageSize()).isEqualTo(20);
        assertThat(orderFor(
                pageRequest,
                "createdAt"
        ).getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void 페이지와_정렬_요청값을_PageRequest로_변환한다() {
        // when
        PageRequest pageRequest = PageRequestFactory.create(
                "2",
                "50",
                "memberId,asc",
                DEFAULT_SORT,
                SORT_PROPERTIES
        );

        // then
        assertThat(pageRequest.getPageNumber()).isEqualTo(2);
        assertThat(pageRequest.getPageSize()).isEqualTo(50);
        assertThat(orderFor(
                pageRequest,
                "member.id"
        ).getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void 정렬_방향을_생략하면_오름차순을_사용한다() {
        // when
        PageRequest pageRequest = PageRequestFactory.create(
                null,
                null,
                "memberId",
                DEFAULT_SORT,
                SORT_PROPERTIES
        );

        // then
        assertThat(orderFor(
                pageRequest,
                "member.id"
        ).getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void 유효하지_않은_페이징이면_예외가_발생한다() {
        assertInvalidPageRequest(() -> PageRequestFactory.create(
                "-1",
                null,
                null,
                DEFAULT_SORT,
                SORT_PROPERTIES
        ));
        assertInvalidPageRequest(() -> PageRequestFactory.create(
                null,
                "0",
                null,
                DEFAULT_SORT,
                SORT_PROPERTIES
        ));
        assertInvalidPageRequest(() -> PageRequestFactory.create(
                "abc",
                null,
                null,
                DEFAULT_SORT,
                SORT_PROPERTIES
        ));
    }

    @Test
    void 유효하지_않은_정렬이면_예외가_발생한다() {
        assertInvalidPageRequest(() -> PageRequestFactory.create(
                null,
                null,
                "deletedAt,desc",
                DEFAULT_SORT,
                SORT_PROPERTIES
        ));
        assertInvalidPageRequest(() -> PageRequestFactory.create(
                null,
                null,
                ",desc",
                DEFAULT_SORT,
                SORT_PROPERTIES
        ));
        assertInvalidPageRequest(() -> PageRequestFactory.create(
                null,
                null,
                "createdAt,desc,extra",
                DEFAULT_SORT,
                SORT_PROPERTIES
        ));
        assertInvalidPageRequest(() -> PageRequestFactory.create(
                null,
                null,
                "createdAt,wrong",
                DEFAULT_SORT,
                SORT_PROPERTIES
        ));
    }

    private Sort.Order orderFor(
            PageRequest pageRequest,
            String property
    ) {
        return pageRequest.getSort()
                .getOrderFor(property);
    }

    private void assertInvalidPageRequest(ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_PAGE_REQUEST);
    }
}
