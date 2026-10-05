package com.ganera.core.tramite;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TramiteRepository extends JpaRepository<Tramite, Long> {
    /**
     * Listado paginado (GET /tramites) con gestoriaId explicito, sin depender del filtro ambiente.
     * La Explotacion (LAZY) se trae en la misma consulta con un left join (EntityGraph; mini-prompt
     * tras A2, punto 5) para el REGA/nombre de cada fila: nunca una consulta por fila. Spring Data
     * no aplica el EntityGraph a la consulta de conteo de la paginacion.
     */
    @EntityGraph(attributePaths = "explotacion")
    Page<Tramite> findByGestoriaId(Long gestoriaId, Pageable pageable);

    /** Listado paginado filtrado por estado, con gestoriaId explicito. Explotacion en la misma
     * consulta, como findByGestoriaId. */
    @EntityGraph(attributePaths = "explotacion")
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

    /**
     * Cola de la extraccion en segundo plano (B1, T2): Tramites con estadoExtraccion en
     * {@code estados} cuyo proximo intento ya ha llegado, del mas antiguo al mas reciente.
     *
     * <p>Consulta deliberadamente SIN gestoriaId: es la del planificador, un job cross-tenant sin
     * peticion (como SuscripcionSyncScheduler) que necesita ver la cola de todas las Gestorias. Solo
     * devuelve (tramiteId, gestoriaId), y todo lo que se hace despues con cada Tramite lleva ese
     * gestoriaId explicito. Ningun endpoint autenticado debe usarla.
     *
     * <p>{@code ahora} es SIEMPRE un parametro (el Instant del Clock), nunca now()/current_timestamp
     * de SQL: la columna es TIMESTAMP sin zona y se compara Instant con Instant (revision T1, n3);
     * ademas, asi los tests fijan "ahora" sin esperas.
     */
    @Query("select new com.ganera.core.tramite.TramitePendienteExtraccion(t.id, t.gestoria.id) from Tramite t "
            + "where t.estadoExtraccion in :estados and t.proximoIntentoExtraccion is not null "
            + "and t.proximoIntentoExtraccion <= :ahora "
            + "order by t.proximoIntentoExtraccion asc, t.id asc")
    List<TramitePendienteExtraccion> findPendientesDeExtraccion(@Param("estados") Collection<EstadoExtraccion> estados,
                                                                 @Param("ahora") Instant ahora,
                                                                 Pageable pagina);

    /**
     * Contabilidad de un intento de extraccion fallido SIN tocar la version (B1, T2): suma un
     * intento y fija el siguiente (null = no hay mas). Un UPDATE JPQL "a secas" (sin "versioned")
     * no incrementa @Version, a proposito: el Tramite ya esta en FALLIDA y su version es la que ve
     * el empleado en el modal (y versionTrasFallo); cambiarla daria 409 falsos al guardar y
     * haria que un reintento que acierte se descartase por "editado". Solo los cambios visibles
     * (estado, tipo, crotales, estadoExtraccion) cambian la version. gestoriaId explicito.
     *
     * <p>clearAutomatically (revision n1): el Tramite ya cargado en esta transaccion se queda con
     * intentos/proximo viejos tras el UPDATE. Hoy nadie lo vuelve a tocar, pero si alguien le
     * pusiera un setter despues, el flush del commit escribiria la fila entera con esos valores
     * viejos y subiria la version. Desprendiendolo, ese setter no se escribe nunca. (Antes se
     * vuelca lo pendiente con flushAutomatically, asi que limpiar no pierde nada.)
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Tramite t set t.intentosExtraccion = t.intentosExtraccion + 1, "
            + "t.proximoIntentoExtraccion = :proximo where t.id = :id and t.gestoria.id = :gestoriaId")
    int registrarIntentoFallidoSinCambiarVersion(@Param("id") Long id, @Param("gestoriaId") Long gestoriaId,
                                                 @Param("proximo") Instant proximo);

    /**
     * Deja de reintentar la extraccion (proximo intento = null) SIN tocar la version ni nada
     * visible: el Tramite ya no procede (lo edito, aprobo o rechazo alguien). Mismo motivo que
     * registrarIntentoFallidoSinCambiarVersion para no incrementar la version. gestoriaId explicito.
     * clearAutomatically por el mismo motivo que alli.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Tramite t set t.proximoIntentoExtraccion = null where t.id = :id and t.gestoria.id = :gestoriaId")
    int dejarDeReintentarExtraccion(@Param("id") Long id, @Param("gestoriaId") Long gestoriaId);
}
