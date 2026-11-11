package com.ganera.core.ovz;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PreferenciaOvzRepository extends JpaRepository<PreferenciaOvz, Long> {

    Optional<PreferenciaOvz> findByGestoriaId(Long gestoriaId);
}
