package com.project.ait.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;

@Data
@Schema(description = "Request payload for URL shortening")
public class ShortenRequest {
    
    @Schema(description = "The original long URL to be shortened", example = "https://www.example.com/very/long/url", required = true)
    private String longUrl;
    
    @Schema(description = "Optional custom alias for the short URL (if not provided, one will be generated)", example = "my-custom-alias")
    private String customAlias;
    
    @Schema(description = "Optional expiration date/time for the URL (ISO format)", example = "2024-12-31T23:59:59Z")
    private Instant expiresAt;
}
