package com.project.ait.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.ait.dto.ShortenRequest;
import com.project.ait.service.UrlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("test")
@DisplayName("Rate Limit Interceptor Tests")
class RateLimitInterceptorTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockBean
    private UrlService urlService;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Configure embedded Redis for testing
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> "6379");
        
        // Set strict rate limiting for testing
        registry.add("app.rate-limit.window-seconds", () -> "60");
        registry.add("app.rate-limit.max-requests", () -> "3");
        
        // Use H2 for database
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:testdb");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        
        // Disable cache for testing
        registry.add("spring.cache.type", () -> "none");
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        // Clean up Redis keys before each test
        redisTemplate.getConnectionFactory().getConnection().flushAll();
    }

    @Test
    @DisplayName("Should allow requests within rate limit")
    void rateLimiting_WithinLimit_ShouldAllowRequests() throws Exception {
        // Given
        ShortenRequest request = new ShortenRequest();
        request.setLongUrl("https://www.example.com");

        // When & Then - Make requests within limit (3 requests allowed)
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/shorten")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .header("X-Forwarded-For", "192.168.1.100"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-RateLimit-Limit", "3"))
                    .andExpect(header().string("X-RateLimit-Window", "60"))
                    .andExpect(header().string("X-RateLimit-Remaining", String.valueOf(2 - i)));
        }
    }

    @Test
    @DisplayName("Should block requests exceeding rate limit")
    void rateLimiting_ExceedingLimit_ShouldBlockRequests() throws Exception {
        // Given
        ShortenRequest request = new ShortenRequest();
        request.setLongUrl("https://www.example.com");

        // When - Make requests up to the limit
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/shorten")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .header("X-Forwarded-For", "192.168.1.101"))
                    .andExpect(status().isOk());
        }

        // Then - Fourth request should be rate limited
        mockMvc.perform(post("/api/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("X-Forwarded-For", "192.168.1.101"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error", is("Rate limit exceeded. Try again in 60 seconds.")));
    }

    @Test
    @DisplayName("Should track different IPs separately")
    void rateLimiting_DifferentIPs_ShouldTrackSeparately() throws Exception {
        // Given
        ShortenRequest request = new ShortenRequest();
        request.setLongUrl("https://www.example.com");

        // When - Make requests from first IP (up to limit)
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/shorten")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .header("X-Forwarded-For", "192.168.1.102"))
                    .andExpect(status().isOk());
        }

        // Then - Requests from second IP should still be allowed
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/shorten")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .header("X-Forwarded-For", "192.168.1.103"))
                    .andExpect(status().isOk());
        }

        // But fourth request from first IP should be blocked
        mockMvc.perform(post("/api/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("X-Forwarded-For", "192.168.1.102"))
                .andExpect(status().isTooManyRequests());

        // And fourth request from second IP should also be blocked
        mockMvc.perform(post("/api/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("X-Forwarded-For", "192.168.1.103"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("Should not apply rate limiting to non-API endpoints")
    void rateLimiting_NonAPIEndpoints_ShouldNotApply() throws Exception {
        // When & Then - Redirect endpoint should not be rate limited
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/some-alias")
                            .header("X-Forwarded-For", "192.168.1.104"))
                    .andExpect(status().isNotFound()); // 404 because alias doesn't exist, but not rate limited
        }
    }

    @Test
    @DisplayName("Should handle X-Real-IP header for client identification")
    void rateLimiting_WithXRealIP_ShouldIdentifyClient() throws Exception {
        // Given
        ShortenRequest request = new ShortenRequest();
        request.setLongUrl("https://www.example.com");

        // When - Make requests using X-Real-IP header
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/shorten")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .header("X-Real-IP", "192.168.1.105"))
                    .andExpect(status().isOk());
        }

        // Then - Fourth request should be rate limited
        mockMvc.perform(post("/api/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("X-Real-IP", "192.168.1.105"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("Should prefer X-Forwarded-For over X-Real-IP")
    void rateLimiting_WithBothHeaders_ShouldPreferXForwardedFor() throws Exception {
        // Given
        ShortenRequest request = new ShortenRequest();
        request.setLongUrl("https://www.example.com");

        // When - Make requests with both headers (should use X-Forwarded-For)
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/shorten")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .header("X-Forwarded-For", "192.168.1.106")
                            .header("X-Real-IP", "192.168.1.107"))
                    .andExpect(status().isOk());
        }

        // Then - Fourth request with same X-Forwarded-For should be blocked
        mockMvc.perform(post("/api/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("X-Forwarded-For", "192.168.1.106")
                        .header("X-Real-IP", "192.168.1.107"))
                .andExpect(status().isTooManyRequests());

        // But request with different X-Forwarded-For should be allowed
        mockMvc.perform(post("/api/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("X-Forwarded-For", "192.168.1.108")
                        .header("X-Real-IP", "192.168.1.107"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should handle comma-separated X-Forwarded-For header")
    void rateLimiting_WithCommaSeparatedXForwardedFor_ShouldUseFirstIP() throws Exception {
        // Given
        ShortenRequest request = new ShortenRequest();
        request.setLongUrl("https://www.example.com");

        // When - Make requests with comma-separated X-Forwarded-For
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/shorten")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .header("X-Forwarded-For", "192.168.1.109, 10.0.0.1, 172.16.0.1"))
                    .andExpect(status().isOk());
        }

        // Then - Fourth request should be rate limited (using first IP)
        mockMvc.perform(post("/api/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("X-Forwarded-For", "192.168.1.109, 10.0.0.2, 172.16.0.2"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("Should include correct rate limit headers")
    void rateLimiting_ShouldIncludeCorrectHeaders() throws Exception {
        // Given
        ShortenRequest request = new ShortenRequest();
        request.setLongUrl("https://www.example.com");

        // When & Then - Check headers in each request
        mockMvc.perform(post("/api/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("X-Forwarded-For", "192.168.1.110"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Limit", "3"))
                .andExpect(header().string("X-RateLimit-Remaining", "2"))
                .andExpect(header().string("X-RateLimit-Window", "60"));

        mockMvc.perform(post("/api/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("X-Forwarded-For", "192.168.1.110"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Remaining", "1"));

        mockMvc.perform(post("/api/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("X-Forwarded-For", "192.168.1.110"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Remaining", "0"));
    }
}
