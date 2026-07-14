package com.example.coffeeorder.common.security;

import java.io.IOException;

import com.example.coffeeorder.common.exception.ErrorCode;
import com.example.coffeeorder.member.entity.Member;
import com.example.coffeeorder.member.repository.MemberRepository;
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
    private final TokenStore tokenStore;
    private final MemberRepository memberRepository;
    private final SecurityErrorResponseWriter securityErrorResponseWriter;

    public JwtAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider,
            TokenStore tokenStore,
            MemberRepository memberRepository,
            SecurityErrorResponseWriter securityErrorResponseWriter
    ) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.tokenStore = tokenStore;
        this.memberRepository = memberRepository;
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

        if (tokenStore.isAccessTokenBlacklisted(token)) {
            throw new JwtAuthenticationException(
                    ErrorCode.BLACKLISTED_TOKEN
            );
        }

        AuthMember authMember = jwtTokenProvider.getAuthMember(token);
        Member member = findAuthenticatableMember(authMember.memberId());
        AuthMember currentAuthMember = new AuthMember(
                member.getId(),
                member.getEmail(),
                member.getRole()
        );

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        currentAuthMember,
                        null,
                        jwtTokenProvider.getAuthorities(currentAuthMember)
                );

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);
    }

    private Member findAuthenticatableMember(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new JwtAuthenticationException(
                        ErrorCode.MEMBER_NOT_FOUND
                ));

        if (member.isInactive()) {
            throw new JwtAuthenticationException(ErrorCode.MEMBER_INACTIVE);
        }

        if (member.isWithdrawn()) {
            throw new JwtAuthenticationException(ErrorCode.MEMBER_WITHDRAWN);
        }

        return member;
    }
}
