package com.example.coffeeorder.point.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static com.example.coffeeorder.testsupport.IntegrationTestFixtures.member;
import static com.example.coffeeorder.testsupport.IntegrationTestFixtures.point;
import static com.example.coffeeorder.testsupport.TestAuthTokens.accessToken;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.example.coffeeorder.common.security.JwtTokenProvider;
import com.example.coffeeorder.common.security.TokenStore;
import com.example.coffeeorder.testsupport.InMemoryTokenStore;
import com.example.coffeeorder.testsupport.TestTokenStoreConfig;
import com.example.coffeeorder.member.entity.Member;
import com.example.coffeeorder.member.repository.MemberRepository;
import com.example.coffeeorder.point.entity.Point;
import com.example.coffeeorder.point.entity.PointHistory;
import com.example.coffeeorder.point.entity.PointHistoryType;
import com.example.coffeeorder.point.repository.PointHistoryRepository;
import com.example.coffeeorder.point.repository.PointRepository;
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
class PointControllerIntegrationTest {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PointRepository pointRepository;

    @Autowired
    private PointHistoryRepository pointHistoryRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private TokenStore tokenStore;

    @BeforeEach
    void setUp() {
        pointHistoryRepository.deleteAll();
        pointRepository.deleteAll();
        memberRepository.deleteAll();

        if (tokenStore instanceof InMemoryTokenStore inMemoryTokenStore) {
            inMemoryTokenStore.clear();
        }
    }

