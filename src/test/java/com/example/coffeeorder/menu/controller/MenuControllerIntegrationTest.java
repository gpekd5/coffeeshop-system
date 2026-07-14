package com.example.coffeeorder.menu.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import com.example.coffeeorder.menu.entity.Menu;
import com.example.coffeeorder.menu.entity.MenuCategory;
import com.example.coffeeorder.menu.entity.MenuStatus;
import com.example.coffeeorder.menu.repository.MenuRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class MenuControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MenuRepository menuRepository;

    @BeforeEach
    void setUp() {
        menuRepository.deleteAll();
    }

    @Test
    void 기본_목록_조회는_ON_SALE_메뉴만_반환한다() throws Exception {
        Menu americano = 메뉴를_저장한다(
                "아메리카노",
                "진한 에스프레소와 물로 만든 커피",
                MenuCategory.COFFEE,
                4500L,
                MenuStatus.ON_SALE
        );
        메뉴를_저장한다(
                "카페라떼",
                "우유가 들어간 커피",
                MenuCategory.COFFEE,
                5000L,
                MenuStatus.SOLD_OUT
        );
        Menu deletedMenu = 메뉴를_저장한다(
                "삭제된 메뉴",
                "삭제된 메뉴 설명",
                MenuCategory.ETC,
                1000L,
                MenuStatus.ON_SALE
        );
        deletedMenu.delete(LocalDateTime.now());
        menuRepository.saveAndFlush(deletedMenu);

        mockMvc.perform(get("/api/v1/menus"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("메뉴 목록 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].menuId").value(americano.getId()))
                .andExpect(jsonPath("$.data.content[0].name").value("아메리카노"))
                .andExpect(jsonPath("$.data.content[0].category").value("COFFEE"))
                .andExpect(jsonPath("$.data.content[0].status").value("ON_SALE"))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1));
    }

    @Test
    void 카테고리_상태_검색어_필터와_페이징_정렬이_동작한다() throws Exception {
        메뉴를_저장한다(
                "비싼 아메리카노",
                "고급 원두 커피",
                MenuCategory.COFFEE,
                5500L,
                MenuStatus.ON_SALE
        );
        Menu cheapAmericano = 메뉴를_저장한다(
                "저렴한 아메리카노",
                "기본 원두 커피",
                MenuCategory.COFFEE,
                3500L,
                MenuStatus.ON_SALE
        );
        메뉴를_저장한다(
                "카페라떼",
                "우유가 들어간 커피",
                MenuCategory.COFFEE,
                5000L,
                MenuStatus.ON_SALE
        );
        메뉴를_저장한다(
                "딸기 스무디",
                "과일 음료",
                MenuCategory.NON_COFFEE,
                6000L,
                MenuStatus.ON_SALE
        );

        mockMvc.perform(get("/api/v1/menus")
                        .param(
                                "category",
                                "COFFEE"
                        )
                        .param(
                                "status",
                                "ON_SALE"
                        )
                        .param(
                                "keyword",
                                "아메리카노"
                        )
                        .param(
                                "page",
                                "0"
                        )
                        .param(
                                "size",
                                "1"
                        )
                        .param(
                                "sort",
                                "price,asc"
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].menuId")
                        .value(cheapAmericano.getId()))
                .andExpect(jsonPath("$.data.content[0].price").value(3500))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2));
    }

    @Test
    void 상태를_지정하면_품절_메뉴도_목록에서_조회할_수_있다() throws Exception {
        Menu soldOutMenu = 메뉴를_저장한다(
                "카페라떼",
                "우유가 들어간 커피",
                MenuCategory.COFFEE,
                5000L,
                MenuStatus.SOLD_OUT
        );
        메뉴를_저장한다(
                "아메리카노",
                "진한 에스프레소와 물로 만든 커피",
                MenuCategory.COFFEE,
                4500L,
                MenuStatus.ON_SALE
        );

        mockMvc.perform(get("/api/v1/menus")
                        .param(
                                "status",
                                "SOLD_OUT"
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].menuId").value(soldOutMenu.getId()))
                .andExpect(jsonPath("$.data.content[0].status").value("SOLD_OUT"));
    }

    @Test
    void 상세_조회는_품절_또는_판매중지_메뉴도_반환한다() throws Exception {
        Menu inactiveMenu = 메뉴를_저장한다(
                "시즌 종료 메뉴",
                "시즌 한정 음료",
                MenuCategory.ETC,
                6500L,
                MenuStatus.INACTIVE
        );

        mockMvc.perform(get("/api/v1/menus/{menuId}", inactiveMenu.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("메뉴 상세 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.menuId").value(inactiveMenu.getId()))
                .andExpect(jsonPath("$.data.name").value("시즌 종료 메뉴"))
                .andExpect(jsonPath("$.data.status").value("INACTIVE"))
                .andExpect(jsonPath("$.data.createdAt").isString())
                .andExpect(jsonPath("$.data.updatedAt").isString());
    }

    @Test
    void 삭제된_메뉴는_상세_조회되지_않는다() throws Exception {
        Menu deletedMenu = 메뉴를_저장한다(
                "삭제된 메뉴",
                "삭제된 메뉴 설명",
                MenuCategory.ETC,
                1000L,
                MenuStatus.ON_SALE
        );
        deletedMenu.delete(LocalDateTime.now());
        menuRepository.saveAndFlush(deletedMenu);

        mockMvc.perform(get("/api/v1/menus/{menuId}", deletedMenu.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("MENU_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("메뉴를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 존재하지_않는_메뉴_상세_조회는_실패한다() throws Exception {
        mockMvc.perform(get("/api/v1/menus/{menuId}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MENU_NOT_FOUND"));
    }

    @Test
    void 유효하지_않은_카테고리이면_목록_조회에_실패한다() throws Exception {
        mockMvc.perform(get("/api/v1/menus")
                        .param(
                                "category",
                                "INVALID"
                        ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MENU_CATEGORY"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 메뉴 카테고리입니다."));
    }

    @Test
    void 유효하지_않은_상태이면_목록_조회에_실패한다() throws Exception {
        mockMvc.perform(get("/api/v1/menus")
                        .param(
                                "status",
                                "INVALID"
                        ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MENU_STATUS"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 메뉴 상태입니다."));
    }

    @Test
    void 유효하지_않은_페이징이나_정렬이면_목록_조회에_실패한다() throws Exception {
        mockMvc.perform(get("/api/v1/menus")
                        .param(
                                "page",
                                "-1"
                        ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAGE_REQUEST"))
                .andExpect(jsonPath("$.message").value("잘못된 페이징 요청입니다."));

        mockMvc.perform(get("/api/v1/menus")
                        .param(
                                "sort",
                                "deletedAt,desc"
                        ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAGE_REQUEST"));
    }

    private Menu 메뉴를_저장한다(
            String name,
            String description,
            MenuCategory category,
            long price,
            MenuStatus status
    ) {
        return menuRepository.saveAndFlush(Menu.create(
                name,
                description,
                category,
                price,
                status
        ));
    }
}
