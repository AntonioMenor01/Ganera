package com.ganera.core.tramite;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.QueryHint;
import java.util.List;
import java.util.Optional;

/**
 * Cola de presentación automática en OVZ (escalón 2, T2). Toda consulta de un endpoint lleva el {@code gestoriaId}
 * EXPLÍCITO (nunca {@code findById} a secas). La única consulta sin tenant es {@link #siguientesParaReclamar}, la del
 * worker (cross-tenant por diseño, como {@code TramiteRepository.findPendientesDeExtraccion}): ningún endpoint
 * autenticado debe usarla.
 */
public interface TrabajoOvzRepository extends JpaRepository<TrabajoOvz, Long> {

    /** El trabajo de un trámite (hay como mucho uno, UNIQUE(tramite_id)), con el gestoriaId explícito. */
    Optional<TrabajoOvz> findByTramiteIdAndGestoriaId(Long tramiteId, Long gestoriaId);

    /** ¿Existe ya un trabajo para este trámite? (El UNIQUE es la garantía real; esto es para tests/legibilidad.) */
    boolean existsByTramiteId(Long tramiteId);

    /**
     * «Reclamar trabajo» del worker (D3): los trabajos {@code PENDIENTE} más antiguos primero, con bloqueo de fila
     * {@code FOR UPDATE SKIP LOCKED} en PostgreSQL (el hint de timeout {@code -2} = SKIP_LOCKED). En H2 el dialecto
     * no soporta SKIP LOCKED y Hibernate degrada a {@code FOR UPDATE} a secas (alternativa para los tests), lo que
     * basta porque el worker es único y serializado (D3: un solo trabajo global a la vez). Normalmente se pide
     * {@code Pageable} de tamaño 1.
     *
     * <p>Consulta deliberadamente SIN {@code gestoriaId}: es la del worker, un proceso cross-tenant sin petición
     * (como {@code TramiteRepository.findPendientesDeExtraccion}) que ve la cola de todas las gestorías; todo lo que
     * se hace después con cada trabajo lleva su {@code gestoriaId} explícito.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select t from TrabajoOvz t where t.estado = :pendiente order by t.createdAt asc, t.id asc")
    List<TrabajoOvz> siguientesParaReclamar(@Param("pendiente") EstadoTrabajoOvz pendiente, Pageable pagina);
}
