package com.project.ait.controller;

import com.project.ait.dto.ShortenRequest;
import com.project.ait.dto.ShortenResponse;
import com.project.ait.entity.UrlMapping;
import com.project.ait.service.UrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import java.util.Map;

@RestController
@Tag(name = "URL Shortener", description = "Operations for URL shortening and redirection")
public class UrlController {
    private final UrlService urlService;

    public UrlController(UrlService urlService) { this.urlService = urlService; }

    @Operation(summary = "Shorten a URL", description = "Create a short URL from a long URL with optional custom alias")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "URL successfully shortened"),
            @ApiResponse(responseCode = "400", description = "Invalid request or custom alias already exists")
    })
    @PostMapping("/api/shorten")
    public ResponseEntity<ShortenResponse> shorten(@RequestBody ShortenRequest req, HttpServletRequest servletReq) {
        String ip = servletReq.getRemoteAddr();
        Instant expires = req.getExpiresAt() == null ? null : req.getExpiresAt();
        UrlMapping mapping = urlService.createShortUrl(req.getLongUrl(), req.getCustomAlias(), ip, expires);
        String base = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        return ResponseEntity.ok(new ShortenResponse(base + "/" + mapping.getAlias(), mapping.getAlias(), mapping.getLongUrl()));
    }

    @Operation(summary = "Redirect to original URL", description = "Redirect using the short URL alias to the original URL")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "302", description = "Successfully redirected to original URL"),
            @ApiResponse(responseCode = "404", description = "Alias not found"),
            @ApiResponse(responseCode = "410", description = "URL expired or inactive")
    })
    @GetMapping("/{alias}")
    public ResponseEntity<?> redirect(@Parameter(description = "The short URL alias") @PathVariable String alias, HttpServletRequest request) {
        var opt = urlService.findByAlias(alias);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        UrlMapping m = opt.get();
        if (!m.isActive() || (m.getExpiresAt() != null && Instant.now().isAfter(m.getExpiresAt()))) {
            return ResponseEntity.status(410).body("URL expired or inactive");
        }
        // record click
        String ip = request.getRemoteAddr();
        String ua = request.getHeader("User-Agent");
        String ref = request.getHeader("Referer");
        urlService.recordClick(alias, ip, ua, ref);
        URI uri = URI.create(m.getLongUrl());
        return ResponseEntity.status(302).location(uri).build();
    }

    @Operation(summary = "Get URL information", description = "Get detailed information about a shortened URL")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "URL information retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Alias not found")
    })
    @GetMapping("/api/info/{alias}")
    public ResponseEntity<?> info(@Parameter(description = "The short URL alias") @PathVariable String alias) {
        var opt = urlService.findByAlias(alias);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        UrlMapping m = opt.get();
        long clicks = urlService.getClickCount(alias);
        return ResponseEntity.ok(Map.of(
                "alias", m.getAlias(),
                "longUrl", m.getLongUrl(),
                "createdAt", m.getCreatedAt(),
                "expiresAt", m.getExpiresAt(),
                "clicks", clicks,
                "custom", m.isCustomAlias()
        ));
    }

    // analytics endpoints (simple)
    @Operation(summary = "Get click analytics", description = "Get click count analytics for a shortened URL")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Click analytics retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Alias not found")
    })
    @GetMapping("/api/analytics/{alias}/clicks")
    public ResponseEntity<?> clicks(@Parameter(description = "The short URL alias") @PathVariable String alias) {
        // For demo: return click count and list limited.
        var opt = urlService.findByAlias(alias);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        long total = urlService.getClickCount(alias);
        return ResponseEntity.ok(Map.of("alias", alias, "totalClicks", total));
    }
}