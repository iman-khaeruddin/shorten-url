package com.project.ait.integration;

import com.project.ait.dto.ShortenRequest;
import com.project.ait.dto.ShortenResponse;
import com.project.ait.entity.UrlMapping;
import com.project.ait.repository.ClickEventRepository;
import com.project.ait.repository.UrlMappingRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("URL Shortener Integration Tests")
class UrlShortenerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UrlMappingRepository urlMappingRepository;

    @Autowired
    private ClickEventRepository clickEventRepository;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Use H2 in-memory database for testing
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.H2Dialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        
        // Disable Redis for integration tests
        registry.add("spring.cache.type", () -> "none");
        
        // Set base URL for testing
        registry.add("app.base-url", () -> "http://localhost");
    }

    @AfterEach
    void cleanup() {
        clickEventRepository.deleteAll();
        urlMappingRepository.deleteAll();
    }

    @Test
    @DisplayName("Should create and retrieve short URL successfully")
    void completeFlow_CreateAndRedirect_ShouldWork() {
        // Given
        ShortenRequest request = new ShortenRequest();
        request.setLongUrl("https://www.example.com");
        request.setCustomAlias("test-alias");

        // When - Create short URL
        ResponseEntity<ShortenResponse> createResponse = restTemplate.postForEntity(
                "/api/shorten",
                request,
                ShortenResponse.class
        );

        // Then - Verify creation
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(createResponse.getBody()).isNotNull();
        assertThat(createResponse.getBody().getAlias()).isEqualTo("test-alias");
        assertThat(createResponse.getBody().getLongUrl()).isEqualTo("https://www.example.com");
        assertThat(createResponse.getBody().getShortUrl()).contains("test-alias");

        // When - Access redirect endpoint
        ResponseEntity<String> redirectResponse = restTemplate.exchange(
                "/test-alias",
                HttpMethod.GET,
                null,
                String.class
        );

        // Then - Verify redirect
        assertThat(redirectResponse.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(redirectResponse.getHeaders().getLocation().toString()).isEqualTo("https://www.example.com");

        // Verify URL mapping was stored in database
        Optional<UrlMapping> savedMapping = urlMappingRepository.findByAlias("test-alias");
        assertThat(savedMapping).isPresent();
        assertThat(savedMapping.get().getLongUrl()).isEqualTo("https://www.example.com");
        assertThat(savedMapping.get().isCustomAlias()).isTrue();
    }

    @Test
    @DisplayName("Should handle generated alias when no custom alias provided")
    void createShortUrl_WithoutCustomAlias_ShouldGenerateAlias() {
        // Given
        ShortenRequest request = new ShortenRequest();
        request.setLongUrl("https://www.google.com");

        // When
        ResponseEntity<ShortenResponse> response = restTemplate.postForEntity(
                "/api/shorten",
                request,
                ShortenResponse.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getAlias()).isNotNull();
        assertThat(response.getBody().getAlias()).hasSize(5); // Base62.encode(5)
        assertThat(response.getBody().getLongUrl()).isEqualTo("https://www.google.com");

        // Verify in database
        Optional<UrlMapping> savedMapping = urlMappingRepository.findByAlias(response.getBody().getAlias());
        assertThat(savedMapping).isPresent();
        assertThat(savedMapping.get().isCustomAlias()).isFalse();
    }

    @Test
    @DisplayName("Should return 400 when custom alias already exists")
    void createShortUrl_WithExistingCustomAlias_ShouldReturn400() {
        // Given - First create a URL with custom alias
        ShortenRequest firstRequest = new ShortenRequest();
        firstRequest.setLongUrl("https://www.example.com");
        firstRequest.setCustomAlias("duplicate-alias");

        restTemplate.postForEntity("/api/shorten", firstRequest, ShortenResponse.class);

        // When - Try to create another URL with the same alias
        ShortenRequest secondRequest = new ShortenRequest();
        secondRequest.setLongUrl("https://www.different.com");
        secondRequest.setCustomAlias("duplicate-alias");

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/shorten",
                secondRequest,
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Should get URL information successfully")
    void getUrlInfo_WithExistingAlias_ShouldReturnInfo() {
        // Given - Create a URL first
        ShortenRequest request = new ShortenRequest();
        request.setLongUrl("https://www.example.com");
        request.setCustomAlias("info-test");

        restTemplate.postForEntity("/api/shorten", request, ShortenResponse.class);

        // When
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "/api/info/info-test",
                Map.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("alias")).isEqualTo("info-test");
        assertThat(response.getBody().get("longUrl")).isEqualTo("https://www.example.com");
        assertThat(response.getBody().get("clicks")).isEqualTo(0);
        assertThat(response.getBody().get("custom")).isEqualTo(true);
        assertThat(response.getBody().get("createdAt")).isNotNull();
    }

    @Test
    @DisplayName("Should return 404 for non-existing alias in info endpoint")
    void getUrlInfo_WithNonExistingAlias_ShouldReturn404() {
        // When
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/info/non-existent-alias",
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should track clicks and return analytics")
    void clickAnalytics_ShouldTrackClicksCorrectly() {
        // Given - Create a URL
        ShortenRequest request = new ShortenRequest();
        request.setLongUrl("https://www.example.com");
        request.setCustomAlias("analytics-test");

        restTemplate.postForEntity("/api/shorten", request, ShortenResponse.class);

        // When - Access the short URL multiple times
        for (int i = 0; i < 3; i++) {
            restTemplate.exchange("/analytics-test", HttpMethod.GET, null, String.class);
        }

        // Give some time for async click recording
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Then - Check analytics
        ResponseEntity<Map> analyticsResponse = restTemplate.getForEntity(
                "/api/analytics/analytics-test/clicks",
                Map.class
        );

        assertThat(analyticsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(analyticsResponse.getBody()).isNotNull();
        assertThat(analyticsResponse.getBody().get("alias")).isEqualTo("analytics-test");
        assertThat(analyticsResponse.getBody().get("totalClicks")).isEqualTo(3);

        // Verify clicks were recorded in database
        long clickCount = clickEventRepository.countByAlias("analytics-test");
        assertThat(clickCount).isEqualTo(3);
    }

    @Test
    @DisplayName("Should return 404 for non-existing alias in redirect")
    void redirect_WithNonExistingAlias_ShouldReturn404() {
        // When
        ResponseEntity<String> response = restTemplate.exchange(
                "/non-existent-alias",
                HttpMethod.GET,
                null,
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should return 410 for expired URL")
    void redirect_WithExpiredUrl_ShouldReturn410() {
        // Given - Create URL mapping directly with expired date
        UrlMapping expiredMapping = UrlMapping.builder()
                .alias("expired-url")
                .longUrl("https://www.example.com")
                .createdAt(Instant.now())
                .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .active(true)
                .customAlias(true)
                .build();

        urlMappingRepository.save(expiredMapping);

        // When
        ResponseEntity<String> response = restTemplate.exchange(
                "/expired-url",
                HttpMethod.GET,
                null,
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(response.getBody()).isEqualTo("URL expired or inactive");
    }

    @Test
    @DisplayName("Should return 410 for inactive URL")
    void redirect_WithInactiveUrl_ShouldReturn410() {
        // Given - Create inactive URL mapping
        UrlMapping inactiveMapping = UrlMapping.builder()
                .alias("inactive-url")
                .longUrl("https://www.example.com")
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plus(365, ChronoUnit.DAYS))
                .active(false)
                .customAlias(true)
                .build();

        urlMappingRepository.save(inactiveMapping);

        // When
        ResponseEntity<String> response = restTemplate.exchange(
                "/inactive-url",
                HttpMethod.GET,
                null,
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(response.getBody()).isEqualTo("URL expired or inactive");
    }

    @Test
    @DisplayName("Should handle URL with expiration date")
    void createShortUrl_WithExpirationDate_ShouldRespectExpiration() {
        // Given
        Instant expirationDate = Instant.now().plus(30, ChronoUnit.DAYS);
        ShortenRequest request = new ShortenRequest();
        request.setLongUrl("https://www.example.com");
        request.setCustomAlias("expiring-url");
        request.setExpiresAt(expirationDate);

        // When
        ResponseEntity<ShortenResponse> response = restTemplate.postForEntity(
                "/api/shorten",
                request,
                ShortenResponse.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        // Verify in database
        Optional<UrlMapping> savedMapping = urlMappingRepository.findByAlias("expiring-url");
        assertThat(savedMapping).isPresent();
        assertThat(savedMapping.get().getExpiresAt()).isCloseTo(expirationDate, within(1, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("Should handle concurrent requests without conflicts")
    void concurrentRequests_ShouldNotCauseConflicts() throws InterruptedException {
        // Given
        int numberOfThreads = 5;
        int requestsPerThread = 10;
        Thread[] threads = new Thread[numberOfThreads];

        // When - Make concurrent requests
        for (int i = 0; i < numberOfThreads; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < requestsPerThread; j++) {
                    ShortenRequest request = new ShortenRequest();
                    request.setLongUrl("https://www.example.com/thread" + threadId + "/request" + j);
                    
                    ResponseEntity<ShortenResponse> response = restTemplate.postForEntity(
                            "/api/shorten",
                            request,
                            ShortenResponse.class
                    );
                    
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                }
            });
            threads[i].start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // Then - Verify all URLs were created
        long totalMappings = urlMappingRepository.count();
        assertThat(totalMappings).isEqualTo(numberOfThreads * requestsPerThread);
    }
}
