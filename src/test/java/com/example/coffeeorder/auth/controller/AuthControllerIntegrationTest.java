package com.example.coffeeorder.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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
}
