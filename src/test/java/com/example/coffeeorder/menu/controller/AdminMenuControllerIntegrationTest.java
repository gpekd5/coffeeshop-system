package com.example.coffeeorder.menu.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.example.coffeeorder.common.security.JwtTokenProvider;
import com.example.coffeeorder.common.security.TokenStore;
import com.example.coffeeorder.member.entity.Member;
import com.example.coffeeorder.member.entity.MemberRole;
import com.example.coffeeorder.member.repository.MemberRepository;
import com.example.coffeeorder.menu.entity.Menu;
import com.example.coffeeorder.menu.entity.MenuCategory;
import com.example.coffeeorder.menu.entity.MenuStatus;
import com.example.coffeeorder.menu.repository.MenuRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import(AdminMenuControllerIntegrationTest.TestTokenStoreConfig.class)
class AdminMenuControllerIntegrationTest {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MenuRepository menuRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private TokenStore tokenStore;

    @BeforeEach
    void setUp() {
        menuRepository.deleteAll();
        memberRepository.deleteAll();

        if (tokenStore instanceof InMemoryTokenStore inMemoryTokenStore) {
            inMemoryTokenStore.clear();
        }
    }

    @Test
    void 관리자는_메뉴를_등록할_수_있다() throws Exception {
        String accessToken = 토큰을_발급한다(
                "admin@example.com",
                MemberRole.ADMIN
        );

        mockMvc.perform(
                        post("/api/v1/admin/menus")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + accessToken
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "name": "카페라떼",
                                          "description": "에스프레소와 우유로 만든 커피",
                                          "category": "COFFEE",
                                          "price": 5000
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("메뉴가 등록되었습니다."))
                .andExpect(jsonPath("$.data.menuId").isNumber())
                .andExpect(jsonPath("$.data.name").value("카페라떼"))
                .andExpect(jsonPath("$.data.description")
                        .value("에스프레소와 우유로 만든 커피"))
                .andExpect(jsonPath("$.data.category").value("COFFEE"))
                .andExpect(jsonPath("$.data.price").value(5000))
                .andExpect(jsonPath("$.data.status").value("ON_SALE"))
                .andExpect(jsonPath("$.data.createdAt").isString())
                .andExpect(jsonPath("$.data.updatedAt").isString());

        Menu savedMenu = menuRepository.findAll()
                .getFirst();

        assertThat(menuRepository.count()).isEqualTo(1);
        assertThat(savedMenu.getName()).isEqualTo("카페라떼");
        assertThat(savedMenu.getStatus()).isEqualTo(MenuStatus.ON_SALE);
        assertThat(savedMenu.isDeleted()).isFalse();
    }

