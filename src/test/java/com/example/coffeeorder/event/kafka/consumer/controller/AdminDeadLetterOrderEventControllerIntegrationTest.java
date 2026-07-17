package com.example.coffeeorder.event.kafka.consumer.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.example.coffeeorder.common.security.JwtTokenProvider;
import com.example.coffeeorder.common.security.TokenStore;
import com.example.coffeeorder.event.kafka.consumer.entity.DeadLetterOrderEvent;
import com.example.coffeeorder.event.kafka.consumer.repository.DeadLetterOrderEventRepository;
import com.example.coffeeorder.member.entity.Member;
import com.example.coffeeorder.member.entity.MemberRole;
import com.example.coffeeorder.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import(AdminDeadLetterOrderEventControllerIntegrationTest.TestTokenStoreConfig.class)
class AdminDeadLetterOrderEventControllerIntegrationTest {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ORIGINAL_TOPIC = "order.completed";
    private static final String DEAD_LETTER_TOPIC = "order.completed.DLT";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private DeadLetterOrderEventRepository deadLetterOrderEventRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private TokenStore tokenStore;

    @BeforeEach
    void setUp() {
        deadLetterOrderEventRepository.deleteAll();
        memberRepository.deleteAll();

        if (tokenStore instanceof InMemoryTokenStore inMemoryTokenStore) {
            inMemoryTokenStore.clear();
        }
    }

    @Test
    void 관리자는_Dead_Letter_주문_이벤트_목록을_페이징_정렬해_조회할_수_있다() throws Exception {
        TestMember admin = 회원과_토큰을_발급한다(
                "admin@example.com",
                "관리자",
                MemberRole.ADMIN
        );
        DeadLetterOrderEvent secondEvent = Dead_Letter_이벤트를_저장한다(
                20L,
                "External API 500",
                LocalDateTime.of(
                        2026,
                        7,
                        17,
                        10,
                        0
                )
        );
        DeadLetterOrderEvent firstEvent = Dead_Letter_이벤트를_저장한다(
                10L,
                "External API timeout",
                LocalDateTime.of(
                        2026,
                        7,
                        17,
                        10,
                        1
                )
        );

        mockMvc.perform(
                        get("/api/v1/admin/dead-letter-order-events")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + admin.accessToken()
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
                                        "kafkaOffset,asc"
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("Dead Letter 주문 이벤트 목록 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.content[0].id").value(firstEvent.getId()))
                .andExpect(jsonPath("$.data.content[0].eventId").value(firstEvent.getEventId()))
                .andExpect(jsonPath("$.data.content[0].topic").value(DEAD_LETTER_TOPIC))
                .andExpect(jsonPath("$.data.content[0].originalTopic").value(ORIGINAL_TOPIC))
                .andExpect(jsonPath("$.data.content[0].kafkaPartition").value(1))
                .andExpect(jsonPath("$.data.content[0].kafkaOffset").value(10L))
                .andExpect(jsonPath("$.data.content[0].exceptionMessage").value("External API timeout"))
                .andExpect(jsonPath("$.data.content[0].payload").value(firstEvent.getPayload()))
                .andExpect(jsonPath("$.data.content[0].receivedAt").value("2026-07-17T10:01:00"))
                .andExpect(jsonPath("$.data.content[0].createdAt").exists());

        mockMvc.perform(
                        get("/api/v1/admin/dead-letter-order-events")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + admin.accessToken()
                                )
                                .param(
                                        "page",
                                        "1"
                                )
                                .param(
                                        "size",
                                        "1"
                                )
                                .param(
                                        "sort",
                                        "kafkaOffset,asc"
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(secondEvent.getId()))
                .andExpect(jsonPath("$.data.content[0].kafkaOffset").value(20L));
    }

