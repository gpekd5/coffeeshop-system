package com.example.coffeeorder.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.example.coffeeorder.common.security.TokenStore;
import com.example.coffeeorder.member.entity.Member;
import com.example.coffeeorder.member.entity.MemberStatus;
import com.example.coffeeorder.member.repository.MemberRepository;
import com.example.coffeeorder.point.repository.PointRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import(MemberControllerIntegrationTest.TestTokenStoreConfig.class)
class MemberControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PointRepository pointRepository;

    @Autowired
    private TokenStore tokenStore;

    @BeforeEach
    void setUp() {
        pointRepository.deleteAll();
        memberRepository.deleteAll();

        if (tokenStore instanceof InMemoryTokenStore inMemoryTokenStore) {
            inMemoryTokenStore.clear();
        }
    }

    @Test
    void 인증된_사용자는_내_정보를_조회할_수_있다() throws Exception {
        TokenData tokens = 회원가입_후_로그인한다(
                "test@example.com",
                "Password123!",
                "홍길동"
        );
        Member member = memberRepository.findByEmail("test@example.com")
                .orElseThrow();

        mockMvc.perform(
                        get("/api/v1/members/me")
                                .header(
                                        "Authorization",
                                        "Bearer " + tokens.accessToken()
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("내 정보 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.memberId").value(member.getId()))
                .andExpect(jsonPath("$.data.email").value("test@example.com"))
                .andExpect(jsonPath("$.data.name").value("홍길동"))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.createdAt").isString())
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    void 내_정보_조회는_인증이_필요하다() throws Exception {
        mockMvc.perform(get("/api/v1/members/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("인증되지 않은 사용자입니다."));
    }

    @Test
    void 회원_탈퇴는_회원데이터를_물리삭제하지_않고_토큰을_차단한다() throws Exception {
        TokenData tokens = 회원가입_후_로그인한다(
                "test@example.com",
                "Password123!",
                "홍길동"
        );
        Member member = memberRepository.findByEmail("test@example.com")
                .orElseThrow();

        mockMvc.perform(
                        delete("/api/v1/members/me")
                                .header(
                                        "Authorization",
                                        "Bearer " + tokens.accessToken()
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("회원 탈퇴가 완료되었습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());

        Member withdrawnMember = memberRepository.findById(member.getId())
                .orElseThrow();

        assertThat(withdrawnMember.getStatus()).isEqualTo(MemberStatus.WITHDRAWN);
        assertThat(withdrawnMember.getDeletedAt()).isNotNull();
        assertThat(memberRepository.existsById(member.getId())).isTrue();
        assertThat(tokenStore.isAccessTokenBlacklisted(
                tokens.accessToken()
        )).isTrue();
        assertThat(tokenStore.matchesRefreshToken(
                member.getId(),
                tokens.refreshToken()
        )).isFalse();
    }

    @Test
    void 회원_탈퇴_후_요청_AccessToken과_RefreshToken을_사용할_수_없다() throws Exception {
        TokenData tokens = 회원가입_후_로그인한다(
                "test@example.com",
                "Password123!",
                "홍길동"
        );

        회원_탈퇴를_요청한다(tokens.accessToken());

        mockMvc.perform(
                        get("/api/v1/members/me")
                                .header(
                                        "Authorization",
                                        "Bearer " + tokens.accessToken()
                                )
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BLACKLISTED_TOKEN"))
                .andExpect(jsonPath("$.message").value("로그아웃 처리된 Access Token입니다."));

        mockMvc.perform(
                        post("/api/v1/auth/reissue")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "refreshToken": "%s"
                                        }
                                        """.formatted(tokens.refreshToken()))
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEMBER_WITHDRAWN"))
                .andExpect(jsonPath("$.message").value("탈퇴한 회원입니다."));
    }

    @Test
    void 회원_탈퇴_후_다른_AccessToken도_회원상태_검증으로_차단된다() throws Exception {
        회원가입을_요청한다(
                "test@example.com",
                "Password123!",
                "홍길동"
        );
        TokenData firstLoginTokens = 로그인_토큰을_요청한다(
                "test@example.com",
                "Password123!"
        );
        TokenData secondLoginTokens = 로그인_토큰을_요청한다(
                "test@example.com",
                "Password123!"
        );

        회원_탈퇴를_요청한다(firstLoginTokens.accessToken());

        mockMvc.perform(
                        get("/api/v1/members/me")
                                .header(
                                        "Authorization",
                                        "Bearer " + secondLoginTokens.accessToken()
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEMBER_WITHDRAWN"))
                .andExpect(jsonPath("$.message").value("탈퇴한 회원입니다."));
    }

    private TokenData 회원가입_후_로그인한다(
            String email,
            String password,
            String name
    ) throws Exception {
        회원가입을_요청한다(
                email,
                password,
                name
        );

        return 로그인_토큰을_요청한다(
                email,
                password
        );
    }

    private void 회원가입을_요청한다(
            String email,
            String password,
            String name
    ) throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "email": "%s",
                                          "password": "%s",
                                          "name": "%s"
                                        }
                                        """.formatted(
                                        email,
                                        password,
                                        name
                                ))
                )
                .andExpect(status().isCreated());
    }

    private TokenData 로그인_토큰을_요청한다(
            String email,
            String password
    ) throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "email": "%s",
                                          "password": "%s"
                                        }
                                        """.formatted(
                                        email,
                                        password
                                ))
                )
                .andExpect(status().isOk())
                .andReturn();

        return tokenDataFrom(result);
    }

    private void 회원_탈퇴를_요청한다(String accessToken) throws Exception {
        mockMvc.perform(
                        delete("/api/v1/members/me")
                                .header(
                                        "Authorization",
                                        "Bearer " + accessToken
                                )
                )
                .andExpect(status().isOk());
    }

    private TokenData tokenDataFrom(MvcResult result) throws Exception {
        MapResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                MapResponse.class
        );

        return response.data();
    }

    private record MapResponse(TokenData data) {
    }

    private record TokenData(
            String accessToken,
            String refreshToken
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
        public synchronized boolean matchesRefreshToken(
                Long memberId,
                String refreshToken
        ) {
            return refreshToken.equals(refreshTokens.get(memberId));
        }

        @Override
        public synchronized boolean rotateRefreshToken(
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
        public synchronized void deleteRefreshToken(Long memberId) {
            refreshTokens.remove(memberId);
        }

        @Override
        public synchronized void logoutTokens(
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
