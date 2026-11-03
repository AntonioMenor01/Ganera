package com.ganera.core.whatsapp;

import com.ganera.core.shared.auditoria.AccionAuditoria;
import com.ganera.core.shared.auditoria.Actor;
import com.ganera.core.shared.auditoria.AuditoriaService;
import com.ganera.core.shared.auditoria.Clave;
import com.ganera.core.shared.auditoria.DatosAuditoria;
import com.ganera.core.shared.auditoria.EntidadAuditada;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final AuditoriaService auditoriaService;
    private final TransactionTemplate transaccion;
    private final Clock reloj;
    private final Period plazoSinTramite;
    private final Period plazoTextoConTramite;

    @Autowired
    public RetencionMensajesService(
            MensajeCampoRepository mensajeCampoRepository,
            RespuestaWhatsappRepository respuestaWhatsappRepository,
            AuditoriaService auditoriaService,
            PlatformTransactionManager transactionManager,
            Clock reloj,
            @Value("${ganera.retencion.mensajes-sin-tramite:P30D}") Period plazoSinTramite,
            @Value("${ganera.retencion.texto-con-tramite:P12M}") Period plazoTextoConTramite) {
        this.mensajeCampoRepository = mensajeCampoRepository;
        this.respuestaWhatsappRepository = respuestaWhatsappRepository;
        this.auditoriaService = auditoriaService;
        this.transaccion = new TransactionTemplate(transactionManager);
        this.reloj = reloj;
        this.plazoSinTramite = plazoSinTramite;
        this.plazoTextoConTramite = plazoTextoConTramite;
    }

    /** Una pasada de retencion. Devuelve cuantas filas se borraron y cuantas se vaciaron (-1 en la
     * sentencia que fallo). */
    public Resultado aplicar() {
        Instant ahora = reloj.instant();
        int borrados = borrarSinTramite(limiteBorrado(ahora));
        int vaciados = vaciarTextoConTramite(limiteVaciado(ahora));
        int respuestasVaciadas = vaciarRespuestas(limiteVaciado(ahora));
        log.info("Retencion de mensajes: borrados={}, vaciados={}, respuestasVaciadas={}", borrados, vaciados,
                respuestasVaciadas);
        return new Resultado(borrados, vaciados, respuestasVaciadas);
    }

    /**
     * Borrado POR GESTORIA (D6): una transaccion por gestoria con su fila RETENCION_MENSAJES_BORRADOS (filas, limite)
     * si borro algo (0 filas -> sin fila). Los mensajes SIN gestoria se borran en una sentencia aparte SIN auditoria.
     * El total que devuelve es el mismo de siempre (suma de todas las gestorias + los sin gestoria); -1 si falla.
     */
    private int borrarSinTramite(Instant limite) {
        try {
            int total = 0;
            for (Long gestoriaId : mensajeCampoRepository.gestoriasConMensajesSinTramiteAntesDe(limite)) {
                total += transaccion.execute(estado -> {
                    int filas = mensajeCampoRepository.borrarSinTramiteDeGestoriaAntesDe(gestoriaId, limite);
                    auditar(AccionAuditoria.RETENCION_MENSAJES_BORRADOS, gestoriaId, EntidadAuditada.MENSAJE_CAMPO,
                            filas, limite);
                    return filas;
                });
            }
            total += mensajeCampoRepository.borrarSinTramiteSinGestoriaAntesDe(limite);
            return total;
        } catch (RuntimeException e) {
            log.error("Retencion de mensajes: fallo al borrar los mensajes sin tramite: excepcion={}",
                    e.getClass().getName());
            return -1;
        }
    }

    /** Vaciado del texto de mensajes con Tramite POR GESTORIA (D6): fila RETENCION_TEXTO_VACIADO. -1 si falla. */
    private int vaciarTextoConTramite(Instant limite) {
        try {
            int total = 0;
            for (Long gestoriaId : mensajeCampoRepository.gestoriasConTextoPorVaciarAntesDe(limite, TEXTO_ELIMINADO)) {
                total += transaccion.execute(estado -> {
                    int filas = mensajeCampoRepository.vaciarTextoConTramiteDeGestoriaAntesDe(
                            gestoriaId, limite, TEXTO_ELIMINADO);
                    auditar(AccionAuditoria.RETENCION_TEXTO_VACIADO, gestoriaId, EntidadAuditada.MENSAJE_CAMPO,
                            filas, limite);
                    return filas;
                });
            }
            return total;
        } catch (RuntimeException e) {
            log.error("Retencion de mensajes: fallo al vaciar el texto de los mensajes con tramite: excepcion={}",
                    e.getClass().getName());
            return -1;
        }
    }

    /** Vaciado de respuestas por WhatsApp POR GESTORIA (D6): fila RETENCION_RESPUESTAS_VACIADAS. -1 si falla. */
    private int vaciarRespuestas(Instant limite) {
        try {
            int total = 0;
            for (Long gestoriaId : respuestaWhatsappRepository.gestoriasConRespuestasPorVaciarAntesDe(limite)) {
                total += transaccion.execute(estado -> {
                    int filas = respuestaWhatsappRepository.vaciarTextoYTelefonoDeGestoriaAntesDe(
                            gestoriaId, limite, TEXTO_ELIMINADO);
                    auditar(AccionAuditoria.RETENCION_RESPUESTAS_VACIADAS, gestoriaId,
                            EntidadAuditada.RESPUESTA_WHATSAPP, filas, limite);
                    return filas;
                });
            }
            return total;
        } catch (RuntimeException e) {
            log.error("Retencion de mensajes: fallo al vaciar las respuestas enviadas por WhatsApp: excepcion={}",
                    e.getClass().getName());
            return -1;
        }
    }

    /** Una fila de retencion (actor PLANIFICADOR, sin peticion -> ip/request_id NULL; entidad_id NULL) con el numero
     * de filas y el limite, dentro de la misma transaccion que la sentencia. 0 filas -> sin fila. */
    private void auditar(AccionAuditoria accion, Long gestoriaId, EntidadAuditada entidad, int filas, Instant limite) {
        if (filas <= 0) {
            return;
        }
        auditoriaService.registrar(accion, gestoriaId, Actor.planificador(), entidad, null, null,
                DatosAuditoria.con(Clave.FILAS, filas).y(Clave.LIMITE, limite));
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
