package com.project.ait.service;

import com.project.ait.entity.ClickEvent;
import com.project.ait.entity.UrlMapping;
import com.project.ait.repository.ClickEventRepository;
import com.project.ait.repository.UrlMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UrlService Unit Tests")
class UrlServiceTest {

    @Mock
    private UrlMappingRepository urlMappingRepository;

    @Mock
    private ClickEventRepository clickEventRepository;

    private UrlService urlService;

    private final String baseUrl = "http://localhost:8080";
    private final int defaultExpirationDays = 365;

    @BeforeEach
    void setUp() {
        // Manually inject the values since @Value annotations don't work in unit tests
        urlService = new UrlService(urlMappingRepository, clickEventRepository, baseUrl, defaultExpirationDays);
    }

    @Test
    @DisplayName("Should create short URL with custom alias successfully")
    void createShortUrl_WithCustomAlias_ShouldReturnUrlMapping() {
        // Given
        String longUrl = "https://www.example.com";
        String customAlias = "my-custom-link";
        String creatorIp = "192.168.1.1";
        Instant expiresAt = Instant.now().plus(30, ChronoUnit.DAYS);

        UrlMapping expectedMapping = UrlMapping.builder()
                .alias(customAlias)
                .longUrl(longUrl)
                .createdAt(Instant.now())
                .createdByIp(creatorIp)
                .customAlias(true)
                .active(true)
                .expiresAt(expiresAt)
                .build();

        when(urlMappingRepository.existsByAlias(customAlias)).thenReturn(false);
        when(urlMappingRepository.save(any(UrlMapping.class))).thenReturn(expectedMapping);

        // When
        UrlMapping result = urlService.createShortUrl(longUrl, customAlias, creatorIp, expiresAt);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getAlias()).isEqualTo(customAlias);
        assertThat(result.getLongUrl()).isEqualTo(longUrl);
        assertThat(result.getCreatedByIp()).isEqualTo(creatorIp);
        assertThat(result.isCustomAlias()).isTrue();
        assertThat(result.isActive()).isTrue();
        
