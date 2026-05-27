package com.interview.homeshoptraffic.traffic;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TrafficGuardFilter extends OncePerRequestFilter {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final RequestRateLimiter rateLimiter;
    private final TrafficMetrics metrics;

    public TrafficGuardFilter(
        RequestRateLimiter rateLimiter,
        TrafficMetrics metrics
    ) {
        this.rateLimiter = rateLimiter;
        this.metrics = metrics;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equalsIgnoreCase(request.getMethod()) && "/api/orders".equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String key = resolveClientKey(request);
        RateLimitDecision decision = rateLimiter.tryConsume(key);

        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remainingTokens()));

        if (!decision.allowed()) {
            metrics.orderRejected();

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setCharacterEncoding("UTF-8");
            response.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
            response.getWriter().write("""
                    "{"success":false,"data":null,"message":"Too many requests. Please retry after a moment."}
                    """
                    );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveClientKey(HttpServletRequest request) {
        String userId = request.getHeader(USER_ID_HEADER);
        if (userId != null && !userId.isBlank()) {
            return "user:" + userId;
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return "ip:" + forwardedFor.split(",")[0].trim();
        }

        return "ip:" + request.getRemoteAddr();
    }
}
