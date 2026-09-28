package com.ganera.core.contacto;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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

    Optional<Contacto> findByGestoriaIdAndTelefono(Long gestoriaId, String telefono);

    Page<Contacto> findByGestoriaIdAndActivoTrue(Long gestoriaId, Pageable pageable);

    /** Incluye inactivos (listado con incluirInactivos). */
    Page<Contacto> findByGestoriaId(Long gestoriaId, Pageable pageable);
}
