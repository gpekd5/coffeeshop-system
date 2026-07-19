package com.example.coffeeorder.cart.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static com.example.coffeeorder.testsupport.IntegrationTestFixtures.cart;
import static com.example.coffeeorder.testsupport.IntegrationTestFixtures.cartItem;
import static com.example.coffeeorder.testsupport.IntegrationTestFixtures.member;
import static com.example.coffeeorder.testsupport.IntegrationTestFixtures.menu;
import static com.example.coffeeorder.testsupport.TestAuthTokens.accessToken;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import com.example.coffeeorder.cart.entity.Cart;
import com.example.coffeeorder.cart.entity.CartItem;
import com.example.coffeeorder.cart.repository.CartItemRepository;
import com.example.coffeeorder.cart.repository.CartRepository;
import com.example.coffeeorder.common.security.JwtTokenProvider;
import com.example.coffeeorder.common.security.TokenStore;
import com.example.coffeeorder.testsupport.InMemoryTokenStore;
import com.example.coffeeorder.testsupport.TestTokenStoreConfig;
import com.example.coffeeorder.member.entity.Member;
import com.example.coffeeorder.member.repository.MemberRepository;
import com.example.coffeeorder.menu.entity.Menu;
import com.example.coffeeorder.menu.entity.MenuCategory;
import com.example.coffeeorder.menu.entity.MenuStatus;
import com.example.coffeeorder.menu.repository.MenuRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import(TestTokenStoreConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CartControllerIntegrationTest {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MenuRepository menuRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private TokenStore tokenStore;

    @BeforeEach
    void setUp() {
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        menuRepository.deleteAll();
        memberRepository.deleteAll();

        if (tokenStore instanceof InMemoryTokenStore inMemoryTokenStore) {
            inMemoryTokenStore.clear();
        }
    }

    @Test
    void 인증되지_않은_사용자는_장바구니를_조회할_수_없다() throws Exception {
        mockMvc.perform(get("/api/v1/cart"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("인증되지 않은 사용자입니다."));
    }

    @Test
    void 장바구니가_없으면_빈_장바구니를_생성해_반환한다() throws Exception {
        TestUser user = 회원과_토큰을_생성한다("user@example.com");

        mockMvc.perform(
                        get("/api/v1/cart")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + user.accessToken()
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("장바구니 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.cartId").isNumber())
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.expectedTotalAmount").value(0))
                .andExpect(jsonPath("$.data.updatedAt").isString());

        Cart cart = cartRepository.findByMember_Id(user.member().getId())
                .orElseThrow();

        assertThat(cart.getMemberId()).isEqualTo(user.member().getId());
    }

    @Test
    void 판매중인_메뉴를_장바구니에_추가할_수_있다() throws Exception {
        TestUser user = 회원과_토큰을_생성한다("user@example.com");
        Menu menu = 메뉴를_저장한다(
                "아메리카노",
                MenuCategory.COFFEE,
                4500L,
                MenuStatus.ON_SALE
        );

        mockMvc.perform(
                        post("/api/v1/cart/items")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + user.accessToken()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "menuId": %d,
                                          "quantity": 2
                                        }
                                        """.formatted(menu.getId()))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("메뉴가 장바구니에 추가되었습니다."))
                .andExpect(jsonPath("$.data.cartItemId").isNumber())
                .andExpect(jsonPath("$.data.menuId").value(menu.getId()))
                .andExpect(jsonPath("$.data.menuName").value("아메리카노"))
                .andExpect(jsonPath("$.data.unitPrice").value(4500))
                .andExpect(jsonPath("$.data.quantity").value(2))
                .andExpect(jsonPath("$.data.lineAmount").value(9000));

        Cart cart = cartRepository.findByMember_Id(user.member().getId())
                .orElseThrow();
        CartItem cartItem = cartItemRepository.findAllByCart_IdOrderByIdAsc(
                        cart.getId()
                )
                .getFirst();

        assertThat(cartItem.getMenu().getId()).isEqualTo(menu.getId());
        assertThat(cartItem.getQuantity()).isEqualTo(2);
    }

    @Test
    void 동일_메뉴를_추가하면_기존_항목_수량이_증가한다() throws Exception {
        TestUser user = 회원과_토큰을_생성한다("user@example.com");
        Menu menu = 메뉴를_저장한다(
                "아메리카노",
                MenuCategory.COFFEE,
                4500L,
                MenuStatus.ON_SALE
        );

        메뉴_추가를_요청한다(
                user.accessToken(),
                menu.getId(),
                2
        );

        mockMvc.perform(
                        post("/api/v1/cart/items")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + user.accessToken()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "menuId": %d,
                                          "quantity": 3
                                        }
                                        """.formatted(menu.getId()))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantity").value(5))
                .andExpect(jsonPath("$.data.lineAmount").value(22500));

        Cart cart = cartRepository.findByMember_Id(user.member().getId())
                .orElseThrow();

        assertThat(cartItemRepository.findAllByCart_IdOrderByIdAsc(cart.getId()))
                .hasSize(1)
                .first()
                .extracting(CartItem::getQuantity)
                .isEqualTo(5);
    }

    @Test
    void 장바구니_조회는_현재_메뉴_가격과_상태를_표시한다() throws Exception {
        TestUser user = 회원과_토큰을_생성한다("user@example.com");
        Menu menu = 메뉴를_저장한다(
                "아메리카노",
                MenuCategory.COFFEE,
                4500L,
                MenuStatus.ON_SALE
        );
        메뉴_추가를_요청한다(
                user.accessToken(),
                menu.getId(),
                2
        );
        menu.update(
                null,
                null,
                null,
                5000L
        );
        menu.changeStatus(MenuStatus.SOLD_OUT);
        menuRepository.saveAndFlush(menu);

        mockMvc.perform(
                        get("/api/v1/cart")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + user.accessToken()
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].menuId").value(menu.getId()))
                .andExpect(jsonPath("$.data.items[0].unitPrice").value(5000))
                .andExpect(jsonPath("$.data.items[0].quantity").value(2))
                .andExpect(jsonPath("$.data.items[0].lineAmount").value(10000))
                .andExpect(jsonPath("$.data.items[0].menuStatus").value("SOLD_OUT"))
                .andExpect(jsonPath("$.data.expectedTotalAmount").value(10000));
    }

    @Test
    void 판매중이_아닌_메뉴는_장바구니에_추가할_수_없다() throws Exception {
        TestUser user = 회원과_토큰을_생성한다("user@example.com");
        Menu soldOutMenu = 메뉴를_저장한다(
                "카페라떼",
                MenuCategory.COFFEE,
                5000L,
                MenuStatus.SOLD_OUT
        );

        mockMvc.perform(
                        post("/api/v1/cart/items")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + user.accessToken()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "menuId": %d,
                                          "quantity": 1
                                        }
                                        """.formatted(soldOutMenu.getId()))
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("MENU_NOT_ON_SALE"))
                .andExpect(jsonPath("$.message").value("판매 중인 메뉴가 아닙니다."));
    }

    @Test
    void 삭제된_메뉴는_장바구니에_추가할_수_없다() throws Exception {
        TestUser user = 회원과_토큰을_생성한다("user@example.com");
        Menu deletedMenu = 메뉴를_저장한다(
                "삭제된 메뉴",
                MenuCategory.ETC,
                1000L,
                MenuStatus.ON_SALE
        );
        deletedMenu.delete(LocalDateTime.now());
        menuRepository.saveAndFlush(deletedMenu);

        mockMvc.perform(
                        post("/api/v1/cart/items")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + user.accessToken()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "menuId": %d,
                                          "quantity": 1
                                        }
                                        """.formatted(deletedMenu.getId()))
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MENU_NOT_FOUND"));
    }

    @Test
    void 수량은_1_이상이어야_한다() throws Exception {
        TestUser user = 회원과_토큰을_생성한다("user@example.com");
        Menu menu = 메뉴를_저장한다(
                "아메리카노",
                MenuCategory.COFFEE,
                4500L,
                MenuStatus.ON_SALE
        );

        mockMvc.perform(
                        post("/api/v1/cart/items")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + user.accessToken()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "menuId": %d,
                                          "quantity": 0
                                        }
                                        """.formatted(menu.getId()))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_QUANTITY"));
    }

    @Test
    void 장바구니_항목_수량을_변경할_수_있다() throws Exception {
        TestUser user = 회원과_토큰을_생성한다("user@example.com");
        Menu menu = 메뉴를_저장한다(
                "아메리카노",
                MenuCategory.COFFEE,
                4500L,
                MenuStatus.ON_SALE
        );
        CartItem cartItem = 장바구니_항목을_저장한다(
                user.member(),
                menu,
                2
        );

        mockMvc.perform(
                        patch("/api/v1/cart/items/{cartItemId}", cartItem.getId())
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + user.accessToken()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "quantity": 3
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("장바구니 수량이 변경되었습니다."))
                .andExpect(jsonPath("$.data.cartItemId").value(cartItem.getId()))
                .andExpect(jsonPath("$.data.quantity").value(3))
                .andExpect(jsonPath("$.data.lineAmount").value(13500));

        assertThat(cartItemRepository.findById(cartItem.getId())
                .orElseThrow()
                .getQuantity()).isEqualTo(3);
    }

    @Test
    void 다른_회원의_장바구니_항목은_수정하거나_삭제할_수_없다() throws Exception {
        TestUser owner = 회원과_토큰을_생성한다("owner@example.com");
        TestUser other = 회원과_토큰을_생성한다("other@example.com");
        Menu menu = 메뉴를_저장한다(
                "아메리카노",
                MenuCategory.COFFEE,
                4500L,
                MenuStatus.ON_SALE
        );
        CartItem ownerItem = 장바구니_항목을_저장한다(
                owner.member(),
                menu,
                1
        );

        mockMvc.perform(
                        patch("/api/v1/cart/items/{cartItemId}", ownerItem.getId())
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + other.accessToken()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "quantity": 2
                                        }
                                        """)
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CART_ITEM_FORBIDDEN"));

        mockMvc.perform(
                        delete("/api/v1/cart/items/{cartItemId}", ownerItem.getId())
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + other.accessToken()
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CART_ITEM_FORBIDDEN"));
    }

    @Test
    void 존재하지_않는_장바구니_항목은_수정하거나_삭제할_수_없다() throws Exception {
        TestUser user = 회원과_토큰을_생성한다("user@example.com");

        mockMvc.perform(
                        patch("/api/v1/cart/items/{cartItemId}", 999L)
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + user.accessToken()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "quantity": 2
                                        }
                                        """)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CART_ITEM_NOT_FOUND"));

        mockMvc.perform(
                        delete("/api/v1/cart/items/{cartItemId}", 999L)
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + user.accessToken()
                                )
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CART_ITEM_NOT_FOUND"));
    }

    @Test
    void 자신의_장바구니_항목을_Hard_Delete_할_수_있다() throws Exception {
        TestUser user = 회원과_토큰을_생성한다("user@example.com");
        Menu menu = 메뉴를_저장한다(
                "아메리카노",
                MenuCategory.COFFEE,
                4500L,
                MenuStatus.ON_SALE
        );
        CartItem cartItem = 장바구니_항목을_저장한다(
                user.member(),
                menu,
                1
        );

        mockMvc.perform(
                        delete("/api/v1/cart/items/{cartItemId}", cartItem.getId())
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + user.accessToken()
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("장바구니 항목이 삭제되었습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());

        assertThat(cartItemRepository.findById(cartItem.getId())).isEmpty();
    }

    @Test
    void 장바구니_전체_비우기는_자신의_항목만_삭제한다() throws Exception {
        TestUser user = 회원과_토큰을_생성한다("user@example.com");
        TestUser other = 회원과_토큰을_생성한다("other@example.com");
        Menu americano = 메뉴를_저장한다(
                "아메리카노",
                MenuCategory.COFFEE,
                4500L,
                MenuStatus.ON_SALE
        );
        Menu latte = 메뉴를_저장한다(
                "카페라떼",
                MenuCategory.COFFEE,
                5000L,
                MenuStatus.ON_SALE
        );
        CartItem userItem = 장바구니_항목을_저장한다(
                user.member(),
                americano,
                1
        );
        CartItem otherItem = 장바구니_항목을_저장한다(
                other.member(),
                latte,
                1
        );

        mockMvc.perform(
                        delete("/api/v1/cart/items")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + user.accessToken()
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("장바구니를 비웠습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());

        assertThat(cartItemRepository.findById(userItem.getId())).isEmpty();
        assertThat(cartItemRepository.findById(otherItem.getId())).isPresent();
    }

    private void 메뉴_추가를_요청한다(
            String accessToken,
            Long menuId,
            int quantity
    ) throws Exception {
        mockMvc.perform(
                        post("/api/v1/cart/items")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + accessToken
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "menuId": %d,
                                          "quantity": %d
                                        }
                                        """.formatted(
                                        menuId,
                                        quantity
                                ))
                )
                .andExpect(status().isOk());
    }

    private TestUser 회원과_토큰을_생성한다(String email) {
        Member member = memberRepository.saveAndFlush(member(email));
        String accessToken = accessToken(
                jwtTokenProvider,
                member
        );

        return new TestUser(
                member,
                accessToken
        );
    }

    private Menu 메뉴를_저장한다(
            String name,
            MenuCategory category,
            long price,
            MenuStatus status
    ) {
        return menuRepository.saveAndFlush(menu(
                name,
                category,
                price,
                status
        ));
    }

    private CartItem 장바구니_항목을_저장한다(
            Member member,
            Menu menu,
            int quantity
    ) {
        Cart cart = cartRepository.findByMember_Id(member.getId())
                .orElseGet(() -> cartRepository.saveAndFlush(cart(member)));

        return cartItemRepository.saveAndFlush(cartItem(
                cart,
                menu,
                quantity
        ));
    }

    private record TestUser(
            Member member,
            String accessToken
    ) {
    }
}
