package com.ganera.core.explotacion;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AnimalRepository extends JpaRepository<Animal, Long> {
    /** Upsert del importador por crotal (UNIQUE global), con gestoriaId explicito: vacio tanto si
     * no existe como si es de otra Gestoria -- no depende del gestoriaFilter ambiente. */
    Optional<Animal> findByCrotalAndGestoriaId(String crotal, Long gestoriaId);

    /** Animales de una Explotacion (GET /explotaciones/{id}/animales), con gestoriaId explicito. */
    Page<Animal> findByExplotacionIdAndGestoriaId(Long explotacionId, Long gestoriaId, Pageable pageable);

    /** Crotal completo de un Tramite (TramiteCrotalService): igualdad exacta, solo dentro de la
     * Explotacion del Tramite y de la Gestoria autenticada (gestoriaId explicito). */
    Optional<Animal> findByExplotacionIdAndGestoriaIdAndCrotal(Long explotacionId, Long gestoriaId, String crotal);

    /** Crotal incompleto (ultimos digitos) de un Tramite: sufijo, solo dentro de la Explotacion del
     * Tramite y de la Gestoria autenticada. Orden por id para que el resultado sea determinista. */
    List<Animal> findByExplotacionIdAndGestoriaIdAndCrotalEndingWithOrderByIdAsc(
            Long explotacionId, Long gestoriaId, String sufijo);

    /**
     * Buscador global: animales de la gestoria (gestoriaId explicito) cuyo crotal CONTIENE el
     * patron (en mayusculas, un LIKE '%token%' ya escapado). Devuelve directamente un DTO con la
     * explotacion y el ganadero (join), nunca la entidad: una sola consulta, sin N+1 ni exponer de
     * mas. Orden por crotal para que sea determinista; el limite llega en el Pageable.
     */
    @Query("select new com.ganera.core.explotacion.AnimalResultado(a.crotal, e.codigoRega, g.id, g.nombre) "
            + "from Animal a join a.explotacion e join e.ganadero g "
            + "where a.gestoria.id = :gestoriaId and upper(a.crotal) like :patron escape '!' "
            + "order by a.crotal asc")
    List<AnimalResultado> buscarPorCrotal(@Param("gestoriaId") Long gestoriaId,
                                          @Param("patron") String patron, Pageable limite);
}
