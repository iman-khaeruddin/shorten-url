package com.project.ait.repository;

import com.project.ait.entity.UrlMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UrlMappingRepository extends JpaRepository<UrlMapping, Long> {
    Optional<UrlMapping> findByAlias(String alias);
    boolean existsByAlias(String alias);
}