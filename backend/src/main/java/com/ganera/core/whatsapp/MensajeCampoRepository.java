package com.ganera.core.whatsapp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface MensajeCampoRepository extends JpaRepository<MensajeCampo, Long> {
    boolean existsByMessageSid(String messageSid);

    Optional<MensajeCampo> findFirstByTramiteIdOrderByCreatedAtDesc(Long tramiteId);

    /**
     * Mensaje de un Tramite con el gestoriaId explicito (el del Tramite). Lo usa la extraccion en
     * segundo plano (B1, T2), que corre sin peticion ni gestoriaFilter: un mensaje enlazado al
     * Tramite pero con otra gestoria_id (datos inconsistentes) nunca se envia a la IA.
     */
    Optional<MensajeCampo> findFirstByTramiteIdAndGestoriaIdOrderByCreatedAtDesc(Long tramiteId, Long gestoriaId);

    /**
     * Retencion (B1, T3, D6): borra en bloque los mensajes SIN Tramite (numero desconocido, contacto
     * inactivo, error de recepcion o filas anteriores a B1) recibidos estrictamente antes de
     * {@code limite}. Cross-tenant a proposito: solo la usa RetencionMensajesService, desde el job
     * nocturno. {@code limite} sale del Clock (nunca now() en SQL).
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from MensajeCampo m where m.tramite is null and m.createdAt < :limite")
    int borrarSinTramiteRecibidosAntesDe(@Param("limite") Instant limite);

    /**
     * Retencion (B1, T3, D6): sustituye por {@code textoEliminado} el texto de los mensajes CON
     * Tramite recibidos estrictamente antes de {@code limite} que no lo tengan ya (asi una segunda
     * pasada no cuenta ni toca nada). Un mensaje sin texto (cuerpo vacio, SIN_TEXTO) no se toca: nunca
     * hubo texto que eliminar. No toca el Tramite. Cross-tenant a proposito, como la anterior.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update MensajeCampo m set m.cuerpo = :textoEliminado "
            + "where m.tramite is not null and m.createdAt < :limite and m.cuerpo <> :textoEliminado "
            + "and m.cuerpo <> ''")
    int vaciarTextoConTramiteRecibidosAntesDe(@Param("limite") Instant limite,
                                              @Param("textoEliminado") String textoEliminado);
}