    @Test
    void 인증되지_않은_사용자는_포인트_잔액을_조회할_수_없다() throws Exception {
        mockMvc.perform(get("/api/v1/points"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 인증된_사용자는_자신의_포인트_잔액을_조회할_수_있다() throws Exception {
        TestUser user = 회원과_포인트와_토큰을_생성한다("user@example.com");
        Point point = user.point();
        point.charge(15_000L);
        pointRepository.saveAndFlush(point);

        mockMvc.perform(
                        get("/api/v1/points")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + user.accessToken()
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message")
                        .value("포인트 잔액 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.memberId")
                        .value(user.member().getId()))
                .andExpect(jsonPath("$.data.balance").value(15_000))
                .andExpect(jsonPath("$.data.updatedAt").isString());
    }

    @Test
    void 포인트를_충전하면_잔액과_충전_이력이_같은_트랜잭션으로_저장된다() throws Exception {
        TestUser user = 회원과_포인트와_토큰을_생성한다("user@example.com");
        Point point = user.point();
        point.charge(5_000L);
        pointRepository.saveAndFlush(point);

        mockMvc.perform(
                        post("/api/v1/points/charge")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + user.accessToken()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "amount": 10000
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("포인트가 충전되었습니다."))
                .andExpect(jsonPath("$.data.historyId").isNumber())
                .andExpect(jsonPath("$.data.chargedAmount").value(10_000))
                .andExpect(jsonPath("$.data.balanceBefore").value(5_000))
                .andExpect(jsonPath("$.data.balanceAfter").value(15_000))
                .andExpect(jsonPath("$.data.chargedAt").isString());

        Point chargedPoint = pointRepository.findByMemberId(user.member().getId())
                .orElseThrow();
        List<PointHistory> histories = pointHistoryRepository.findAll();

        assertThat(chargedPoint.getBalance()).isEqualTo(15_000L);
        assertThat(histories).hasSize(1);
        assertThat(histories.getFirst().getMemberId())
                .isEqualTo(user.member().getId());
        assertThat(histories.getFirst().getType())
                .isEqualTo(PointHistoryType.CHARGE);
        assertThat(histories.getFirst().getAmount()).isEqualTo(10_000L);
        assertThat(histories.getFirst().getBalanceAfter()).isEqualTo(15_000L);
    }

    @Test
    void 충전_금액이_0_이하이면_잔액과_이력이_변경되지_않는다() throws Exception {
        TestUser user = 회원과_포인트와_토큰을_생성한다("user@example.com");

        mockMvc.perform(
                        post("/api/v1/points/charge")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + user.accessToken()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "amount": 0
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_POINT_AMOUNT"));

        Point point = pointRepository.findByMemberId(user.member().getId())
                .orElseThrow();

        assertThat(point.getBalance()).isZero();
        assertThat(pointHistoryRepository.count()).isZero();
    }

    @Test
    void 충전_금액이_누락되면_검증_오류를_반환한다() throws Exception {
        TestUser user = 회원과_포인트와_토큰을_생성한다("user@example.com");

        mockMvc.perform(
                        post("/api/v1/points/charge")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + user.accessToken()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.data.errors[0].field").value("amount"))
                .andExpect(jsonPath("$.data.errors[0].message")
                        .value("충전 금액은 필수입니다."));
    }

    @Test
    void 포인트_계정이_없으면_조회와_충전에_실패한다() throws Exception {
        TestUser user = 회원과_토큰만_생성한다("user@example.com");

        mockMvc.perform(
                        get("/api/v1/points")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + user.accessToken()
                                )
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POINT_ACCOUNT_NOT_FOUND"));

        mockMvc.perform(
                        post("/api/v1/points/charge")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + user.accessToken()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "amount": 1000
                                        }
                                        """)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POINT_ACCOUNT_NOT_FOUND"));
    }

    @Test
    void 포인트_이력은_인증된_사용자의_이력만_조회한다() throws Exception {
        TestUser user = 회원과_포인트와_토큰을_생성한다("user@example.com");
        TestUser other = 회원과_포인트와_토큰을_생성한다("other@example.com");

        포인트를_충전한다(
                user.accessToken(),
                1_000L
        );
        포인트를_충전한다(
                user.accessToken(),
                2_000L
        );
        포인트를_충전한다(
                other.accessToken(),
                3_000L
        );

        mockMvc.perform(
                        get("/api/v1/points/histories")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + user.accessToken()
                                )
                                .param(
                                        "type",
                                        "CHARGE"
                                )
                                .param(
                                        "sort",
                                        "historyId,asc"
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message")
                        .value("포인트 이력 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].type").value("CHARGE"))
                .andExpect(jsonPath("$.data.content[0].amount").value(1_000))
                .andExpect(jsonPath("$.data.content[0].balanceAfter").value(1_000))
                .andExpect(jsonPath("$.data.content[0].orderId").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].createdAt").isString())
                .andExpect(jsonPath("$.data.content[1].amount").value(2_000))
                .andExpect(jsonPath("$.data.content[1].balanceAfter").value(3_000))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(1));
    }

    @Test
    void 유효하지_않은_포인트_이력_유형이면_실패한다() throws Exception {
        TestUser user = 회원과_포인트와_토큰을_생성한다("user@example.com");

        mockMvc.perform(
                        get("/api/v1/points/histories")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + user.accessToken()
                                )
                                .param(
                                        "type",
                                        "INVALID"
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_POINT_HISTORY_TYPE"));
    }

    @Test
    void 동시_충전_요청에서도_최종_잔액과_이력이_정확하다() throws Exception {
        TestUser user = 회원과_포인트와_토큰을_생성한다("user@example.com");
        int requestCount = 5;
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(requestCount);

        try {
            List<Future<Integer>> futures = new ArrayList<>();

            for (int i = 0; i < requestCount; i++) {
                futures.add(executorService.submit(() -> 충전_상태코드를_반환한다(
                        user.accessToken(),
                        1_000L,
                        ready,
                        start
                )));
            }

            assertThat(ready.await(
                    1,
                    TimeUnit.SECONDS
            )).isTrue();
            start.countDown();

            List<Integer> statusCodes = new ArrayList<>();

            for (Future<Integer> future : futures) {
                statusCodes.add(future.get(
                        10,
                        TimeUnit.SECONDS
                ));
            }

            assertThat(statusCodes).containsOnly(200);
        } finally {
            executorService.shutdownNow();
        }

        Point point = pointRepository.findByMemberId(user.member().getId())
                .orElseThrow();

        assertThat(point.getBalance()).isEqualTo(5_000L);
        assertThat(pointHistoryRepository.findAll())
                .hasSize(5)
                .allSatisfy(history -> {
                    assertThat(history.getMemberId())
                            .isEqualTo(user.member().getId());
                    assertThat(history.getType())
                            .isEqualTo(PointHistoryType.CHARGE);
                    assertThat(history.getAmount()).isEqualTo(1_000L);
                });
    }

    private void 포인트를_충전한다(
            String accessToken,
            long amount
    ) throws Exception {
        mockMvc.perform(
                        post("/api/v1/points/charge")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + accessToken
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "amount": %d
                                        }
                                        """.formatted(amount))
                )
                .andExpect(status().isOk());
    }

    private int 충전_상태코드를_반환한다(
            String accessToken,
            long amount,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        start.await(
                1,
                TimeUnit.SECONDS
        );

        return mockMvc.perform(
                        post("/api/v1/points/charge")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + accessToken
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "amount": %d
                                        }
                                        """.formatted(amount))
                )
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private TestUser 회원과_포인트와_토큰을_생성한다(String email) {
        Member member = memberRepository.saveAndFlush(member(email));
        Point point = pointRepository.saveAndFlush(point(member));
        String accessToken = accessToken(
                jwtTokenProvider,
                member
        );

        return new TestUser(
                member,
                point,
                accessToken
        );
    }

    private TestUser 회원과_토큰만_생성한다(String email) {
        Member member = memberRepository.saveAndFlush(member(email));
        String accessToken = accessToken(
                jwtTokenProvider,
                member
        );

        return new TestUser(
                member,
                null,
                accessToken
        );
    }

    private record TestUser(
            Member member,
            Point point,
            String accessToken
    ) {
    }
}
