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

    /**
     * Mensaje de un Tramite con el gestoriaId explicito (el del Tramite). Lo usa la extraccion en
     * segundo plano (B1, T2), que corre sin peticion ni gestoriaFilter: un mensaje enlazado al
     * Tramite pero con otra gestoria_id (datos inconsistentes) nunca se envia a la IA. Desde «hecho en
     * OVZ» (2026-10-07) tambien GET /tramites/{id} y PATCH: es la unica lectura del mensaje de un Tramite
     * (la variante sin gestoriaId se borro).
     */
    Optional<MensajeCampo> findFirstByTramiteIdAndGestoriaIdOrderByCreatedAtDesc(Long tramiteId, Long gestoriaId);

    /**
     * Instante del ultimo mensaje entrante de un Contacto EN SU GESTORIA (B2 reducido, D5): de aqui sale la
     * ventana de 24 h de WhatsApp. Cuenta cualquier mensaje del contacto en esa gestoria, no solo los del
     * tramite. gestoriaId explicito: un mensaje de otra gestoria con el mismo contacto_id (datos
     * inconsistentes) nunca abre la ventana. Null si no hay ninguno.
     */
    @Query("select max(m.createdAt) from MensajeCampo m where m.contacto.id = :contactoId and m.gestoria.id = :gestoriaId")
    Instant ultimoMensajeDelContacto(@Param("contactoId") Long contactoId, @Param("gestoriaId") Long gestoriaId);

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
