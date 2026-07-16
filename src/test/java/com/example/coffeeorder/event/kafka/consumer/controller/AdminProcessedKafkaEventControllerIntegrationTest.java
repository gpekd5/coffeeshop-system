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
import com.example.coffeeorder.event.kafka.consumer.entity.ProcessedKafkaEvent;
import com.example.coffeeorder.event.kafka.consumer.repository.ProcessedKafkaEventRepository;
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
@Import(AdminProcessedKafkaEventControllerIntegrationTest.TestTokenStoreConfig.class)
class AdminProcessedKafkaEventControllerIntegrationTest {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ORDER_COMPLETED_EVENT_TYPE = "ORDER_COMPLETED";
    private static final String ORDER_COMPLETED_TOPIC = "order.completed";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProcessedKafkaEventRepository processedKafkaEventRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private TokenStore tokenStore;

    @BeforeEach
    void setUp() {
        processedKafkaEventRepository.deleteAll();
        memberRepository.deleteAll();

        if (tokenStore instanceof InMemoryTokenStore inMemoryTokenStore) {
            inMemoryTokenStore.clear();
        }
    }

    @Test
    void 관리자는_Kafka_이벤트_처리_이력을_상태별로_조회할_수_있다() throws Exception {
        TestMember admin = 회원과_토큰을_발급한다(
                "admin@example.com",
                "관리자",
                MemberRole.ADMIN
        );
        LocalDateTime deadlineAt = LocalDateTime.of(
                2026,
                7,
                17,
                10,
                0
        );
        LocalDateTime processedAt = LocalDateTime.of(
                2026,
                7,
                17,
                10,
                5
        );
        ProcessedKafkaEvent processingEvent = 처리중_이벤트를_저장한다(
                10L,
                deadlineAt
        );
        ProcessedKafkaEvent completedEvent = 완료_이벤트를_저장한다(
                20L,
                deadlineAt.plusMinutes(1),
                processedAt
        );
        ProcessedKafkaEvent failedEvent = 실패_이벤트를_저장한다(
                30L,
                deadlineAt.plusMinutes(2),
                "External API 500"
        );

        mockMvc.perform(
                        get("/api/v1/admin/processed-kafka-events")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + admin.accessToken()
                                )
                                .param(
                                        "status",
                                        "PROCESSING"
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("Kafka 이벤트 처리 이력 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].eventId").value(processingEvent.getEventId()))
                .andExpect(jsonPath("$.data.content[0].eventType").value(ORDER_COMPLETED_EVENT_TYPE))
                .andExpect(jsonPath("$.data.content[0].status").value("PROCESSING"))
                .andExpect(jsonPath("$.data.content[0].topic").value(ORDER_COMPLETED_TOPIC))
                .andExpect(jsonPath("$.data.content[0].kafkaPartition").value(0))
                .andExpect(jsonPath("$.data.content[0].kafkaOffset").value(10L))
                .andExpect(jsonPath("$.data.content[0].attemptCount").value(1))
                .andExpect(jsonPath("$.data.content[0].lastError").isEmpty())
                .andExpect(jsonPath("$.data.content[0].processingDeadlineAt").value("2026-07-17T10:00:00"))
                .andExpect(jsonPath("$.data.content[0].processedAt").isEmpty())
                .andExpect(jsonPath("$.data.content[0].createdAt").exists());

        mockMvc.perform(
                        get("/api/v1/admin/processed-kafka-events")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + admin.accessToken()
                                )
                                .param(
                                        "status",
                                        "COMPLETED"
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].eventId").value(completedEvent.getEventId()))
                .andExpect(jsonPath("$.data.content[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.content[0].attemptCount").value(1))
                .andExpect(jsonPath("$.data.content[0].processingDeadlineAt").isEmpty())
                .andExpect(jsonPath("$.data.content[0].processedAt").value("2026-07-17T10:05:00"));

        mockMvc.perform(
                        get("/api/v1/admin/processed-kafka-events")
                                .header(
                                        AUTHORIZATION_HEADER,
                                        BEARER_PREFIX + admin.accessToken()
                                )
                                .param(
                                        "status",
                                        "FAILED"
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].eventId").value(failedEvent.getEventId()))
                .andExpect(jsonPath("$.data.content[0].status").value("FAILED"))
                .andExpect(jsonPath("$.data.content[0].attemptCount").value(2))
                .andExpect(jsonPath("$.data.content[0].lastError").value("External API 500"))
                .andExpect(jsonPath("$.data.content[0].processingDeadlineAt").isEmpty())
                .andExpect(jsonPath("$.data.content[0].processedAt").isEmpty());
    }

    @Test
    void 관리자는_Kafka_이벤트_처리_이력_전체를_페이징_정렬해_조회할_수_있다() throws Exception {
        TestMember admin = 회원과_토큰을_발급한다(
                "admin-all@example.com",
                "관리자",
                MemberRole.ADMIN
        );
        LocalDateTime deadlineAt = LocalDateTime.of(
                2026,
                7,
                17,
                11,
                0
        );
        처리중_이벤트를_저장한다(
                30L,
                deadlineAt
        );
        처리중_이벤트를_저장한다(
                10L,
                deadlineAt.plusMinutes(1)
        );
        처리중_이벤트를_저장한다(
                20L,
                deadlineAt.plusMinutes(2)
        );

        mockMvc.perform(
                        get("/api/v1/admin/processed-kafka-events")
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
                                        "kafkaOffset,asc"
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.content[0].kafkaOffset").value(10L))
                .andExpect(jsonPath("$.data.content[1].kafkaOffset").value(20L));
    }

    @Test
    void 일반_사용자는_Kafka_이벤트_처리_이력을_조회할_수_없다() throws Exception {
        TestMember member = 회원과_토큰을_발급한다(
                "member@example.com",
                "일반 회원",
                MemberRole.USER
        );

        mockMvc.perform(
                        get("/api/v1/admin/processed-kafka-events")
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
    void 인증되지_않은_사용자는_Kafka_이벤트_처리_이력을_조회할_수_없다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/processed-kafka-events"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void Kafka_이벤트_처리_이력_조회_파라미터가_올바르지_않으면_오류를_반환한다() throws Exception {
        TestMember admin = 회원과_토큰을_발급한다(
                "invalid-admin@example.com",
                "관리자",
                MemberRole.ADMIN
        );

        mockMvc.perform(
                        get("/api/v1/admin/processed-kafka-events")
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
                .andExpect(jsonPath("$.code").value("INVALID_KAFKA_EVENT_PROCESSING_STATUS"));

        mockMvc.perform(
                        get("/api/v1/admin/processed-kafka-events")
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
                        get("/api/v1/admin/processed-kafka-events")
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

    private ProcessedKafkaEvent 처리중_이벤트를_저장한다(
            Long kafkaOffset,
            LocalDateTime processingDeadlineAt
    ) {
        return processedKafkaEventRepository.saveAndFlush(
                처리중_이벤트를_생성한다(
                        kafkaOffset,
                        processingDeadlineAt
                )
        );
    }

    private ProcessedKafkaEvent 완료_이벤트를_저장한다(
            Long kafkaOffset,
            LocalDateTime processingDeadlineAt,
            LocalDateTime processedAt
    ) {
        ProcessedKafkaEvent event = 처리중_이벤트를_생성한다(
                kafkaOffset,
                processingDeadlineAt
        );
        event.complete(processedAt);

        return processedKafkaEventRepository.saveAndFlush(event);
    }

    private ProcessedKafkaEvent 실패_이벤트를_저장한다(
            Long kafkaOffset,
            LocalDateTime processingDeadlineAt,
            String lastError
    ) {
        ProcessedKafkaEvent event = 처리중_이벤트를_생성한다(
                kafkaOffset,
                processingDeadlineAt
        );
        event.startRetry(
                ORDER_COMPLETED_TOPIC,
                0,
                kafkaOffset + 1,
                processingDeadlineAt.plusMinutes(1)
        );
        event.fail(lastError);

        return processedKafkaEventRepository.saveAndFlush(event);
    }

    private ProcessedKafkaEvent 처리중_이벤트를_생성한다(
            Long kafkaOffset,
            LocalDateTime processingDeadlineAt
    ) {
        return ProcessedKafkaEvent.start(
                UUID.randomUUID()
                        .toString(),
                ORDER_COMPLETED_EVENT_TYPE,
                ORDER_COMPLETED_TOPIC,
                0,
                kafkaOffset,
                processingDeadlineAt
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
