package com.ganera.core.tramite;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TramiteRepository extends JpaRepository<Tramite, Long> {
    /** Listado paginado (GET /tramites) con gestoriaId explicito, sin depender del filtro ambiente. */
    Page<Tramite> findByGestoriaId(Long gestoriaId, Pageable pageable);

    /** Listado paginado filtrado por estado, con gestoriaId explicito. */
    Page<Tramite> findByGestoriaIdAndEstado(Long gestoriaId, EstadoTramite estado, Pageable pageable);

    /**
     * Usar SIEMPRE esta en vez de findById(id) a secas cuando el id venga de un path variable
     * (potencialmente de cualquier Gestoria): Hibernate NO aplica gestoriaFilter a
     * EntityManager.find()/findById() por clave primaria -- el filtro solo se aplica a queries
     * derivadas/HQL de resultado (findAll, esta misma). Sin el gestoriaId explicito
     * aqui, un id de otra Gestoria se resuelve igual (encontrado en auditoria post-mortem del
     * bug de WebMvcTenantConfig, 2026-07-14 -- ver TenantIsolationEndToEndTest).
     */
    Optional<Tramite> findByIdAndGestoriaId(Long id, Long gestoriaId);

    /**
     * Igual que findByIdAndGestoriaId pero con bloqueo de fila (SELECT ... FOR UPDATE) hasta el
     * final de la transaccion: dos ediciones/aprobaciones simultaneas del mismo Tramite se
     * serializan en vez de pisarse (decision 26). Solo para TramiteRevisionService, que abre la
     * transaccion. gestoriaId es un parametro real de la query, no el filtro ambiente.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Tramite t where t.id = :id and t.gestoria.id = :gestoriaId")
    Optional<Tramite> findConBloqueoByIdAndGestoriaId(@Param("id") Long id, @Param("gestoriaId") Long gestoriaId);
}
