package com.example.coffeeorder.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.coffeeorder.common.response.ApiResponse;
import com.example.coffeeorder.common.security.AuthMember;
import com.example.coffeeorder.member.entity.Member;
import com.example.coffeeorder.member.entity.MemberRole;
import com.example.coffeeorder.member.entity.MemberStatus;
import com.example.coffeeorder.member.repository.MemberRepository;
import com.example.coffeeorder.point.entity.Point;
import com.example.coffeeorder.point.repository.PointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import(AuthControllerIntegrationTest.TestAuthController.class)
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PointRepository pointRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        pointRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    void 회원가입에_성공하면_회원과_0포인트_계정을_생성한다() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "email": " Test@Example.COM ",
                                          "password": "Password123!",
                                          "name": "홍길동"
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("회원가입이 완료되었습니다."))
                .andExpect(jsonPath("$.data.memberId").isNumber())
                .andExpect(jsonPath("$.data.email").value("test@example.com"))
                .andExpect(jsonPath("$.data.name").value("홍길동"))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.pointBalance").value(0));

        Member member = memberRepository.findByEmail("test@example.com")
                .orElseThrow();
        Point point = pointRepository.findByMemberId(member.getId())
                .orElseThrow();

        assertThat(member.getRole()).isEqualTo(MemberRole.USER);
        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(member.getPassword()).isNotEqualTo("Password123!");
        assertThat(passwordEncoder.matches(
                "Password123!",
                member.getPassword()
        )).isTrue();
        assertThat(point.getBalance()).isZero();
    }

    @Test
    void 정규화된_이메일이_중복되면_회원가입에_실패한다() throws Exception {
        회원가입을_요청한다(
                " Test@Example.COM ",
                "Password123!",
                "홍길동"
        );

        mockMvc.perform(
                        post("/api/v1/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "email": "test@example.com",
                                          "password": "AnotherPassword123!",
                                          "name": "김철수"
                                        }
                                        """)
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("DUPLICATED_EMAIL"))
                .andExpect(jsonPath("$.message").value("이미 사용 중인 이메일입니다."))
                .andExpect(jsonPath("$.data").doesNotExist());

        assertThat(memberRepository.count()).isEqualTo(1);
        assertThat(pointRepository.count()).isEqualTo(1);
    }

    @Test
    void 이메일_형식이_잘못되면_검증_오류를_반환한다() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "email": "invalid-email",
                                          "password": "Password123!",
                                          "name": "홍길동"
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("입력값이 올바르지 않습니다."))
                .andExpect(jsonPath("$.data.errors[0].field").value("email"))
                .andExpect(jsonPath("$.data.errors[0].message")
                        .value("올바른 이메일 형식이어야 합니다."));
    }

    @Test
    void 필수값이_누락되면_검증_오류를_반환한다() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "email": "",
                                          "password": "",
                                          "name": ""
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.data.errors[0].field").value("email"))
                .andExpect(jsonPath("$.data.errors[0].message").value("이메일은 필수입니다."))
                .andExpect(jsonPath("$.data.errors[1].field").value("name"))
                .andExpect(jsonPath("$.data.errors[1].message").value("이름은 필수입니다."))
                .andExpect(jsonPath("$.data.errors[2].field").value("password"))
                .andExpect(jsonPath("$.data.errors[2].message").value("비밀번호는 필수입니다."));
    }

    @Test
    void 로그인에_성공하면_AccessToken과_RefreshToken을_발급한다() throws Exception {
        회원가입을_요청한다(
                " Test@Example.COM ",
                "Password123!",
                "홍길동"
        );

        MvcResult result = mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "email": " TEST@example.com ",
                                          "password": "Password123!"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("로그인에 성공했습니다."))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isString())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessTokenExpiresIn").value(1800))
                .andExpect(jsonPath("$.data.refreshTokenExpiresIn").value(1209600))
                .andReturn();

        String accessToken = accessTokenFrom(result);
        String refreshToken = tokenDataFrom(result).refreshToken();
        Member member = memberRepository.findByEmail("test@example.com")
                .orElseThrow();

        mockMvc.perform(
                        get("/api/v1/test/auth-member")
                                .header(
                                        "Authorization",
                                        "Bearer " + accessToken
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberId").value(member.getId()))
                .andExpect(jsonPath("$.data.email").value("test@example.com"))
                .andExpect(jsonPath("$.data.role").value("USER"));

        mockMvc.perform(
                        get("/api/v1/test/auth-member")
                                .header(
                                        "Authorization",
                                        "Bearer " + refreshToken
                                )
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void 비밀번호가_일치하지_않으면_로그인에_실패한다() throws Exception {
        회원가입을_요청한다(
                "test@example.com",
                "Password123!",
                "홍길동"
        );

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "email": "test@example.com",
                                          "password": "WrongPassword123!"
                                        }
                                        """)
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_LOGIN"))
                .andExpect(jsonPath("$.message").value("이메일 또는 비밀번호가 일치하지 않습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 비활성화된_회원은_로그인할_수_없다() throws Exception {
        회원가입을_요청한다(
                "test@example.com",
                "Password123!",
                "홍길동"
        );
        회원상태를_변경한다(
                "test@example.com",
                MemberStatus.INACTIVE
        );

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "email": "test@example.com",
                                          "password": "Password123!"
                                        }
                                        """)
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEMBER_INACTIVE"))
                .andExpect(jsonPath("$.message").value("비활성화된 회원입니다."));
    }

    @Test
    void 탈퇴한_회원은_로그인할_수_없다() throws Exception {
        회원가입을_요청한다(
                "test@example.com",
                "Password123!",
                "홍길동"
        );
        회원상태를_변경한다(
                "test@example.com",
                MemberStatus.WITHDRAWN
        );

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "email": "test@example.com",
                                          "password": "Password123!"
                                        }
                                        """)
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEMBER_WITHDRAWN"))
                .andExpect(jsonPath("$.message").value("탈퇴한 회원입니다."));
    }

    @Test
    void 인증이_필요한_API는_토큰이_없으면_거부된다() throws Exception {
        mockMvc.perform(get("/api/v1/test/auth-member"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("인증되지 않은 사용자입니다."));
    }

    @Test
    void 유효하지_않은_토큰이면_공통_인증_오류를_반환한다() throws Exception {
        mockMvc.perform(
                        get("/api/v1/test/auth-member")
                                .header(
                                        "Authorization",
                                        "Bearer invalid-token"
                                )
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 Access Token입니다."));
    }

    @Test
    void 일반_사용자는_관리자_API에_접근할_수_없다() throws Exception {
        String accessToken = 로그인_후_AccessToken을_반환한다(
                "test@example.com",
                "Password123!",
                "홍길동"
        );

        mockMvc.perform(
                        get("/api/v1/admin/test")
                                .header(
                                        "Authorization",
                                        "Bearer " + accessToken
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("접근 권한이 없습니다."));
    }

    @Test
    void 관리자는_관리자_API에_접근할_수_있다() throws Exception {
        회원가입을_요청한다(
                "admin@example.com",
                "Password123!",
                "관리자"
        );
        회원권한을_변경한다(
                "admin@example.com",
                MemberRole.ADMIN
        );
        String accessToken = 로그인_AccessToken을_요청한다(
                "admin@example.com",
                "Password123!"
        );

        mockMvc.perform(
                        get("/api/v1/admin/test")
                                .header(
                                        "Authorization",
                                        "Bearer " + accessToken
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("admin-ok"));
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

    private String 로그인_후_AccessToken을_반환한다(
            String email,
            String password,
            String name
    ) throws Exception {
        회원가입을_요청한다(
                email,
                password,
                name
        );

        return 로그인_AccessToken을_요청한다(
                email,
                password
        );
    }

    private String 로그인_AccessToken을_요청한다(
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

        return accessTokenFrom(result);
    }

    private void 회원상태를_변경한다(
            String email,
            MemberStatus status
    ) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow();

        ReflectionTestUtils.setField(
                member,
                "status",
                status
        );
        memberRepository.saveAndFlush(member);
    }

    private void 회원권한을_변경한다(
            String email,
            MemberRole role
    ) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow();

        ReflectionTestUtils.setField(
                member,
                "role",
                role
        );
        memberRepository.saveAndFlush(member);
    }

    private String accessTokenFrom(MvcResult result) throws Exception {
        return tokenDataFrom(result).accessToken();
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

    @RestController
    static class TestAuthController {

        @GetMapping("/api/v1/test/auth-member")
        ResponseEntity<ApiResponse<TestAuthMemberResponse>> authMember(
                @AuthenticationPrincipal AuthMember authMember
        ) {
            return ResponseEntity.ok(ApiResponse.success(
                    new TestAuthMemberResponse(
                            authMember.memberId(),
                            authMember.email(),
                            authMember.role()
                    )
            ));
        }

        @GetMapping("/api/v1/admin/test")
        ResponseEntity<ApiResponse<String>> admin() {
            return ResponseEntity.ok(ApiResponse.success("admin-ok"));
        }
    }

    private record TestAuthMemberResponse(
            Long memberId,
            String email,
            MemberRole role
    ) {
    }
}
