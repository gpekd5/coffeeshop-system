package com.example.coffeeorder.event.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class MockOrderEventControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 주문_완료_이벤트를_수신하면_성공_응답을_반환한다() throws Exception {
        mockMvc.perform(
                        post("/mock/v1/order-events")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(주문_완료_이벤트_JSON())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("주문 이벤트를 수신했습니다."));
    }

    @Test
    void status_파라미터로_서버_오류를_재현할_수_있다() throws Exception {
        mockMvc.perform(
                        post("/mock/v1/order-events")
                                .param(
                                        "status",
                                        "500"
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(주문_완료_이벤트_JSON())
                )
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("주문 이벤트 수신에 실패했습니다."));
    }

    private String 주문_완료_이벤트_JSON() {
        return """
                {
                  "eventId": "4fb6a592-65e8-4e5d-8833-77a4ca1f661a",
                  "eventType": "ORDER_COMPLETED",
                  "orderId": 50,
                  "orderNumber": "ORD-20260713-000050",
                  "memberId": 1,
                  "orderChannel": "WEB_CART",
                  "items": [
                    {
                      "menuId": 1,
                      "menuName": "아메리카노",
                      "menuCategory": "COFFEE",
                      "unitPrice": 4500,
                      "quantity": 2,
                      "lineAmount": 9000
                    }
                  ],
                  "totalAmount": 9000,
                  "paymentMethod": "POINT",
                  "orderedAt": "2026-07-13T14:00:00"
                }
                """;
    }
}
