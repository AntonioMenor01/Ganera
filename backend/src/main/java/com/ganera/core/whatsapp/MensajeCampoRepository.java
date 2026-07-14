package com.ganera.core.whatsapp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MensajeCampoRepository extends JpaRepository<MensajeCampo, Long> {
    boolean existsByMessageSid(String messageSid);

    Optional<MensajeCampo> findFirstByTramiteIdOrderByCreatedAtDesc(Long tramiteId);
}
