package com.example.coffeeorder.common.security;

import java.io.IOException;

import com.example.coffeeorder.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final SecurityErrorResponseWriter securityErrorResponseWriter;

    public JwtAccessDeniedHandler(
            SecurityErrorResponseWriter securityErrorResponseWriter
    ) {
        this.securityErrorResponseWriter = securityErrorResponseWriter;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {
        securityErrorResponseWriter.write(
                response,
                ErrorCode.FORBIDDEN
        );
    }
}
