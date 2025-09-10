package com.project.ait.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate redisTemplate;
    private final int windowSeconds;
    private final int maxRequests;

    public RateLimitInterceptor(StringRedisTemplate redisTemplate,
                               @Value("${app.rate-limit.window-seconds}") int windowSeconds,
                               @Value("${app.rate-limit.max-requests}") int maxRequests) {
        this.redisTemplate = redisTemplate;
        this.windowSeconds = windowSeconds;
        this.maxRequests = maxRequests;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Only apply rate limiting to API endpoints
        String requestURI = request.getRequestURI();
        if (!requestURI.startsWith("/api/")) {
            return true; // Skip rate limiting for non-API requests
        }

        String clientIp = getClientIp(request);
        String key = "rate_limit:" + clientIp;

        try {
            String currentCountStr = redisTemplate.opsForValue().get(key);
            int currentCount = currentCountStr != null ? Integer.parseInt(currentCountStr) : 0;

            if (currentCount >= maxRequests) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Rate limit exceeded. Try again in " + windowSeconds + " seconds.\"}");
                return false;
            }

            // Increment counter
            redisTemplate.opsForValue().increment(key);
            
            // Set TTL on first request
            if (currentCount == 0) {
                redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
            }

            // Add rate limit headers
            response.setHeader("X-RateLimit-Limit", String.valueOf(maxRequests));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(maxRequests - currentCount - 1));
            response.setHeader("X-RateLimit-Window", String.valueOf(windowSeconds));

            return true;

        } catch (Exception e) {
            // If Redis is down, allow the request (fail-open approach)
            return true;
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}
