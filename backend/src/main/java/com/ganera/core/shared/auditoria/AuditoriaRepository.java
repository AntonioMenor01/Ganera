package com.ganera.core.shared.auditoria;

import org.springframework.data.repository.Repository;

import java.util.List;

/**
 * Solo insercion y lectura con el gestoriaId EXPLICITO (plan 2026-10-21). A proposito NO extiende
 * {@code JpaRepository}/{@code CrudRepository}: no hay {@code findById}, {@code delete*} ni {@code @Modifying} que
 * llamar (lo vigila {@code AuditoriaRepositoryTest}).
 */
public interface AuditoriaRepository extends Repository<Auditoria, Long> {

    Auditoria save(Auditoria auditoria);

    /** Para los tests: las filas de una gestoria en orden de insercion. */
    List<Auditoria> findByGestoriaIdOrderByIdAsc(Long gestoriaId);
}
