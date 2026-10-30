package com.agentprobe.security;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;
import java.time.Instant;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, ApiTokenService apiTokens, RateLimiter rateLimiter)
            throws Exception {
        // API tokens (apt_...) are authenticated by ApiTokenFilter, so the JWT filter must not try to parse them.
        BearerTokenResolver defaultResolver = new DefaultBearerTokenResolver();
        BearerTokenResolver resolver = request -> {
            String token = defaultResolver.resolve(request);
            return token != null && token.startsWith(ApiTokenService.PREFIX) ? null : token;
        };

        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        .requestMatchers(
                                "/api/auth/**",
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/prometheus",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .bearerTokenResolver(resolver)
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint((req, res, ex) ->
                                writeError(req, res, 401, "Unauthorized",
                                        "Authentication required or token invalid"))
                        .accessDeniedHandler((req, res, ex) ->
                                writeError(req, res, 403, "Forbidden",
                                        "You do not have access to this resource")))
                .addFilterBefore(new ApiTokenFilter(apiTokens), BearerTokenAuthenticationFilter.class)
                .addFilterAfter(new RateLimitFilter(rateLimiter), BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    private static void writeError(HttpServletRequest req, HttpServletResponse res,
                                   int status, String error, String message) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        String path = req.getRequestURI().replace("\\", "\\\\").replace("\"", "\\\"");
        String body = "{\"timestamp\":\"%s\",\"status\":%d,\"error\":\"%s\",\"message\":\"%s\",\"path\":\"%s\"}"
                .formatted(Instant.now(), status, error, message, path);
        res.getWriter().write(body);
    }
}