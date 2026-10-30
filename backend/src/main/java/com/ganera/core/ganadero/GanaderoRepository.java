package com.ganera.core.ganadero;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GanaderoRepository extends JpaRepository<Ganadero, Long> {
    /** Upsert del importador por NIF (UNIQUE global), con gestoriaId explicito: vacio tanto si no
     * existe como si es de otra Gestoria -- no depende del gestoriaFilter ambiente. */
    Optional<Ganadero> findByNifAndGestoriaId(String nif, Long gestoriaId);

    /** Busqueda por id con gestoriaId explicito: NUNCA findById(id) a secas desde un endpoint --
     * Hibernate no aplica gestoriaFilter a una carga por clave primaria (ver CLAUDE.md). */
    Optional<Ganadero> findByIdAndGestoriaId(Long id, Long gestoriaId);

    /** ¿Existe un Ganadero con este id en OTRA Gestoria? Solo para auditar el acceso ajeno (T5/D3): booleano, nunca
     * la entidad, tras no encontrarlo en la propia; la respuesta sigue siendo 404 sin cuerpo. Consulta NATIVA: tiene
     * que ver filas de otra Gestoria, que el gestoriaFilter (activo en toda peticion) oculta a un existsBy derivado;
     * el filtro de Hibernate no se aplica a lo nativo. */
    @Query(value = "select case when count(*) > 0 then true else false end from ganadero "
            + "where id = :id and gestoria_id <> :gestoriaId", nativeQuery = true)
    boolean existsByIdAndGestoriaIdNot(@Param("id") Long id, @Param("gestoriaId") Long gestoriaId);

    /** Listado paginado con gestoriaId explicito, sin depender del gestoriaFilter ambiente. */
    Page<Ganadero> findByGestoriaId(Long gestoriaId, Pageable pageable);
}
