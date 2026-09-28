package com.ganera.core.ganadero;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GanaderoRepository extends JpaRepository<Ganadero, Long> {
    /** Upsert del importador por NIF (UNIQUE global), con gestoriaId explicito: vacio tanto si no
     * existe como si es de otra Gestoria -- no depende del gestoriaFilter ambiente. */
    Optional<Ganadero> findByNifAndGestoriaId(String nif, Long gestoriaId);

    /** Busqueda por id con gestoriaId explicito: NUNCA findById(id) a secas desde un endpoint --
     * Hibernate no aplica gestoriaFilter a una carga por clave primaria (ver CLAUDE.md). */
    Optional<Ganadero> findByIdAndGestoriaId(Long id, Long gestoriaId);

    /** Listado paginado con gestoriaId explicito, sin depender del gestoriaFilter ambiente. */
    Page<Ganadero> findByGestoriaId(Long gestoriaId, Pageable pageable);
}
