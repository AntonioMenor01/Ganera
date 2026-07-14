package com.ganera.core.explotacion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ExplotacionRepository extends JpaRepository<Explotacion, Long> {
    long countByGestoriaId(Long gestoriaId);

    Optional<Explotacion> findByCodigoRega(String codigoRega);
}
