package com.ganera.core.tramite;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TramiteRepository extends JpaRepository<Tramite, Long> {
    Page<Tramite> findByEstado(EstadoTramite estado, Pageable pageable);
}
