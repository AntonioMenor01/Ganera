package com.ganera.core.facturacion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SuscripcionRepository extends JpaRepository<Suscripcion, Long> {
    Optional<Suscripcion> findByGestoriaId(Long gestoriaId);
}
