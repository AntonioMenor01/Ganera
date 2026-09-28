package com.ganera.core.explotacion;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
