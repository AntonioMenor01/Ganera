package com.ganera.core.whatsapp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneId;

/**
 * Retencion de los mensajes de WhatsApp (B1, T3, decision D6; R4 del plan): lo que hace cada noche
 * {@link RetencionMensajesScheduler}.
 *
 * <ul>
 *   <li><b>Borra</b> los MensajeCampo <b>sin Tramite</b> (resultado NUMERO_DESCONOCIDO,
 *       CONTACTO_INACTIVO, ERROR_RECEPCION, o filas anteriores a B1 con resultado null) recibidos
 *       antes de ahora - {@code ganera.retencion.mensajes-sin-tramite} (P30D por defecto).</li>
 *   <li><b>Vacia el texto</b> ({@link #TEXTO_ELIMINADO}) de los MensajeCampo <b>con Tramite</b>
 *       recibidos antes de ahora - {@code ganera.retencion.texto-con-tramite} (P12M por defecto) que
 *       no lo tengan ya vacio (ni un cuerpo vacio de un mensaje SIN_TEXTO: no hubo texto). Se quedan los metadatos (MessageSid, telefono, fecha, resultado,
 *       enlaces) y el Tramite no se toca: es el registro del trabajo de la gestoria.</li>
 *   <li><b>Vacia el texto y el telefono de destino</b> de las respuestas enviadas por WhatsApp
 *       (B2 reducido, D8 ajustada) creadas antes del mismo limite que el texto con Tramite
 *       ({@code ganera.retencion.texto-con-tramite}): cuerpo = {@link #TEXTO_ELIMINADO},
 *       telefono_destino = null, texto_vaciado = true (idempotente). Se quedan el estado, el SID, el
 *       codigo, quien y cuando.</li>
 * </ul>
 *
 * <p>Los dos limites se calculan con el {@link Clock} inyectado, en la hora de Madrid (se restan
 * meses o dias a la fecha local de Madrid, no a un instante UTC), y se pasan a la consulta como
 * parametro {@link Instant}: nunca now() en SQL. Comparacion estricta: un mensaje recibido justo en
 * el limite se queda.
 *
 * <p>Cross-tenant a proposito, como SuscripcionSyncScheduler: mensaje_campo no es tenant-scoped (no
 * extiende GestoriaScopedEntity) y el job corre sin peticion, sin gestoriaFilter. respuesta_whatsapp SI
 * es tenant-scoped, pero su sentencia tambien es cross-tenant a proposito: es el mismo job sin peticion,
 * para todas las gestorias. Las tres sentencias son DELETE/UPDATE JPQL en bloque, cada una en su propia
 * transaccion (la del repositorio) y su propio try: si una falla, las otras se ejecutan igual. Un fallo se registra solo con el tipo de excepcion (su mensaje
 * podria llevar datos del mensaje) y nunca se propaga.
 */
@Slf4j
@Service
public class RetencionMensajesService {

    /** Texto que sustituye al de un mensaje con Tramite pasado su plazo. Lo devuelve el detalle del
     * Tramite como mensajeOriginal. */
    public static final String TEXTO_ELIMINADO = "[texto eliminado por antigüedad]";

    static final ZoneId ZONA = ZoneId.of("Europe/Madrid");

    private final MensajeCampoRepository mensajeCampoRepository;
    private final RespuestaWhatsappRepository respuestaWhatsappRepository;
    private final Clock reloj;
    private final Period plazoSinTramite;
    private final Period plazoTextoConTramite;

    @Autowired
    public RetencionMensajesService(
            MensajeCampoRepository mensajeCampoRepository,
            RespuestaWhatsappRepository respuestaWhatsappRepository,
            Clock reloj,
            @Value("${ganera.retencion.mensajes-sin-tramite:P30D}") Period plazoSinTramite,
            @Value("${ganera.retencion.texto-con-tramite:P12M}") Period plazoTextoConTramite) {
        this.mensajeCampoRepository = mensajeCampoRepository;
        this.respuestaWhatsappRepository = respuestaWhatsappRepository;
        this.reloj = reloj;
        this.plazoSinTramite = plazoSinTramite;
        this.plazoTextoConTramite = plazoTextoConTramite;
    }

    /** Una pasada de retencion. Devuelve cuantas filas se borraron y cuantas se vaciaron (-1 en la
     * sentencia que fallo). */
    public Resultado aplicar() {
        Instant ahora = reloj.instant();
        int borrados = -1;
        int vaciados = -1;
        int respuestasVaciadas = -1;
        try {
            borrados = mensajeCampoRepository.borrarSinTramiteRecibidosAntesDe(limiteBorrado(ahora));
        } catch (RuntimeException e) {
            log.error("Retencion de mensajes: fallo al borrar los mensajes sin tramite: excepcion={}",
                    e.getClass().getName());
        }
        try {
            vaciados = mensajeCampoRepository.vaciarTextoConTramiteRecibidosAntesDe(
                    limiteVaciado(ahora), TEXTO_ELIMINADO);
        } catch (RuntimeException e) {
            log.error("Retencion de mensajes: fallo al vaciar el texto de los mensajes con tramite: excepcion={}",
                    e.getClass().getName());
        }
        try {
            respuestasVaciadas = respuestaWhatsappRepository.vaciarTextoYTelefonoEnviadasAntesDe(
                    limiteVaciado(ahora), TEXTO_ELIMINADO);
        } catch (RuntimeException e) {
            log.error("Retencion de mensajes: fallo al vaciar las respuestas enviadas por WhatsApp: excepcion={}",
                    e.getClass().getName());
        }
        log.info("Retencion de mensajes: borrados={}, vaciados={}, respuestasVaciadas={}", borrados, vaciados,
                respuestasVaciadas);
        return new Resultado(borrados, vaciados, respuestasVaciadas);
    }

    Instant limiteBorrado(Instant ahora) {
        return ahora.atZone(ZONA).minus(plazoSinTramite).toInstant();
    }

    Instant limiteVaciado(Instant ahora) {
        return ahora.atZone(ZONA).minus(plazoTextoConTramite).toInstant();
    }

    Period plazoSinTramite() {
        return plazoSinTramite;
    }

    Period plazoTextoConTramite() {
        return plazoTextoConTramite;
    }

    /** Filas afectadas por una pasada; -1 en la sentencia que fallo. */
    public record Resultado(int borrados, int vaciados, int respuestasVaciadas) {
    }
}
