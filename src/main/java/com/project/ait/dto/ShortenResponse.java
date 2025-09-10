package com.project.ait.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Schema(description = "Response payload containing the shortened URL details")
public class ShortenResponse {
    
    @Schema(description = "The complete short URL", example = "http://localhost:8080/abc123")
    private String shortUrl;
    
    @Schema(description = "The alias/identifier for the short URL", example = "abc123")
    private String alias;
    
    @Schema(description = "The original long URL", example = "https://www.example.com/very/long/url")
    private String longUrl;
}