    @Test
    void 관리자는_Dead_Letter_주문_이벤트_상세에서_원본_payload를_확인할_수_있다() throws Exception {
        TestMember admin = 회원과_토큰을_발급한다(
                "detail-admin@example.com",
                "관리자",
                MemberRole.ADMIN
        );
        DeadLetterOrderEvent event = Dead_Letter_이벤트를_저장한다(
                30L,
                "External API 500",
                LocalDateTime.of(
                        2026,
                        7,
                        17,
                        11,
                        0
                )
        );

        mockMvc.perform(
                        get(
                                "/api/v1/admin/dead-letter-order-events/{deadLetterEventId}",
                                event.getId()
                        )
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + admin.accessToken()
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("Dead Letter 주문 이벤트 상세 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.id").value(event.getId()))
                .andExpect(jsonPath("$.data.eventId").value(event.getEventId()))
                .andExpect(jsonPath("$.data.topic").value(DEAD_LETTER_TOPIC))
                .andExpect(jsonPath("$.data.originalTopic").value(ORIGINAL_TOPIC))
                .andExpect(jsonPath("$.data.kafkaPartition").value(1))
                .andExpect(jsonPath("$.data.kafkaOffset").value(30L))
                .andExpect(jsonPath("$.data.exceptionMessage").value("External API 500"))
                .andExpect(jsonPath("$.data.payload").value(event.getPayload()))
                .andExpect(jsonPath("$.data.receivedAt").value("2026-07-17T11:00:00"))
                .andExpect(jsonPath("$.data.createdAt").exists());
    }

    @Test
    void 존재하지_않는_Dead_Letter_주문_이벤트_상세_조회는_실패한다() throws Exception {
        TestMember admin = 회원과_토큰을_발급한다(
                "not-found-admin@example.com",
                "관리자",
                MemberRole.ADMIN
        );

        mockMvc.perform(
                        get(
                                "/api/v1/admin/dead-letter-order-events/{deadLetterEventId}",
                                999_999L
                        )
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + admin.accessToken()
                                )
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("DEAD_LETTER_ORDER_EVENT_NOT_FOUND"));
    }

    @Test
    void 일반_사용자는_Dead_Letter_주문_이벤트를_조회할_수_없다() throws Exception {
        TestMember member = 회원과_토큰을_발급한다(
                "member@example.com",
                "일반 회원",
                MemberRole.USER
        );

        mockMvc.perform(
                        get("/api/v1/admin/dead-letter-order-events")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + member.accessToken()
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void 인증되지_않은_사용자는_Dead_Letter_주문_이벤트를_조회할_수_없다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dead-letter-order-events"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void Dead_Letter_주문_이벤트_조회_파라미터가_올바르지_않으면_오류를_반환한다() throws Exception {
        TestMember admin = 회원과_토큰을_발급한다(
                "invalid-admin@example.com",
                "관리자",
                MemberRole.ADMIN
        );

        mockMvc.perform(
                        get("/api/v1/admin/dead-letter-order-events")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + admin.accessToken()
                                )
                                .param(
                                        "page",
                                        "-1"
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_PAGE_REQUEST"));

        mockMvc.perform(
                        get("/api/v1/admin/dead-letter-order-events")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + admin.accessToken()
                                )
                                .param(
                                        "sort",
                                        "payload,desc"
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_PAGE_REQUEST"));
    }

    private TestMember 회원과_토큰을_발급한다(
            String email,
            String name,
            MemberRole role
    ) {
        Member member = memberRepository.saveAndFlush(Member.create(
                email,
                "encrypted-password",
                name
        ));
        ReflectionTestUtils.setField(
                member,
                "role",
                role
        );
        memberRepository.saveAndFlush(member);

        return new TestMember(
                member,
                jwtTokenProvider.createLoginTokens(member)
                        .accessToken()
        );
    }

    private DeadLetterOrderEvent Dead_Letter_이벤트를_저장한다(
            Long kafkaOffset,
            String exceptionMessage,
            LocalDateTime receivedAt
    ) {
        String eventId = UUID.randomUUID()
                .toString();

        return deadLetterOrderEventRepository.saveAndFlush(
                DeadLetterOrderEvent.of(
                        eventId,
                        ORIGINAL_TOPIC,
                        DEAD_LETTER_TOPIC,
                        1,
                        kafkaOffset,
                        """
                                {"eventId":"%s","eventType":"ORDER_COMPLETED"}
                                """.formatted(eventId),
                        exceptionMessage,
                        receivedAt
                )
        );
    }

    private record TestMember(
            Member member,
            String accessToken
    ) {
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
