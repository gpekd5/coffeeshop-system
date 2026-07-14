package com.example.coffeeorder.common.exception;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;
    private LocalValidatorFactoryBean validator;

    @BeforeEach
    void setUp() {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void 비즈니스_예외를_공통_에러_응답으로_반환한다() throws Exception {
        mockMvc.perform(get("/business-exception"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("MENU_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("메뉴를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void 요청_본문_검증_오류를_공통_검증_응답으로_반환한다() throws Exception {
        mockMvc.perform(
                        post("/validation")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "name": "",
                                          "email": "invalid-email"
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("입력값이 올바르지 않습니다."))
                .andExpect(jsonPath("$.data.errors[0].field").value("email"))
                .andExpect(jsonPath("$.data.errors[0].message").value("올바른 이메일 형식이어야 합니다."))
                .andExpect(jsonPath("$.data.errors[1].field").value("name"))
                .andExpect(jsonPath("$.data.errors[1].message").value("이름은 필수입니다."));
    }

    @Test
    void 요청_파라미터_검증_오류를_공통_검증_응답으로_반환한다() throws Exception {
        mockMvc.perform(get("/param-validation").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("입력값이 올바르지 않습니다."))
                .andExpect(jsonPath("$.data.errors[0].field").value("size"))
                .andExpect(jsonPath("$.data.errors[0].message").value("size는 1 이상이어야 합니다."));
    }

    @Test
    void 경로_변수_검증_오류를_공통_검증_응답으로_반환한다() throws Exception {
        mockMvc.perform(get("/path-validation/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("입력값이 올바르지 않습니다."))
                .andExpect(jsonPath("$.data.errors[0].field").value("menuId"))
                .andExpect(jsonPath("$.data.errors[0].message").value("menuId는 1 이상이어야 합니다."));
    }

    @Test
    void 필수_요청_파라미터_누락을_공통_검증_응답으로_반환한다() throws Exception {
        mockMvc.perform(get("/missing-param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("입력값이 올바르지 않습니다."))
                .andExpect(jsonPath("$.data.errors[0].field").value("keyword"))
                .andExpect(jsonPath("$.data.errors[0].message").value("필수 요청 파라미터입니다."));
    }

    @Test
    void 예상하지_못한_예외는_내부_정보를_숨기고_공통_에러_응답으로_반환한다() throws Exception {
        mockMvc.perform(get("/unexpected-exception"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @RestController
    private static class TestController {

        @GetMapping("/business-exception")
        void businessException() {
            throw new BusinessException(ErrorCode.MENU_NOT_FOUND);
        }

        @PostMapping("/validation")
        void validation(
                @Valid @RequestBody TestRequest request
        ) {
        }

        @GetMapping("/param-validation")
        void paramValidation(
                @RequestParam("size")
                @Min(
                        value = 1,
                        message = "size는 1 이상이어야 합니다."
                )
                int size
        ) {
        }

        @GetMapping("/path-validation/{menuId}")
        void pathValidation(
                @PathVariable("menuId")
                @Min(
                        value = 1,
                        message = "menuId는 1 이상이어야 합니다."
                )
                long menuId
        ) {
        }

        @GetMapping("/missing-param")
        void missingParam(
                @RequestParam("keyword") String keyword
        ) {
        }

        @GetMapping("/unexpected-exception")
        void unexpectedException() {
            throw new IllegalStateException("database password leaked");
        }
    }

    private record TestRequest(
            @NotBlank(message = "이름은 필수입니다.")
            String name,

            @Email(message = "올바른 이메일 형식이어야 합니다.")
            String email
    ) {
    }
}
