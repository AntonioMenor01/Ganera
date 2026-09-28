package com.ganera.core.explotacion;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExplotacionRepository extends JpaRepository<Explotacion, Long> {
    long countByGestoriaId(Long gestoriaId);

    /** Listado paginado (GET /explotaciones) con gestoriaId explicito, sin depender del filtro ambiente. */
    Page<Explotacion> findByGestoriaId(Long gestoriaId, Pageable pageable);

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