    @Test
    void 일반_사용자는_관리자_메뉴_API를_호출할_수_없다() throws Exception {
        String accessToken = 토큰을_발급한다(
                "user@example.com",
                MemberRole.USER
        );

        mockMvc.perform(
                        post("/api/v1/admin/menus")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + accessToken
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "name": "카페라떼",
                                          "category": "COFFEE",
                                          "price": 5000
                                        }
                                        """)
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("접근 권한이 없습니다."));
    }

    @Test
    void 인증되지_않은_사용자는_관리자_메뉴_API를_호출할_수_없다() throws Exception {
        mockMvc.perform(
                        post("/api/v1/admin/menus")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "name": "카페라떼",
                                          "category": "COFFEE",
                                          "price": 5000
                                        }
                                        """)
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("인증되지 않은 사용자입니다."));
    }

    @Test
    void 메뉴_등록_필수값이_누락되면_검증_오류를_반환한다() throws Exception {
        String accessToken = 토큰을_발급한다(
                "admin@example.com",
                MemberRole.ADMIN
        );

        mockMvc.perform(
                        post("/api/v1/admin/menus")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + accessToken
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "description": "필수값 누락"
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.data.errors[0].field").value("category"))
                .andExpect(jsonPath("$.data.errors[1].field").value("name"))
                .andExpect(jsonPath("$.data.errors[2].field").value("price"));
    }

    @Test
    void 메뉴_등록시_카테고리_상태_가격을_검증한다() throws Exception {
        String accessToken = 토큰을_발급한다(
                "admin@example.com",
                MemberRole.ADMIN
        );

        mockMvc.perform(
                        post("/api/v1/admin/menus")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + accessToken
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "name": "카페라떼",
                                          "category": "INVALID",
                                          "price": 5000
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MENU_CATEGORY"));

        mockMvc.perform(
                        post("/api/v1/admin/menus")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + accessToken
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "name": "카페라떼",
                                          "category": "COFFEE",
                                          "price": 5000,
                                          "status": "INVALID"
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MENU_STATUS"));

        mockMvc.perform(
                        post("/api/v1/admin/menus")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + accessToken
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "name": "카페라떼",
                                          "category": "COFFEE",
                                          "price": 0
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MENU_PRICE"));

        assertThat(menuRepository.count()).isZero();
    }

    @Test
    void 관리자는_전달된_필드만_메뉴를_수정할_수_있다() throws Exception {
        String accessToken = 토큰을_발급한다(
                "admin@example.com",
                MemberRole.ADMIN
        );
        Menu menu = 메뉴를_저장한다(
                "카페라떼",
                "에스프레소와 우유로 만든 커피",
                MenuCategory.COFFEE,
                5000L,
                MenuStatus.ON_SALE
        );

        mockMvc.perform(
                        patch("/api/v1/admin/menus/{menuId}", menu.getId())
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + accessToken
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "name": "바닐라 라떼",
                                          "price": 5500
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("메뉴가 수정되었습니다."))
                .andExpect(jsonPath("$.data.menuId").value(menu.getId()))
                .andExpect(jsonPath("$.data.name").value("바닐라 라떼"))
                .andExpect(jsonPath("$.data.description")
                        .value("에스프레소와 우유로 만든 커피"))
                .andExpect(jsonPath("$.data.category").value("COFFEE"))
                .andExpect(jsonPath("$.data.price").value(5500))
                .andExpect(jsonPath("$.data.status").value("ON_SALE"))
                .andExpect(jsonPath("$.data.updatedAt").isString());

        Menu updatedMenu = menuRepository.findById(menu.getId())
                .orElseThrow();

        assertThat(updatedMenu.getName()).isEqualTo("바닐라 라떼");
        assertThat(updatedMenu.getDescription())
                .isEqualTo("에스프레소와 우유로 만든 커피");
        assertThat(updatedMenu.getPrice()).isEqualTo(5500L);
    }

    @Test
    void 메뉴_수정시_입력값과_가격을_검증한다() throws Exception {
        String accessToken = 토큰을_발급한다(
                "admin@example.com",
                MemberRole.ADMIN
        );
        Menu menu = 메뉴를_저장한다(
                "카페라떼",
                "에스프레소와 우유로 만든 커피",
                MenuCategory.COFFEE,
                5000L,
                MenuStatus.ON_SALE
        );

        mockMvc.perform(
                        patch("/api/v1/admin/menus/{menuId}", menu.getId())
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + accessToken
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "name": " "
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.data.errors[0].field").value("name"));

        mockMvc.perform(
                        patch("/api/v1/admin/menus/{menuId}", menu.getId())
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + accessToken
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "price": -1
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MENU_PRICE"));
    }

    @Test
    void 관리자는_메뉴_상태를_변경할_수_있다() throws Exception {
        String accessToken = 토큰을_발급한다(
                "admin@example.com",
                MemberRole.ADMIN
        );
        Menu menu = 메뉴를_저장한다(
                "카페라떼",
                "에스프레소와 우유로 만든 커피",
                MenuCategory.COFFEE,
                5000L,
                MenuStatus.ON_SALE
        );

        mockMvc.perform(
                        patch("/api/v1/admin/menus/{menuId}/status", menu.getId())
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + accessToken
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "status": "SOLD_OUT"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("메뉴 상태가 변경되었습니다."))
                .andExpect(jsonPath("$.data.menuId").value(menu.getId()))
                .andExpect(jsonPath("$.data.status").value("SOLD_OUT"))
                .andExpect(jsonPath("$.data.updatedAt").isString());

        Menu updatedMenu = menuRepository.findById(menu.getId())
                .orElseThrow();

        assertThat(updatedMenu.getStatus()).isEqualTo(MenuStatus.SOLD_OUT);
    }

    @Test
    void 유효하지_않은_상태로_변경하면_실패한다() throws Exception {
        String accessToken = 토큰을_발급한다(
                "admin@example.com",
                MemberRole.ADMIN
        );
        Menu menu = 메뉴를_저장한다(
                "카페라떼",
                "에스프레소와 우유로 만든 커피",
                MenuCategory.COFFEE,
                5000L,
                MenuStatus.ON_SALE
        );

        mockMvc.perform(
                        patch("/api/v1/admin/menus/{menuId}/status", menu.getId())
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + accessToken
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "status": "INVALID"
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MENU_STATUS"));
    }

    @Test
    void 관리자는_메뉴를_Soft_Delete_할_수_있다() throws Exception {
        String accessToken = 토큰을_발급한다(
                "admin@example.com",
                MemberRole.ADMIN
        );
        Menu menu = 메뉴를_저장한다(
                "카페라떼",
                "에스프레소와 우유로 만든 커피",
                MenuCategory.COFFEE,
                5000L,
                MenuStatus.ON_SALE
        );

        mockMvc.perform(
                        delete("/api/v1/admin/menus/{menuId}", menu.getId())
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + accessToken
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("메뉴가 삭제되었습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());

        Menu deletedMenu = menuRepository.findById(menu.getId())
                .orElseThrow();

        assertThat(deletedMenu.isDeleted()).isTrue();
        assertThat(deletedMenu.getDeletedAt()).isNotNull();

        mockMvc.perform(get("/api/v1/menus/{menuId}", menu.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MENU_NOT_FOUND"));
    }

    @Test
    void 삭제된_메뉴는_재수정_상태변경_재삭제할_수_없다() throws Exception {
        String accessToken = 토큰을_발급한다(
                "admin@example.com",
                MemberRole.ADMIN
        );
        Menu menu = 메뉴를_저장한다(
                "카페라떼",
                "에스프레소와 우유로 만든 커피",
                MenuCategory.COFFEE,
                5000L,
                MenuStatus.ON_SALE
        );
        menu.delete(LocalDateTime.now());
        menuRepository.saveAndFlush(menu);

        mockMvc.perform(
                        patch("/api/v1/admin/menus/{menuId}", menu.getId())
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + accessToken
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "name": "바닐라 라떼"
                                        }
                                        """)
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MENU_ALREADY_DELETED"));

        mockMvc.perform(
                        patch("/api/v1/admin/menus/{menuId}/status", menu.getId())
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + accessToken
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "status": "SOLD_OUT"
                                        }
                                        """)
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MENU_ALREADY_DELETED"));

        mockMvc.perform(
                        delete("/api/v1/admin/menus/{menuId}", menu.getId())
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + accessToken
                                )
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MENU_ALREADY_DELETED"));
    }

    @Test
    void 존재하지_않는_메뉴는_관리자_수정과_삭제에_실패한다() throws Exception {
        String accessToken = 토큰을_발급한다(
                "admin@example.com",
                MemberRole.ADMIN
        );

        mockMvc.perform(
                        patch("/api/v1/admin/menus/{menuId}", 999L)
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + accessToken
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "name": "바닐라 라떼"
                                        }
                                        """)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MENU_NOT_FOUND"));

        mockMvc.perform(
                        delete("/api/v1/admin/menus/{menuId}", 999L)
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + accessToken
                                )
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MENU_NOT_FOUND"));
    }

    private String 토큰을_발급한다(
            String email,
            MemberRole role
    ) {
        Member member = memberRepository.saveAndFlush(Member.create(
                email,
                "encrypted-password",
                role.name()
        ));
        ReflectionTestUtils.setField(
                member,
                "role",
                role
        );
        memberRepository.saveAndFlush(member);

        return jwtTokenProvider.createLoginTokens(member)
                .accessToken();
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

    @TestConfiguration
    static class TestTokenStoreConfig {

        @Bean
        @Primary
        TokenStore tokenStore() {
            return new InMemoryTokenStore();
        }
    }

    static class InMemoryTokenStore implements TokenStore {

        private final Map<Long, String> refreshTokens =
                new ConcurrentHashMap<>();
        private final Set<String> blacklistedAccessTokens =
                ConcurrentHashMap.newKeySet();

        @Override
        public void saveRefreshToken(
                Long memberId,
                String refreshToken,
                long ttlSeconds
        ) {
            if (ttlSeconds > 0) {
                refreshTokens.put(
                        memberId,
                        refreshToken
                );
            }
        }

        @Override
        public boolean matchesRefreshToken(
                Long memberId,
                String refreshToken
        ) {
            return refreshToken.equals(refreshTokens.get(memberId));
        }

        @Override
        public boolean rotateRefreshToken(
                Long memberId,
                String currentRefreshToken,
                String newRefreshToken,
                long ttlSeconds
        ) {
            if (ttlSeconds <= 0) {
                return false;
            }

            if (!currentRefreshToken.equals(refreshTokens.get(memberId))) {
                return false;
            }

            refreshTokens.put(
                    memberId,
                    newRefreshToken
            );

            return true;
        }

        @Override
        public void deleteRefreshToken(Long memberId) {
            refreshTokens.remove(memberId);
        }

        @Override
        public void logoutTokens(
                Long memberId,
                String accessToken,
                long accessTokenTtlSeconds
        ) {
            if (accessTokenTtlSeconds > 0) {
                blacklistedAccessTokens.add(accessToken);
            }

            refreshTokens.remove(memberId);
        }

        @Override
        public boolean isAccessTokenBlacklisted(String accessToken) {
            return blacklistedAccessTokens.contains(accessToken);
        }

        void clear() {
            refreshTokens.clear();
            blacklistedAccessTokens.clear();
        }
    }
}
