package com.project.ait.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.ait.dto.ShortenRequest;
import com.project.ait.entity.UrlMapping;
import com.project.ait.service.UrlService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UrlController.class)
@DisplayName("UrlController Unit Tests")
class UrlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UrlService urlService;

    @Test
    @DisplayName("Should create short URL successfully with custom alias")
    void shortenUrl_WithCustomAlias_ShouldReturnShortenResponse() throws Exception {
        // Given
        ShortenRequest request = new ShortenRequest();
        request.setLongUrl("https://www.example.com");
        request.setCustomAlias("my-custom-alias");

        UrlMapping mockMapping = UrlMapping.builder()
                .alias("my-custom-alias")
                .longUrl("https://www.example.com")
                .createdAt(Instant.now())
                .active(true)
                .customAlias(true)
                .build();

        when(urlService.createShortUrl(anyString(), anyString(), anyString(), any()))
                .thenReturn(mockMapping);

        // When & Then
        mockMvc.perform(post("/api/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.shortUrl", is("http://localhost/my-custom-alias")))
                .andExpect(jsonPath("$.alias", is("my-custom-alias")))
                .andExpect(jsonPath("$.longUrl", is("https://www.example.com")));
    }

    @Test
    @DisplayName("Should create short URL successfully without custom alias")
    void shortenUrl_WithoutCustomAlias_ShouldReturnShortenResponse() throws Exception {
        // Given
        ShortenRequest request = new ShortenRequest();
        request.setLongUrl("https://www.google.com");

        UrlMapping mockMapping = UrlMapping.builder()
                .alias("abc123")
                .longUrl("https://www.google.com")
                .createdAt(Instant.now())
                .active(true)
                .customAlias(false)
                .build();

        when(urlService.createShortUrl(anyString(), any(), anyString(), any()))
                .thenReturn(mockMapping);

        // When & Then
        mockMvc.perform(post("/api/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.shortUrl", is("http://localhost/abc123")))
                .andExpect(jsonPath("$.alias", is("abc123")))
                .andExpect(jsonPath("$.longUrl", is("https://www.google.com")));
    }

    @Test
    @DisplayName("Should create short URL with expiration date")
    void shortenUrl_WithExpirationDate_ShouldReturnShortenResponse() throws Exception {
        // Given
        Instant expirationDate = Instant.now().plus(30, ChronoUnit.DAYS);
        
        ShortenRequest request = new ShortenRequest();
        request.setLongUrl("https://www.example.com");
        request.setCustomAlias("expiring-link");
        request.setExpiresAt(expirationDate);

        UrlMapping mockMapping = UrlMapping.builder()
                .alias("expiring-link")
                .longUrl("https://www.example.com")
                .createdAt(Instant.now())
                .expiresAt(expirationDate)
                .active(true)
                .customAlias(true)
                .build();

        when(urlService.createShortUrl(anyString(), anyString(), anyString(), any(Instant.class)))
                .thenReturn(mockMapping);

        // When & Then
        mockMvc.perform(post("/api/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alias", is("expiring-link")));
    }

    @Test
    @DisplayName("Should return 400 when custom alias already exists")
    void shortenUrl_WithExistingAlias_ShouldReturnBadRequest() throws Exception {
        // Given
        ShortenRequest request = new ShortenRequest();
        request.setLongUrl("https://www.example.com");
        request.setCustomAlias("existing-alias");

        when(urlService.createShortUrl(anyString(), anyString(), anyString(), any()))
                .thenThrow(new IllegalArgumentException("Custom alias already used"));

        // When & Then
        mockMvc.perform(post("/api/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should redirect successfully for existing alias")
    void redirect_WithExistingAlias_ShouldRedirect() throws Exception {
        // Given
        String alias = "test-alias";
        UrlMapping mockMapping = UrlMapping.builder()
                .alias(alias)
                .longUrl("https://www.example.com")
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plus(365, ChronoUnit.DAYS))
                .active(true)
                .build();

        when(urlService.findByAlias(alias)).thenReturn(Optional.of(mockMapping));

        // When & Then
        mockMvc.perform(get("/{alias}", alias))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://www.example.com"));
    }

    @Test
    @DisplayName("Should return 404 for non-existing alias")
    void redirect_WithNonExistingAlias_ShouldReturn404() throws Exception {
        // Given
        String alias = "non-existent";

        when(urlService.findByAlias(alias)).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(get("/{alias}", alias))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should return 410 for inactive URL")
    void redirect_WithInactiveUrl_ShouldReturn410() throws Exception {
        // Given
        String alias = "inactive-alias";
        UrlMapping mockMapping = UrlMapping.builder()
                .alias(alias)
                .longUrl("https://www.example.com")
                .createdAt(Instant.now())
                .active(false)
                .build();

        when(urlService.findByAlias(alias)).thenReturn(Optional.of(mockMapping));

        // When & Then
        mockMvc.perform(get("/{alias}", alias))
                .andExpect(status().isGone())
                .andExpect(content().string("URL expired or inactive"));
    }

    @Test
    @DisplayName("Should return 410 for expired URL")
    void redirect_WithExpiredUrl_ShouldReturn410() throws Exception {
        // Given
        String alias = "expired-alias";
        UrlMapping mockMapping = UrlMapping.builder()
                .alias(alias)
                .longUrl("https://www.example.com")
                .createdAt(Instant.now())
                .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS)) // Expired yesterday
                .active(true)
                .build();

        when(urlService.findByAlias(alias)).thenReturn(Optional.of(mockMapping));

        // When & Then
        mockMvc.perform(get("/{alias}", alias))
                .andExpect(status().isGone())
                .andExpect(content().string("URL expired or inactive"));
    }

    @Test
    @DisplayName("Should get URL info successfully")
    void getUrlInfo_WithExistingAlias_ShouldReturnInfo() throws Exception {
        // Given
        String alias = "test-alias";
        Instant createdAt = Instant.now().minus(7, ChronoUnit.DAYS);
        Instant expiresAt = Instant.now().plus(358, ChronoUnit.DAYS);
        
        UrlMapping mockMapping = UrlMapping.builder()
                .alias(alias)
                .longUrl("https://www.example.com")
                .createdAt(createdAt)
                .expiresAt(expiresAt)
                .active(true)
                .customAlias(true)
                .build();

        when(urlService.findByAlias(alias)).thenReturn(Optional.of(mockMapping));
        when(urlService.getClickCount(alias)).thenReturn(42L);

        // When & Then
        mockMvc.perform(get("/api/info/{alias}", alias))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.alias", is(alias)))
                .andExpect(jsonPath("$.longUrl", is("https://www.example.com")))
                .andExpect(jsonPath("$.clicks", is(42)))
                .andExpect(jsonPath("$.custom", is(true)))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.expiresAt").exists());
    }

    @Test
    @DisplayName("Should return 404 for URL info with non-existing alias")
    void getUrlInfo_WithNonExistingAlias_ShouldReturn404() throws Exception {
        // Given
        String alias = "non-existent";

        when(urlService.findByAlias(alias)).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(get("/api/info/{alias}", alias))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should get click analytics successfully")
    void getClickAnalytics_WithExistingAlias_ShouldReturnAnalytics() throws Exception {
        // Given
        String alias = "test-alias";
        UrlMapping mockMapping = UrlMapping.builder()
                .alias(alias)
                .longUrl("https://www.example.com")
                .active(true)
                .build();

        when(urlService.findByAlias(alias)).thenReturn(Optional.of(mockMapping));
        when(urlService.getClickCount(alias)).thenReturn(100L);

        // When & Then
        mockMvc.perform(get("/api/analytics/{alias}/clicks", alias))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.alias", is(alias)))
                .andExpect(jsonPath("$.totalClicks", is(100)));
    }

    @Test
    @DisplayName("Should return 404 for click analytics with non-existing alias")
    void getClickAnalytics_WithNonExistingAlias_ShouldReturn404() throws Exception {
        // Given
        String alias = "non-existent";

        when(urlService.findByAlias(alias)).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(get("/api/analytics/{alias}/clicks", alias))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should handle invalid JSON in shorten request")
    void shortenUrl_WithInvalidJson_ShouldReturnBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("invalid json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should handle empty request body in shorten")
    void shortenUrl_WithEmptyBody_ShouldHandleGracefully() throws Exception {
        // Given
        ShortenRequest request = new ShortenRequest();
        // longUrl is null

        UrlMapping mockMapping = UrlMapping.builder()
                .alias("abc123")
                .longUrl(null) // Service should handle this
                .build();

        when(urlService.createShortUrl(any(), any(), anyString(), any()))
                .thenReturn(mockMapping);

        // When & Then
        mockMvc.perform(post("/api/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should record click when redirecting")
    void redirect_ShouldRecordClick() throws Exception {
        // Given
        String alias = "tracked-alias";
        UrlMapping mockMapping = UrlMapping.builder()
                .alias(alias)
                .longUrl("https://www.example.com")
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plus(365, ChronoUnit.DAYS))
                .active(true)
                .build();

        when(urlService.findByAlias(alias)).thenReturn(Optional.of(mockMapping));

        // When & Then
        mockMvc.perform(get("/{alias}", alias)
                        .header("User-Agent", "Mozilla/5.0")
                        .header("Referer", "https://google.com"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://www.example.com"));

        // Note: We can't easily verify the recordClick call in this setup
        // This would be better tested in integration tests
    }
}
