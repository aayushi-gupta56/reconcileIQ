package com.reconcileiq.config;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppMetadataRepository extends JpaRepository<AppMetadata, Long> {

    Optional<AppMetadata> findByAppKey(String appKey);
}