        verify(urlMappingRepository).existsByAlias(customAlias);
        verify(urlMappingRepository).save(any(UrlMapping.class));
    }

    @Test
    @DisplayName("Should throw exception when custom alias already exists")
    void createShortUrl_WithExistingCustomAlias_ShouldThrowException() {
        // Given
        String longUrl = "https://www.example.com";
        String customAlias = "existing-alias";
        String creatorIp = "192.168.1.1";

        when(urlMappingRepository.existsByAlias(customAlias)).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> urlService.createShortUrl(longUrl, customAlias, creatorIp, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Custom alias already used");

        verify(urlMappingRepository).existsByAlias(customAlias);
        verify(urlMappingRepository, never()).save(any(UrlMapping.class));
    }

    @Test
    @DisplayName("Should create short URL with generated alias when no custom alias provided")
    void createShortUrl_WithoutCustomAlias_ShouldReturnUrlMappingWithGeneratedAlias() {
        // Given
        String longUrl = "https://www.example.com";
        String creatorIp = "192.168.1.1";

        UrlMapping expectedMapping = UrlMapping.builder()
                .alias("abc12")
                .longUrl(longUrl)
                .createdAt(Instant.now())
                .createdByIp(creatorIp)
                .customAlias(false)
                .active(true)
                .build();

        when(urlMappingRepository.save(any(UrlMapping.class))).thenReturn(expectedMapping);

        // When
        UrlMapping result = urlService.createShortUrl(longUrl, null, creatorIp, null);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getAlias()).isNotNull();
        assertThat(result.getLongUrl()).isEqualTo(longUrl);
        assertThat(result.getCreatedByIp()).isEqualTo(creatorIp);
        assertThat(result.isCustomAlias()).isFalse();
        assertThat(result.isActive()).isTrue();
        
        verify(urlMappingRepository).save(any(UrlMapping.class));
        verify(urlMappingRepository, never()).existsByAlias(anyString());
    }

    @Test
    @DisplayName("Should create short URL with blank custom alias as generated alias")
    void createShortUrl_WithBlankCustomAlias_ShouldTreatAsNoCustomAlias() {
        // Given
        String longUrl = "https://www.example.com";
        String blankAlias = "   ";
        String creatorIp = "192.168.1.1";

        UrlMapping expectedMapping = UrlMapping.builder()
                .alias("xyz89")
                .longUrl(longUrl)
                .createdAt(Instant.now())
                .createdByIp(creatorIp)
                .customAlias(false)
                .active(true)
                .build();

        when(urlMappingRepository.save(any(UrlMapping.class))).thenReturn(expectedMapping);

        // When
        UrlMapping result = urlService.createShortUrl(longUrl, blankAlias, creatorIp, null);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getAlias()).isNotNull();
        assertThat(result.getLongUrl()).isEqualTo(longUrl);
        assertThat(result.isCustomAlias()).isFalse();
        
        verify(urlMappingRepository).save(any(UrlMapping.class));
        verify(urlMappingRepository, never()).existsByAlias(anyString());
    }

    @Test
    @DisplayName("Should use default expiration when no expiration provided")
    void createShortUrl_WithoutExpiresAt_ShouldUseDefaultExpiration() {
        // Given
        String longUrl = "https://www.example.com";
        String customAlias = "test-alias";
        String creatorIp = "192.168.1.1";

        when(urlMappingRepository.existsByAlias(customAlias)).thenReturn(false);
        when(urlMappingRepository.save(any(UrlMapping.class))).thenAnswer(invocation -> {
            UrlMapping mapping = invocation.getArgument(0);
            // Verify that expiration is set to default days from now
            Instant expectedExpiration = Instant.now().plus(defaultExpirationDays, ChronoUnit.DAYS);
            assertThat(mapping.getExpiresAt()).isCloseTo(expectedExpiration, within(1, ChronoUnit.SECONDS));
            return mapping;
        });

        // When
        UrlMapping result = urlService.createShortUrl(longUrl, customAlias, creatorIp, null);

        // Then
        verify(urlMappingRepository).save(any(UrlMapping.class));
    }

    @Test
    @DisplayName("Should find URL mapping by alias successfully")
    void findByAlias_WithExistingAlias_ShouldReturnUrlMapping() {
        // Given
        String alias = "test-alias";
        UrlMapping expectedMapping = UrlMapping.builder()
                .alias(alias)
                .longUrl("https://www.example.com")
                .active(true)
                .build();

        when(urlMappingRepository.findByAlias(alias)).thenReturn(Optional.of(expectedMapping));

        // When
        Optional<UrlMapping> result = urlService.findByAlias(alias);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getAlias()).isEqualTo(alias);
        assertThat(result.get().getLongUrl()).isEqualTo("https://www.example.com");
        
        verify(urlMappingRepository).findByAlias(alias);
    }

    @Test
    @DisplayName("Should return empty when alias does not exist")
    void findByAlias_WithNonExistentAlias_ShouldReturnEmpty() {
        // Given
        String alias = "non-existent";

        when(urlMappingRepository.findByAlias(alias)).thenReturn(Optional.empty());

        // When
        Optional<UrlMapping> result = urlService.findByAlias(alias);

        // Then
        assertThat(result).isEmpty();
        
        verify(urlMappingRepository).findByAlias(alias);
    }

    @Test
    @DisplayName("Should record click event successfully")
    void recordClick_WithValidData_ShouldSaveClickEvent() {
        // Given
        String alias = "test-alias";
        String ip = "192.168.1.1";
        String userAgent = "Mozilla/5.0";
        String referrer = "https://google.com";

        when(clickEventRepository.save(any(ClickEvent.class))).thenAnswer(invocation -> {
            ClickEvent clickEvent = invocation.getArgument(0);
            assertThat(clickEvent.getAlias()).isEqualTo(alias);
            assertThat(clickEvent.getIp()).isEqualTo(ip);
            assertThat(clickEvent.getUserAgent()).isEqualTo(userAgent);
            assertThat(clickEvent.getClickedAt()).isCloseTo(Instant.now(), within(1, ChronoUnit.SECONDS));
            return clickEvent;
        });

        // When
        urlService.recordClick(alias, ip, userAgent, referrer);

        // Then
        verify(clickEventRepository).save(any(ClickEvent.class));
    }

    @Test
    @DisplayName("Should record click event with null values")
    void recordClick_WithNullValues_ShouldSaveClickEvent() {
        // Given
        String alias = "test-alias";
        String ip = null;
        String userAgent = null;
        String referrer = null;

        when(clickEventRepository.save(any(ClickEvent.class))).thenAnswer(invocation -> {
            ClickEvent clickEvent = invocation.getArgument(0);
            assertThat(clickEvent.getAlias()).isEqualTo(alias);
            assertThat(clickEvent.getIp()).isNull();
            assertThat(clickEvent.getUserAgent()).isNull();
            return clickEvent;
        });

        // When
        urlService.recordClick(alias, ip, userAgent, referrer);

        // Then
        verify(clickEventRepository).save(any(ClickEvent.class));
    }

    @Test
    @DisplayName("Should get click count for alias")
    void getClickCount_WithExistingAlias_ShouldReturnCount() {
        // Given
        String alias = "test-alias";
        long expectedCount = 42L;

        when(clickEventRepository.countByAlias(alias)).thenReturn(expectedCount);

        // When
        long result = urlService.getClickCount(alias);

        // Then
        assertThat(result).isEqualTo(expectedCount);
        
        verify(clickEventRepository).countByAlias(alias);
    }

    @Test
    @DisplayName("Should return zero count for alias with no clicks")
    void getClickCount_WithNoClicks_ShouldReturnZero() {
        // Given
        String alias = "no-clicks-alias";
        long expectedCount = 0L;

        when(clickEventRepository.countByAlias(alias)).thenReturn(expectedCount);

        // When
        long result = urlService.getClickCount(alias);

        // Then
        assertThat(result).isEqualTo(0L);
        
        verify(clickEventRepository).countByAlias(alias);
    }
}
