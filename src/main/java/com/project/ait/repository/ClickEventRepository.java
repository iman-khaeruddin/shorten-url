package com.project.ait.repository;

import com.project.ait.entity.ClickEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {
    List<ClickEvent> findByAliasOrderByClickedAtDesc(String alias);
    long countByAlias(String alias);
}