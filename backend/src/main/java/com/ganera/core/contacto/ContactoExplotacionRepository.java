package com.ganera.core.contacto;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Todos los finders llevan gestoriaId explicito: no dependen del gestoriaFilter ambiente. */
public interface ContactoExplotacionRepository extends JpaRepository<ContactoExplotacion, Long> {

    Optional<ContactoExplotacion> findByContactoIdAndExplotacionIdAndGestoriaId(
            Long contactoId, Long explotacionId, Long gestoriaId);

    /** Carga en lote de los enlaces de una pagina de Contactos (evita N+1 en listados). La
     * Explotacion se trae en la misma consulta (codigoRega/nombre van en la respuesta). */
    @EntityGraph(attributePaths = "explotacion")
    List<ContactoExplotacion> findByContactoIdInAndGestoriaId(Collection<Long> contactoIds, Long gestoriaId);

    /** Contactos activos enlazados a unas Explotaciones (detalle de Ganadero). El Contacto se trae
     * en la misma consulta (nombre/telefono van en la respuesta) para no hacer N+1. Filtra por la
     * Gestoria del enlace Y por la del propio Contacto (pasar el mismo gestoriaId dos veces):
     * defensa en profundidad por si alguna escritura futura olvida la comprobacion de la decision 15. */
    @EntityGraph(attributePaths = "contacto")
    List<ContactoExplotacion> findByExplotacionIdInAndGestoriaIdAndContactoGestoriaIdAndContactoActivoTrue(
            Collection<Long> explotacionIds, Long gestoriaId, Long contactoGestoriaId);
}
