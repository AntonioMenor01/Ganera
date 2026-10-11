package com.ganera.core.whatsapp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
    private final Clock reloj;

    public EstadoRespuestaWhatsappService(RespuestaWhatsappRepository respuestaWhatsappRepository, Clock reloj) {
        this.respuestaWhatsappRepository = respuestaWhatsappRepository;
        this.reloj = reloj;
    }

    /**
     * @return si se cambio una fila. false si el MessageStatus no cambia nada, si el SID es desconocido (o aun no
     *         se ha guardado: Twilio puede avisar antes que el paso 3 del envio) o si la fila ya esta en el mismo
     *         estado o en uno posterior.
     */
    public boolean aplicar(String messageSid, String messageStatus, String errorCode) {
        Optional<CallbackEstadoWhatsapp.Destino> destino = CallbackEstadoWhatsapp.destino(messageStatus, errorCode);
        if (destino.isEmpty()) {
            log.info("Callback de estado de WhatsApp con un estado que no cambia nada: sid={}", messageSid);
            return false;
        }
        CallbackEstadoWhatsapp.Destino d = destino.get();
        int filas = respuestaWhatsappRepository.actualizarEstadoPorSid(messageSid, d.estado(), d.codigoError(),
                d.anteriores(), reloj.instant().truncatedTo(ChronoUnit.MICROS));
        if (filas == 0) {
            // Sin leer la fila (una sola sentencia, D1/D4) no se distingue un SID desconocido o aun no guardado
            // de una fila que ya esta en ese estado o en uno posterior: en los dos casos no cambia nada.
            log.info("Callback de estado de WhatsApp sin cambios (SID desconocido, aun no guardado o estado "
                    + "igual o posterior): sid={}", messageSid);
            return false;
        }
        log.info("Respuesta por WhatsApp actualizada por el callback de Twilio: sid={}, estadoAnteriorUnoDe={}, "
                + "estadoNuevo={}, codigo={}", messageSid, d.anteriores(), d.estado(), d.codigoError());
        return true;
    }
}
