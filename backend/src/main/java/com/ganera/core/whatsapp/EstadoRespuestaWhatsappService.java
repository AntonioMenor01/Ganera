package com.ganera.core.whatsapp;

import com.ganera.core.shared.auditoria.AccionAuditoria;
import com.ganera.core.shared.auditoria.Actor;
import com.ganera.core.shared.auditoria.AuditoriaService;
import com.ganera.core.shared.auditoria.Clave;
import com.ganera.core.shared.auditoria.DatosAuditoria;
import com.ganera.core.shared.auditoria.EntidadAuditada;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Aplica un callback de estado de Twilio a una RespuestaWhatsapp (B2 reducido, T4). La decision es la funcion
 * pura {@link CallbackEstadoWhatsapp#destino}; aqui solo se lanza el UPDATE condicional por SID
 * ({@link RespuestaWhatsappRepository#actualizarEstadoPorSid}) con {@code actualizado_en} del Clock.
 *
 * <p>Solo recibe el SID, el MessageStatus y el ErrorCode: ningun otro dato de la peticion localiza ni cambia
 * nada. Logs: solo SID, estado y codigo; nunca telefonos ni texto.
 */
@Slf4j
@Service
public class EstadoRespuestaWhatsappService {

    private final RespuestaWhatsappRepository respuestaWhatsappRepository;
    private final AuditoriaService auditoriaService;
    private final Clock reloj;

    public EstadoRespuestaWhatsappService(RespuestaWhatsappRepository respuestaWhatsappRepository,
                                          AuditoriaService auditoriaService, Clock reloj) {
        this.respuestaWhatsappRepository = respuestaWhatsappRepository;
        this.auditoriaService = auditoriaService;
        this.reloj = reloj;
    }

    /**
     * D7 de auditoria: en UNA transaccion, bloquea la fila del SID con {@code SELECT … FOR UPDATE}
     * ({@link RespuestaWhatsappRepository#bloquearPorSid}) para leer su estado ANTERIOR exacto y su gestoria,
     * aplica el mismo UPDATE condicional y, si cambia algo, deja una fila RESPUESTA_WHATSAPP_ESTADO_ACTUALIZADO
     * con el antes/despues exactos (actor WEBHOOK_TWILIO, gestoria de la respuesta). Sin cambio -> sin fila.
     *
     * @return si se cambio una fila. false si el MessageStatus no cambia nada, si el SID es desconocido (o aun no
     *         se ha guardado: Twilio puede avisar antes que el paso 3 del envio) o si la fila ya esta en el mismo
     *         estado o en uno posterior.
     */
    @Transactional
    public boolean aplicar(String messageSid, String messageStatus, String errorCode) {
        Optional<CallbackEstadoWhatsapp.Destino> destino = CallbackEstadoWhatsapp.destino(messageStatus, errorCode);
        if (destino.isEmpty()) {
            log.info("Callback de estado de WhatsApp con un estado que no cambia nada: sid={}", messageSid);
            return false;
        }
        CallbackEstadoWhatsapp.Destino d = destino.get();
        Optional<RespuestaWhatsapp> bloqueada = respuestaWhatsappRepository.bloquearPorSid(messageSid);
        if (bloqueada.isEmpty()) {
            // SID desconocido o aun no guardado (Twilio puede avisar antes que el paso 3 del envio): nada que tocar.
            log.info("Callback de estado de WhatsApp sin cambios (SID desconocido o aun no guardado): sid={}",
                    messageSid);
            return false;
        }
        RespuestaWhatsapp respuesta = bloqueada.get();
        EstadoRespuestaWhatsapp estadoAntes = respuesta.getEstado();
        Long gestoriaId = respuesta.getGestoria().getId();
        Long respuestaId = respuesta.getId();
        if (!d.anteriores().contains(estadoAntes)) {
            // La fila ya esta en ese estado o en uno posterior (callback repetido o desordenado): no retrocede.
            log.info("Callback de estado de WhatsApp sin cambios (estado igual o posterior): sid={}", messageSid);
            return false;
        }
        int filas = respuestaWhatsappRepository.actualizarEstadoPorSid(messageSid, d.estado(), d.codigoError(),
                d.anteriores(), reloj.instant().truncatedTo(ChronoUnit.MICROS));
        if (filas == 0) {
            // Con la fila bloqueada y el estado comprobado arriba no deberia pasar; por si acaso, sin fila.
            log.info("Callback de estado de WhatsApp sin cambios tras el bloqueo: sid={}", messageSid);
            return false;
        }
        DatosAuditoria despues = DatosAuditoria.con(Clave.ESTADO, d.estado());
        if (d.codigoError() != null) {
            despues = despues.y(Clave.CODIGO_ERROR, d.codigoError());
        }
        auditoriaService.registrar(AccionAuditoria.RESPUESTA_WHATSAPP_ESTADO_ACTUALIZADO, gestoriaId,
                Actor.webhookTwilio(), EntidadAuditada.RESPUESTA_WHATSAPP, respuestaId,
                DatosAuditoria.con(Clave.ESTADO, estadoAntes), despues);
        log.info("Respuesta por WhatsApp actualizada por el callback de Twilio: sid={}, estadoAnterior={}, "
                + "estadoNuevo={}, codigo={}", messageSid, estadoAntes, d.estado(), d.codigoError());
        return true;
    }
}
