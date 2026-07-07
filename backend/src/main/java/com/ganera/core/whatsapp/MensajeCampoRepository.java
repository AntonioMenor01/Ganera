package com.ganera.core.whatsapp;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MensajeCampoRepository extends JpaRepository<MensajeCampo, Long> {
    boolean existsByMessageSid(String messageSid);
}
