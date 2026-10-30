package com.agentprobe.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

/** Placed after the bearer-token filter, so authenticated users are limited per user and everyone else per IP. */
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter limiter;

    public RateLimitFilter(RateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String path = req.getRequestURI();
        if (path.startsWith("/actuator") || path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs")) {
            chain.doFilter(req, res);
            return;
        }
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        boolean authed = a != null && a.isAuthenticated() && !(a instanceof AnonymousAuthenticationToken);

        String bucket;
        String subject;
        if (path.startsWith("/api/auth")) {
            bucket = "auth";
            subject = "ip:" + limiter.clientIp(req);
        } else if (authed) {
            bucket = "POST".equals(req.getMethod()) && path.endsWith("/runs") ? "runs" : "api";
            subject = "u:" + a.getName();
        } else {
            bucket = "anon";
            subject = "ip:" + limiter.clientIp(req);
        }

        RateLimiter.Decision d = limiter.check(bucket, subject);
        res.setHeader("X-RateLimit-Limit", String.valueOf(d.limit()));
        res.setHeader("X-RateLimit-Remaining", String.valueOf(d.remaining()));
        if (!d.allowed()) {
            res.setStatus(429);
            res.setHeader("Retry-After", String.valueOf(d.retryAfterSeconds()));
            res.setContentType("application/json");
            res.getWriter().write("{\"timestamp\":\"" + Instant.now() + "\",\"status\":429,"
                    + "\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded. Retry in "
                    + d.retryAfterSeconds() + " seconds.\"}");
            return;
        }
        chain.doFilter(req, res);
    }
}