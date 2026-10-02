package com.ganera.core.explotacion;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExplotacionRepository extends JpaRepository<Explotacion, Long> {
    long countByGestoriaId(Long gestoriaId);

    /**
     * Listado paginado (GET /explotaciones) con gestoriaId explicito, sin depender del filtro ambiente.
     * El Ganadero (LAZY) se trae en la misma consulta (EntityGraph) porque ExplotacionResponse lee su
     * nombre: nunca una consulta por Ganadero distinto de la pagina (paginas de 500 desde el frontend).
     */
    @EntityGraph(attributePaths = "ganadero")
    Page<Explotacion> findByGestoriaId(Long gestoriaId, Pageable pageable);

    /**
     * Busqueda de GET /explotaciones?q=: "contiene", sin distinguir mayusculas, sobre el codigo REGA
     * y el nombre de la Explotacion y el nombre de su Ganadero, con gestoriaId explicito (no depende
     * del filtro ambiente). {@code patron} ya llega con los comodines de LIKE ({@code %}, {@code _})
     * y el caracter de escape ({@code !}) del usuario escapados con {@code !} y envuelto en
     * {@code %...%} (ver ExplotacionController.patronContiene). Se usa {@code !} y no {@code \} como
     * caracter de escape para no depender de como tratan la barra invertida el lexer de HQL y los
     * literales de cada base de datos. El Ganadero se trae en la misma consulta (join fetch, ManyToOne:
     * sin problema con la paginacion), asi ExplotacionResponse no dispara una consulta por Ganadero.
     * El countQuery repite el filtro sin fetch. El Sort del Pageable se aplica sobre el alias raiz
     * {@code e} (codigoRega/nombre/id de la Explotacion, nunca del Ganadero).
     */
    @Query(value = "select e from Explotacion e join fetch e.ganadero g where e.gestoria.id = :gestoriaId and ("
            + "lower(e.codigoRega) like lower(:patron) escape '!' "
            + "or lower(e.nombre) like lower(:patron) escape '!' "
            + "or lower(g.nombre) like lower(:patron) escape '!')",
            countQuery = "select count(e) from Explotacion e join e.ganadero g where e.gestoria.id = :gestoriaId and ("
                    + "lower(e.codigoRega) like lower(:patron) escape '!' "
                    + "or lower(e.nombre) like lower(:patron) escape '!' "
                    + "or lower(g.nombre) like lower(:patron) escape '!')")
    Page<Explotacion> buscarPorTexto(
            @Param("gestoriaId") Long gestoriaId, @Param("patron") String patron, Pageable pageable);

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
