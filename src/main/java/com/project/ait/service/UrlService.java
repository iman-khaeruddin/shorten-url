package com.project.ait.service;

import com.project.ait.entity.ClickEvent;
import com.project.ait.entity.UrlMapping;
import com.project.ait.repository.ClickEventRepository;
import com.project.ait.repository.UrlMappingRepository;
import com.project.ait.util.Base62;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
public class UrlService {
    private final UrlMappingRepository urlMappingRepository;
    private final ClickEventRepository clickEventRepository;
    private final String baseUrl;
    private final int defaultExpirationDays;

    public UrlService(UrlMappingRepository urlMappingRepository,
                      ClickEventRepository clickEventRepository,
                      @Value("${app.base-url}") String baseUrl,
                      @Value("${app.default-expiration-days}") int defaultExpirationDays) {
        this.urlMappingRepository = urlMappingRepository;
        this.clickEventRepository = clickEventRepository;
        this.baseUrl = baseUrl;
        this.defaultExpirationDays = defaultExpirationDays;
    }

    @Transactional
    @CacheEvict(value = "alias", key = "#result.alias", condition = "#result != null")
    public UrlMapping createShortUrl(String longUrl, String customAlias, String creatorIp, Instant expiresAtRequested) {
        if (customAlias != null && !customAlias.isBlank()) {
            if (urlMappingRepository.existsByAlias(customAlias)) {
                throw new IllegalArgumentException("Custom alias already used");
            }
            UrlMapping mapping = UrlMapping.builder()
                    .alias(customAlias)
                    .longUrl(longUrl)
                    .createdAt(Instant.now())
                    .createdByIp(creatorIp)
                    .customAlias(true)
                    .active(true)
                    .expiresAt(expiresAtRequested == null ? Instant.now().plus(defaultExpirationDays, ChronoUnit.DAYS) : expiresAtRequested)
                    .build();
            return urlMappingRepository.save(mapping);
        }

        // Save url mapping
        UrlMapping saved = UrlMapping.builder()
                .longUrl(longUrl)
                .createdAt(Instant.now())
                .createdByIp(creatorIp)
                .active(true)
                .alias(Base62.encode(5))
                .expiresAt(expiresAtRequested == null ? Instant.now().plus(defaultExpirationDays, ChronoUnit.DAYS) : expiresAtRequested)
                .build();
        return urlMappingRepository.save(saved);
    }

    @Cacheable(value = "alias", key = "#alias")
    public Optional<UrlMapping> findByAlias(String alias) {
        return urlMappingRepository.findByAlias(alias);
    }

    public void recordClick(String alias, String ip, String ua, String referrer) {
        ClickEvent e = ClickEvent.builder()
                .alias(alias)
                .clickedAt(Instant.now())
                .ip(ip)
                .userAgent(ua)
                .build();
        clickEventRepository.save(e);
    }

    public long getClickCount(String alias) {
        return clickEventRepository.countByAlias(alias);
    }
}