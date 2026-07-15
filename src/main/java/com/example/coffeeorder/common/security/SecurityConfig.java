package com.example.coffeeorder.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            AuthenticationEntryPoint authenticationEntryPoint,
            AccessDeniedHandler accessDeniedHandler,
            Environment environment
    )
            throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(
                        SessionCreationPolicy.STATELESS
                ))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .authorizeHttpRequests(requests -> {
                    requests.requestMatchers(
                            HttpMethod.POST,
                            "/api/v1/auth/signup",
                            "/api/v1/auth/login",
                            "/api/v1/auth/reissue"
                    ).permitAll();
                    requests.requestMatchers(
                            HttpMethod.GET,
                            "/api/v1/menus/**"
                    ).permitAll();

                    if (isMockOrderEventPublic(environment)) {
                        requests.requestMatchers(
                                HttpMethod.POST,
                                "/mock/v1/order-events"
                        ).permitAll();
                    }

                    requests.requestMatchers("/api/v1/admin/**").hasRole("ADMIN");
                    requests.anyRequest().authenticated();
                })
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                )
                .build();
    }

    private boolean isMockOrderEventPublic(Environment environment) {
        boolean enabled = environment.getProperty(
                "app.mock-order-event.enabled",
                Boolean.class,
                false
        );

        return enabled && (environment.matchesProfiles("local")
                || environment.matchesProfiles("test"));
    }
}
