package com.example.coffeeorder.common.security;

import java.io.IOException;

import com.example.coffeeorder.common.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final SecurityErrorResponseWriter securityErrorResponseWriter;

    public JwtAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider,
            SecurityErrorResponseWriter securityErrorResponseWriter
    ) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.securityErrorResponseWriter = securityErrorResponseWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            authenticate(request);
            filterChain.doFilter(
                    request,
                    response
            );
        } catch (JwtAuthenticationException exception) {
            SecurityContextHolder.clearContext();
            securityErrorResponseWriter.write(
                    response,
                    exception.getErrorCode()
            );
        }
    }

    private void authenticate(HttpServletRequest request) {
        String authorization = request.getHeader(AUTHORIZATION_HEADER);

        if (authorization == null || authorization.isBlank()) {
            return;
        }

        if (!authorization.startsWith(BEARER_PREFIX)) {
            throw new JwtAuthenticationException(
                    ErrorCode.INVALID_TOKEN
            );
        }

        String token = authorization.substring(BEARER_PREFIX.length());
        AuthMember authMember = jwtTokenProvider.getAuthMember(token);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        authMember,
                        null,
                        jwtTokenProvider.getAuthorities(authMember)
                );

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);
    }
}
