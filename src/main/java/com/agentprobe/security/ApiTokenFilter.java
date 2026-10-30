package com.agentprobe.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/** Authenticates "Authorization: Bearer apt_..." before the normal JWT filter runs. Not a Spring bean on purpose. */
public class ApiTokenFilter extends OncePerRequestFilter {

    private final ApiTokenService service;

    public ApiTokenFilter(ApiTokenService service) {
        this.service = service;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer " + ApiTokenService.PREFIX)) {
            Optional<Authentication> auth = service.authenticate(header.substring(7).trim());
            if (auth.isPresent()) {
                SecurityContext ctx = SecurityContextHolder.createEmptyContext();
                ctx.setAuthentication(auth.get());
                SecurityContextHolder.setContext(ctx);
            }
        }
        chain.doFilter(req, res);
    }
}