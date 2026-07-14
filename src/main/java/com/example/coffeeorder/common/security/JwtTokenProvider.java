package com.example.coffeeorder.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.example.coffeeorder.common.exception.ErrorCode;
import com.example.coffeeorder.member.entity.Member;
import com.example.coffeeorder.member.entity.MemberRole;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public class JwtTokenProvider {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String TOKEN_TYPE = "Bearer";
    private static final String ACCESS_TOKEN_TYPE = "ACCESS";
    private static final String REFRESH_TOKEN_TYPE = "REFRESH";
    private static final int MIN_SECRET_LENGTH = 32;
    private static final TypeReference<Map<String, Object>> CLAIMS_TYPE =
            new TypeReference<>() {
            };

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String secret;
    private final long accessTokenExpiresIn;
    private final long refreshTokenExpiresIn;

    public JwtTokenProvider(
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${app.jwt.secret:local-development-jwt-secret-key-please-change}") String secret,
            @Value("${app.jwt.access-token-expires-in-seconds:1800}") long accessTokenExpiresIn,
            @Value("${app.jwt.refresh-token-expires-in-seconds:1209600}") long refreshTokenExpiresIn
    ) {
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.secret = secret;
        this.accessTokenExpiresIn = accessTokenExpiresIn;
        this.refreshTokenExpiresIn = refreshTokenExpiresIn;
    }

    @PostConstruct
    void validateSecret() {
        if (secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException("JWT secret must be at least 32 characters.");
        }
    }

    public TokenResponse createLoginTokens(Member member) {
        return new TokenResponse(
                createToken(
                        member,
                        ACCESS_TOKEN_TYPE,
                        accessTokenExpiresIn
                ),
                createToken(
                        member,
                        REFRESH_TOKEN_TYPE,
                        refreshTokenExpiresIn
                ),
                TOKEN_TYPE,
                accessTokenExpiresIn,
                refreshTokenExpiresIn
        );
    }

    public AuthMember getAuthMember(String token) {
        try {
            Map<String, Object> claims = parseClaims(
                    token,
                    ErrorCode.INVALID_TOKEN,
                    ErrorCode.EXPIRED_TOKEN
            );

            validateTokenType(
                    claims,
                    ACCESS_TOKEN_TYPE,
                    ErrorCode.INVALID_TOKEN
            );

            return createAuthMember(claims);
        } catch (JwtAuthenticationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new JwtAuthenticationException(ErrorCode.INVALID_TOKEN);
        }
    }

    public AuthMember getRefreshAuthMember(String refreshToken) {
        try {
            Map<String, Object> claims = parseClaims(
                    refreshToken,
                    ErrorCode.INVALID_REFRESH_TOKEN,
                    ErrorCode.EXPIRED_REFRESH_TOKEN
            );

            validateTokenType(
                    claims,
                    REFRESH_TOKEN_TYPE,
                    ErrorCode.INVALID_REFRESH_TOKEN
            );

            return createAuthMember(claims);
        } catch (JwtAuthenticationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new JwtAuthenticationException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
    }

    public long getAccessTokenRemainingSeconds(String accessToken) {
        Map<String, Object> claims = parseClaims(
                accessToken,
                ErrorCode.INVALID_TOKEN,
                ErrorCode.EXPIRED_TOKEN
        );

        validateTokenType(
                claims,
                ACCESS_TOKEN_TYPE,
                ErrorCode.INVALID_TOKEN
        );

        long expiresAt = asLong(
                claims.get("exp"),
                ErrorCode.INVALID_TOKEN
        );
        long now = Instant.now(clock)
                .getEpochSecond();

        return Math.max(
                0,
                expiresAt - now
        );
    }

    public Collection<? extends GrantedAuthority> getAuthorities(
            AuthMember authMember
    ) {
        return List.of(new SimpleGrantedAuthority(
                "ROLE_" + authMember.role().name()
        ));
    }

    String createToken(
            Member member,
            String tokenType,
            long expiresIn
    ) {
        Instant now = Instant.now(clock);
        Map<String, Object> header = Map.of(
                "alg",
                "HS256",
                "typ",
                "JWT"
        );
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put(
                "sub",
                member.getId().toString()
        );
        claims.put(
                "email",
                member.getEmail()
        );
        claims.put(
                "role",
                member.getRole().name()
        );
        claims.put(
                "tokenType",
                tokenType
        );
        claims.put(
                "jti",
                UUID.randomUUID()
                        .toString()
        );
        claims.put(
                "iat",
                now.getEpochSecond()
        );
        claims.put(
                "exp",
                now.plusSeconds(expiresIn).getEpochSecond()
        );

        String unsignedToken = "%s.%s".formatted(
                encodeJson(header),
                encodeJson(claims)
        );

        return "%s.%s".formatted(
                unsignedToken,
                sign(unsignedToken)
        );
    }

    private Map<String, Object> parseClaims(
            String token,
            ErrorCode invalidTokenError,
            ErrorCode expiredTokenError
    ) {
        String[] parts = token.split("\\.");

        if (parts.length != 3) {
            throw new JwtAuthenticationException(invalidTokenError);
        }

        validateSignature(
                "%s.%s".formatted(
                        parts[0],
                        parts[1]
                ),
                parts[2],
                invalidTokenError
        );

        Map<String, Object> claims = decodeClaims(
                parts[1],
                invalidTokenError
        );
        validateExpiresAt(
                claims,
                invalidTokenError,
                expiredTokenError
        );

        return claims;
    }

    private void validateSignature(
            String unsignedToken,
            String signature,
            ErrorCode invalidTokenError
    ) {
        byte[] expected = sign(unsignedToken).getBytes(StandardCharsets.UTF_8);
        byte[] actual = signature.getBytes(StandardCharsets.UTF_8);

        if (!MessageDigest.isEqual(
                expected,
                actual
        )) {
            throw new JwtAuthenticationException(invalidTokenError);
        }
    }

    private void validateExpiresAt(
            Map<String, Object> claims,
            ErrorCode invalidTokenError,
            ErrorCode expiredTokenError
    ) {
        long expiresAt = asLong(
                claims.get("exp"),
                invalidTokenError
        );
        long now = Instant.now(clock).getEpochSecond();

        if (expiresAt <= now) {
            throw new JwtAuthenticationException(expiredTokenError);
        }
    }

    private void validateTokenType(
            Map<String, Object> claims,
            String tokenType,
            ErrorCode invalidTokenError
    ) {
        if (!tokenType.equals(claims.get("tokenType"))) {
            throw new JwtAuthenticationException(invalidTokenError);
        }
    }

    private String encodeJson(Map<String, Object> json) {
        try {
            return base64UrlEncode(objectMapper.writeValueAsBytes(json));
        } catch (Exception exception) {
            throw new JwtAuthenticationException(ErrorCode.INVALID_TOKEN);
        }
    }

    private Map<String, Object> decodeClaims(
            String encodedClaims,
            ErrorCode invalidTokenError
    ) {
        try {
            return objectMapper.readValue(
                    Base64.getUrlDecoder().decode(encodedClaims),
                    CLAIMS_TYPE
            );
        } catch (IllegalArgumentException exception) {
            throw new JwtAuthenticationException(invalidTokenError);
        } catch (Exception exception) {
            throw new JwtAuthenticationException(invalidTokenError);
        }
    }

    private String sign(String unsignedToken) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    HMAC_ALGORITHM
            ));

            return base64UrlEncode(mac.doFinal(
                    unsignedToken.getBytes(StandardCharsets.UTF_8)
            ));
        } catch (Exception exception) {
            throw new JwtAuthenticationException(ErrorCode.INVALID_TOKEN);
        }
    }

    private String base64UrlEncode(byte[] source) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(source);
    }

    private AuthMember createAuthMember(Map<String, Object> claims) {
        return new AuthMember(
                Long.valueOf(claims.get("sub").toString()),
                claims.get("email").toString(),
                MemberRole.valueOf(claims.get("role").toString())
        );
    }

    private long asLong(
            Object value,
            ErrorCode invalidTokenError
    ) {
        if (value == null) {
            throw new JwtAuthenticationException(invalidTokenError);
        }

        if (value instanceof Number number) {
            return number.longValue();
        }

        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException exception) {
            throw new JwtAuthenticationException(invalidTokenError);
        }
    }
}
