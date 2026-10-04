package com.ganera.core.explotacion;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * JpaSpecificationExecutor: toda Specification sobre Explotacion debe llevar el gestoria.id del JWT
 * como predicado explicito (como ExplotacionBusquedaSpecification.porPalabras), porque los
 * findAll/count/findOne/findBy heredados no filtran por Gestoria por si mismos ni llevan EntityGraph
 * (salvo el findAll(Specification, Pageable) redeclarado abajo).
 */
public interface ExplotacionRepository
        extends JpaRepository<Explotacion, Long>, JpaSpecificationExecutor<Explotacion> {
    long countByGestoriaId(Long gestoriaId);

    /**
     * Listado paginado (GET /explotaciones) con gestoriaId explicito, sin depender del filtro ambiente.
     * El Ganadero (LAZY) se trae en la misma consulta (EntityGraph) porque ExplotacionResponse lee su
     * nombre: nunca una consulta por Ganadero distinto de la pagina (paginas de 500 desde el frontend).
     */
    @EntityGraph(attributePaths = "ganadero")
    Page<Explotacion> findByGestoriaId(Long gestoriaId, Pageable pageable);

    /**
     * Busqueda de GET /explotaciones?q= con ExplotacionBusquedaSpecification.porPalabras (el
     * gestoriaId va explicito dentro de la spec). El Ganadero (LAZY) se trae en la consulta de la
     * pagina con este EntityGraph, que Spring Data aplica solo a la consulta de datos, nunca al
     * count. Por eso la Specification no hace fetch: Spring Data usa la misma spec para el count, y
     * un fetch ahi ("select count(e) ... join fetch") falla porque el propietario del fetch no esta
     * en la seleccion. La spec llega al Ganadero por path y Hibernate reutiliza para el predicado el
     * join del EntityGraph: una sola union con ganadero en cada consulta. El Sort se aplica a la raiz.
     */
    @Override
    @EntityGraph(attributePaths = "ganadero")
    Page<Explotacion> findAll(Specification<Explotacion> spec, Pageable pageable);

    /** Busqueda por id con gestoriaId explicito: NUNCA findById(id) a secas desde un endpoint --
     * Hibernate no aplica gestoriaFilter a una carga por clave primaria (ver CLAUDE.md). */
    Optional<Explotacion> findByIdAndGestoriaId(Long id, Long gestoriaId);

    /** Busqueda por codigo_rega con gestoriaId explicito (hoja Contactos del importador): no
     * depende del gestoriaFilter ambiente. Vacio tanto si no existe como si es de otra Gestoria. */
    Optional<Explotacion> findByCodigoRegaAndGestoriaId(String codigoRega, Long gestoriaId);

    /** Explotaciones de un Ganadero (detalle de Ganadero), con gestoriaId explicito y orden estable. */
    List<Explotacion> findByGanaderoIdAndGestoriaIdOrderByCodigoRegaAsc(Long ganaderoId, Long gestoriaId);

    /** Numero de Explotaciones de cada Ganadero de una pagina, en UNA consulta agrupada (evita N+1
     * en GET /ganaderos). Un Ganadero sin Explotaciones no aparece en el resultado (cuenta 0). */
    @Query("select new com.ganera.core.explotacion.ConteoExplotacionesPorGanadero(e.ganadero.id, count(e)) "
            + "from Explotacion e where e.gestoria.id = :gestoriaId and e.ganadero.id in :ganaderoIds "
            + "group by e.ganadero.id")
    List<ConteoExplotacionesPorGanadero> contarPorGanadero(
            @Param("gestoriaId") Long gestoriaId, @Param("ganaderoIds") Collection<Long> ganaderoIds);
}
