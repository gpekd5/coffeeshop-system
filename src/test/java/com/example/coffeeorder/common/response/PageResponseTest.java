package com.example.coffeeorder.common.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PageResponseTest {

    @Test
    void Page를_공통_페이지_응답으로_변환한다() {
        // given
        Page<String> page = new PageImpl<>(
                List.of("americano", "latte"),
                PageRequest.of(1, 2),
                5
        );

        // when
        PageResponse<String> response = PageResponse.from(page);

        // then
        assertThat(response.content()).containsExactly("americano", "latte");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
    }
}
