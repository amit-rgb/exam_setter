package com.exam.setter.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ApiRateLimitFilter extends OncePerRequestFilter {

    private static final Duration WINDOW = Duration.ofHours(1);
    private static final int GENERATION_LIMIT = 10;
    private static final int INGESTION_LIMIT = 5;

    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String limitKey = limitKey(request);
        int limit = limitFor(request);

        if (limitKey != null && limit > 0 && !allow(limitKey, limit)) {
            response.setStatus(429);
            response.setHeader("Retry-After", "3600");
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"status\":\"RATE_LIMITED\",\"error\":\"Too many AI requests. Please try again later.\"}"
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private int limitFor(HttpServletRequest request) {
        if ("POST".equalsIgnoreCase(request.getMethod())
                && "/api/questions/generate".equals(request.getRequestURI())) {
            return GENERATION_LIMIT;
        }
        if ("POST".equalsIgnoreCase(request.getMethod())
                && "/api/ingest/pdf".equals(request.getRequestURI())) {
            return INGESTION_LIMIT;
        }
        return 0;
    }

    private String limitKey(HttpServletRequest request) {
        int limit = limitFor(request);
        if (limit == 0) return null;

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getName() != null) {
            return authentication.getName() + ":" + request.getRequestURI();
        }

        return request.getRemoteAddr() + ":" + request.getRequestURI();
    }

    private boolean allow(String key, int limit) {
        Instant now = Instant.now();
        WindowCounter counter = counters.compute(key, (k, existing) -> {
            if (existing == null || Duration.between(existing.windowStart(), now).compareTo(WINDOW) >= 0) {
                return new WindowCounter(now, 1);
            }
            return new WindowCounter(existing.windowStart(), existing.count() + 1);
        });
        return counter.count() <= limit;
    }

    private record WindowCounter(Instant windowStart, int count) {}
}
