package com.project.ait.entity;

import lombok.*;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="click_event", indexes = {@Index(columnList = "alias")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClickEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String alias;
    private Instant clickedAt;
    private String ip;
    private String userAgent;
}