package com.ganera.core.ovz;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Documentos OVZ, siempre con {@code gestoriaId} explicito (nunca un {@code findById} desnudo sobre una
 * {@code GestoriaScopedEntity}, CLAUDE.md). {@code existsByIdAndGestoriaIdNot} es para {@link
 * com.ganera.core.shared.auditoria.AccesoAjeno}: detectar un documento de otra gestoria sin devolver datos.
 */
public interface DocumentoOvzRepository extends JpaRepository<DocumentoOvz, Long> {

    Optional<DocumentoOvz> findByIdAndGestoriaId(Long id, Long gestoriaId);

    List<DocumentoOvz> findByGanaderoIdAndGestoriaIdOrderByCreatedAtDescIdDesc(Long ganaderoId, Long gestoriaId);

    /** Para {@code AccesoAjeno}: ¿el documento es de OTRA gestoria? Consulta NATIVA porque tiene que ver filas de
     * otra gestoria, que el gestoriaFilter (activo en toda peticion) ocultaria a un existsBy derivado; el filtro de
     * Hibernate no se aplica a lo nativo. Portable H2/PostgreSQL. */
    @Query(value = "select case when count(*) > 0 then true else false end from documento_ovz "
            + "where id = :id and gestoria_id <> :gestoriaId", nativeQuery = true)
    boolean existsByIdAndGestoriaIdNot(@Param("id") Long id, @Param("gestoriaId") Long gestoriaId);
}
