package com.ganera.core.whatsapp;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Respuestas por WhatsApp (B2 reducido). Toda consulta lleva el gestoriaId EXPLICITO (nunca findById a
 * secas ni el filtro ambiente solo), salvo {@link #actualizarEstadoPorSid}, la excepcion deliberada del callback
 * de estado de Twilio (T4). No hay (ni debe haber) ningun finder por SID.
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

    /** Gestorias con respuestas por vaciar antes de {@code limite} (D6 de auditoria): una sentencia por gestoria. */
    @Query("select distinct r.gestoria.id from RespuestaWhatsapp r where r.createdAt < :limite and r.textoVaciado = false")
    List<Long> gestoriasConRespuestasPorVaciarAntesDe(@Param("limite") Instant limite);

    /** Vacia el texto y el telefono de destino de las respuestas de UNA gestoria creadas antes de {@code limite}. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RespuestaWhatsapp r set r.cuerpo = :textoEliminado, r.telefonoDestino = null, r.textoVaciado = true "
            + "where r.gestoria.id = :gestoriaId and r.createdAt < :limite and r.textoVaciado = false")
    int vaciarTextoYTelefonoDeGestoriaAntesDe(@Param("gestoriaId") Long gestoriaId, @Param("limite") Instant limite,
                                              @Param("textoEliminado") String textoEliminado);

    /**
     * Callback de estado de Twilio (D7 de auditoria): bloquea con {@code SELECT … FOR UPDATE} la respuesta de ese SID
     * dentro de la transaccion del callback, para leer su estado ANTERIOR exacto y su gestoria antes del UPDATE
     * condicional y dejar la fila de auditoria con el antes/despues exactos. Devuelve la entidad entera (no tiene
     * columnas cifradas); el id, la gestoria y el estado bastan.
     *
     * <p><b>Busqueda SIN gestoria, a proposito:</b> las dos consultas por SID (esta y {@link #actualizarEstadoPorSid})
     * son tenant-unscoped a proposito -- el callback no trae tenant y el SID es {@code UNIQUE} --, como
     * {@code ContactoRepository.findByTelefono}; reservadas a EstadoRespuestaWhatsappService.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RespuestaWhatsapp r where r.twilioMessageSid = :sid")
    Optional<RespuestaWhatsapp> bloquearPorSid(@Param("sid") String sid);

    /**
     * Callback de estado de Twilio (B2 reducido, T4): UNA sola sentencia que cambia el estado y el codigo de error
     * de la respuesta con ese SID solo si su estado actual esta en {@code anteriores} (los que van antes del nuevo
     * en el orden de D1, ver CallbackEstadoWhatsapp). Asi un callback repetido, desordenado o simultaneo nunca
     * hace retroceder una fila, sin leerla antes ni bloquearla. Devuelve las filas afectadas (0 o 1).
     *
     * <p><b>Busqueda SIN gestoria, a proposito:</b> el callback llega sin JWT ni tenant (lo firma Twilio) y el
     * SID es {@code UNIQUE} en toda la tabla. Es la segunda excepcion documentada, junto a
     * {@code ContactoRepository.findByTelefono} (webhook de entrada); reservada exclusivamente para
     * EstadoRespuestaWhatsappService. Ningun codigo autenticado la llama. {@code ahora} sale del Clock.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RespuestaWhatsapp r set r.estado = :nuevo, r.codigoErrorTwilio = :codigo, "
            + "r.actualizadoEn = :ahora where r.twilioMessageSid = :sid and r.estado in :anteriores")
    int actualizarEstadoPorSid(@Param("sid") String sid,
                               @Param("nuevo") EstadoRespuestaWhatsapp nuevo,
                               @Param("codigo") Integer codigo,
                               @Param("anteriores") Set<EstadoRespuestaWhatsapp> anteriores,
                               @Param("ahora") Instant ahora);
}
