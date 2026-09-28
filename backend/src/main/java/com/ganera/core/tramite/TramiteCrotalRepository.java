package com.ganera.core.tramite;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/** Todos los finders llevan gestoriaId explicito: no dependen del gestoriaFilter ambiente. */
public interface TramiteCrotalRepository extends JpaRepository<TramiteCrotal, Long> {

    /** Crotales de un Tramite, en el orden en que se indicaron. */
    List<TramiteCrotal> findByTramiteIdAndGestoriaIdOrderByIdAsc(Long tramiteId, Long gestoriaId);

    /** Carga en lote de los crotales de una pagina de Tramites (una sola consulta, sin N+1). */
    List<TramiteCrotal> findByTramiteIdInAndGestoriaIdOrderByIdAsc(Collection<Long> tramiteIds, Long gestoriaId);
}
