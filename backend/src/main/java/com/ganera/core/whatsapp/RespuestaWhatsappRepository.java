package com.ganera.core.whatsapp;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Respuestas por WhatsApp (B2 reducido). Toda consulta lleva el gestoriaId EXPLICITO (nunca findById a
 * secas ni el filtro ambiente solo). Todavia no hay finder por SID: es del callback de estado (T4).
 */
public interface RespuestaWhatsappRepository extends JpaRepository<RespuestaWhatsapp, Long> {

    /** Respuestas de un Tramite en orden cronologico, con el usuario que las envio en la MISMA consulta
     * (sin N+1 en el detalle). */
    @EntityGraph(attributePaths = "enviadoPor")
    List<RespuestaWhatsapp> findByTramiteIdAndGestoriaIdOrderByCreatedAtAscIdAsc(Long tramiteId, Long gestoriaId);

    /** La respuesta guardada con esa clave de idempotencia en la gestoria, con su usuario. */
    @EntityGraph(attributePaths = "enviadoPor")
    Optional<RespuestaWhatsapp> findByGestoriaIdAndClaveIdempotencia(Long gestoriaId, String claveIdempotencia);

    /** Una respuesta por id con el gestoriaId explicito, con su usuario. */
    @EntityGraph(attributePaths = "enviadoPor")
    Optional<RespuestaWhatsapp> findByIdAndGestoriaId(Long id, Long gestoriaId);

    /**
     * Retencion (D8 ajustada): a las respuestas creadas estrictamente antes de {@code limite} que aun no
     * se hayan vaciado se les cambia el cuerpo por {@code textoEliminado} y se pone el telefono de destino
     * a null. Idempotente por texto_vaciado. Cross-tenant a proposito: solo la usa
     * RetencionMensajesService, desde el job nocturno sin peticion. {@code limite} sale del Clock.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RespuestaWhatsapp r set r.cuerpo = :textoEliminado, r.telefonoDestino = null, "
            + "r.textoVaciado = true where r.createdAt < :limite and r.textoVaciado = false")
    int vaciarTextoYTelefonoEnviadasAntesDe(@Param("limite") Instant limite,
                                            @Param("textoEliminado") String textoEliminado);
}
