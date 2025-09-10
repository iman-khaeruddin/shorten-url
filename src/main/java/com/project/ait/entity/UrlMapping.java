package com.project.ait.entity;

import lombok.*;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "url_mapping", indexes = {@Index(columnList = "alias", unique = true)})
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class UrlMapping implements Serializable {
    private static final long serialVersionUID = 1L;
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(nullable=false, unique=true)
    private String alias;

    @Column(nullable=false, length=2048)
    private String longUrl;

    private String createdByIp;
    private Instant createdAt;
    private Instant expiresAt;
    @Builder.Default
    private boolean active = true;

    @Builder.Default
    private boolean customAlias = false;
}
