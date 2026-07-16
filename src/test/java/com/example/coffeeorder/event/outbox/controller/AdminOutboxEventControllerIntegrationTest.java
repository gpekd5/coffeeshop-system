package com.example.coffeeorder.event.outbox.controller;

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
import com.example.coffeeorder.event.outbox.entity.OutboxEvent;
import com.example.coffeeorder.event.outbox.repository.OutboxEventRepository;
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
@Import(AdminOutboxEventControllerIntegrationTest.TestTokenStoreConfig.class)
class AdminOutboxEventControllerIntegrationTest {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private TokenStore tokenStore;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
        memberRepository.deleteAll();

        if (tokenStore instanceof InMemoryTokenStore inMemoryTokenStore) {
            inMemoryTokenStore.clear();
        }
    }

    @Test
    void 관리자는_Outbox_이벤트를_상태별로_페이징_조회할_수_있다() throws Exception {
        TestMember admin = 회원과_토큰을_발급한다(
                "admin@example.com",
                "관리자",
                MemberRole.ADMIN
        );
        이벤트를_저장한다(1L);
        OutboxEvent failedEvent = 이벤트를_저장한다(2L);
        failedEvent.markPublishFailed(
                "Kafka unavailable",
                LocalDateTime.of(
                        2026,
                        7,
                        17,
                        10,
                        0
                )
        );
        outboxEventRepository.saveAndFlush(failedEvent);
        OutboxEvent publishedEvent = 이벤트를_저장한다(3L);
        publishedEvent.markPublished(LocalDateTime.of(
                2026,
                7,
                17,
                11,
                0
        ));
        outboxEventRepository.saveAndFlush(publishedEvent);

        mockMvc.perform(
                        get("/api/v1/admin/outbox-events")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + admin.accessToken()
                                )
                                .param(
                                        "status",
                                        "FAILED"
                                )
                                .param(
                                        "page",
                                        "0"
                                )
                                .param(
                                        "size",
                                        "10"
                                )
                                .param(
                                        "sort",
                                        "aggregateId,desc"
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("Outbox 이벤트 목록 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.content[0].eventId").value(failedEvent.getId()))
                .andExpect(jsonPath("$.data.content[0].aggregateType").value("ORDER"))
                .andExpect(jsonPath("$.data.content[0].aggregateId").value(2L))
                .andExpect(jsonPath("$.data.content[0].eventType").value("ORDER_COMPLETED"))
                .andExpect(jsonPath("$.data.content[0].status").value("FAILED"))
                .andExpect(jsonPath("$.data.content[0].retryCount").value(1))
                .andExpect(jsonPath("$.data.content[0].nextRetryAt").value("2026-07-17T10:00:00"))
                .andExpect(jsonPath("$.data.content[0].lastError").value("Kafka unavailable"))
                .andExpect(jsonPath("$.data.content[0].publishedAt").isEmpty())
                .andExpect(jsonPath("$.data.content[0].createdAt").exists());
    }

    @Test
    void 관리자는_Outbox_이벤트_전체를_정렬해_조회할_수_있다() throws Exception {
        TestMember admin = 회원과_토큰을_발급한다(
                "admin-all@example.com",
                "관리자",
                MemberRole.ADMIN
        );
        이벤트를_저장한다(1L);
        이벤트를_저장한다(3L);
        이벤트를_저장한다(2L);

        mockMvc.perform(
                        get("/api/v1/admin/outbox-events")
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
                                        "2"
                                )
                                .param(
                                        "sort",
                                        "aggregateId,asc"
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.content[0].aggregateId").value(1L))
                .andExpect(jsonPath("$.data.content[1].aggregateId").value(2L));
    }

    @Test
    void 일반_사용자는_Outbox_이벤트를_조회할_수_없다() throws Exception {
        TestMember member = 회원과_토큰을_발급한다(
                "member@example.com",
                "일반 회원",
                MemberRole.USER
        );

        mockMvc.perform(
                        get("/api/v1/admin/outbox-events")
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
    void 인증되지_않은_사용자는_Outbox_이벤트를_조회할_수_없다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/outbox-events"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void Outbox_이벤트_조회_파라미터가_올바르지_않으면_오류를_반환한다() throws Exception {
        TestMember admin = 회원과_토큰을_발급한다(
                "invalid-admin@example.com",
                "관리자",
                MemberRole.ADMIN
        );

        mockMvc.perform(
                        get("/api/v1/admin/outbox-events")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + admin.accessToken()
                                )
                                .param(
                                        "status",
                                        "UNKNOWN"
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_OUTBOX_STATUS"));

        mockMvc.perform(
                        get("/api/v1/admin/outbox-events")
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
                        get("/api/v1/admin/outbox-events")
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

    private OutboxEvent 이벤트를_저장한다(Long aggregateId) {
        return outboxEventRepository.saveAndFlush(OutboxEvent.orderCompleted(
                UUID.randomUUID()
                        .toString(),
                aggregateId,
                """
                        {"eventType":"ORDER_COMPLETED"}
                        """
        ));
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
