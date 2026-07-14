package com.example.coffeeorder.common.security;

import java.io.IOException;

import com.example.coffeeorder.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final SecurityErrorResponseWriter securityErrorResponseWriter;

    public JwtAuthenticationEntryPoint(
            SecurityErrorResponseWriter securityErrorResponseWriter
    ) {
        this.securityErrorResponseWriter = securityErrorResponseWriter;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        securityErrorResponseWriter.write(
                response,
                ErrorCode.UNAUTHORIZED
        );
    }
}
