package com.ganera.core.tramite;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Historial de descarte (A6). Toda consulta lleva el gestoriaId EXPLICITO; nunca findById a secas. */
public interface TramiteEventoRepository extends JpaRepository<TramiteEvento, Long> {

    /** Eventos de un Tramite en orden cronologico, con su usuario en la MISMA consulta (sin N+1). */
    @EntityGraph(attributePaths = "usuario")
    List<TramiteEvento> findByTramiteIdAndGestoriaIdOrderByCreatedAtAscIdAsc(Long tramiteId, Long gestoriaId);
}
