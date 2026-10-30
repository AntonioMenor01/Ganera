package com.ganera.core.contacto;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ContactoRepository extends JpaRepository<Contacto, Long> {

    /**
     * Deliberadamente SIN scope de tenant: telefono es UNIQUE global y esta es la
     * unica forma de saber a que Gestoria pertenece un mensaje entrante.
     * Reservado EXCLUSIVAMENTE al webhook de Twilio de 3b (sin Authentication).
     * El importador Excel y cualquier endpoint autenticado deben usar
     * findByGestoriaIdAndTelefono -- nunca este metodo.
     */
    Optional<Contacto> findByTelefono(String telefono);

    Optional<Contacto> findByIdAndGestoriaId(Long id, Long gestoriaId);

    /** ¿Existe un Contacto con este id en OTRA Gestoria? Solo para auditar el acceso ajeno (T5/D3): booleano, nunca
     * la entidad, tras no encontrarlo en la propia; la respuesta sigue siendo 404 sin cuerpo. Consulta NATIVA: tiene
     * que ver filas de otra Gestoria, que el gestoriaFilter (activo en toda peticion) oculta a un existsBy derivado;
     * el filtro de Hibernate no se aplica a lo nativo. */
    @Query(value = "select case when count(*) > 0 then true else false end from contacto "
            + "where id = :id and gestoria_id <> :gestoriaId", nativeQuery = true)
    boolean existsByIdAndGestoriaIdNot(@Param("id") Long id, @Param("gestoriaId") Long gestoriaId);

    Optional<Contacto> findByGestoriaIdAndTelefono(Long gestoriaId, String telefono);

    Page<Contacto> findByGestoriaIdAndActivoTrue(Long gestoriaId, Pageable pageable);

    /** Incluye inactivos (listado con incluirInactivos). */
    Page<Contacto> findByGestoriaId(Long gestoriaId, Pageable pageable);
}
