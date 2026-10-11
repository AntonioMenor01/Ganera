package com.ganera.core.whatsapp;

import com.ganera.core.contacto.Contacto;
import com.ganera.core.tramite.EstadoTramite;
import com.ganera.core.tramite.OrigenTramite;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Reglas puras de la respuesta por WhatsApp (B2 reducido, D1, D5, D9), sin Spring ni Twilio. Los textos
 * los decide Ganera; nunca se muestra el mensaje en ingles de Twilio.
 */
public final class ReglasRespuestaWhatsapp {

    /** WhatsApp solo deja mensajes libres en las 24 h siguientes al ultimo mensaje del contacto (D5). */
    static final Duration VENTANA = Duration.ofHours(24);
    /** Una fila en ENVIANDO mas vieja que esto se expone como SIN_CONFIRMAR (el proceso pudo caerse). */
    static final Duration ENVIANDO_MAXIMO = Duration.ofMinutes(2);

    /** Estados del tramite en los que se puede responder (D1). */
    static final Set<EstadoTramite> ESTADOS_PERMITIDOS = EnumSet.of(EstadoTramite.PENDIENTE_REVISION,
            EstadoTramite.APROBADO, EstadoTramite.HECHO_EN_OVZ, EstadoTramite.RECHAZADO);

    public static final String MOTIVO_NO_ES_WHATSAPP = "Este trámite no llegó por WhatsApp; no hay a quién responder.";
    public static final String MOTIVO_ESTADO_NO_PERMITIDO = "En este estado no se puede responder por WhatsApp.";
    public static final String MOTIVO_CONTACTO_INACTIVO = "El contacto está dado de baja; no se le puede escribir.";
    /** Ventana cerrada y codigo 63016 de Twilio: el mismo texto en los dos sitios. */
    public static final String MOTIVO_FUERA_DE_VENTANA =
            "Han pasado más de 24 horas desde su último mensaje. WhatsApp no deja escribirle hasta que vuelva a escribir.";
    static final String MOTIVO_NO_UNIDO_AL_SANDBOX = "Este número no se ha unido al sandbox de WhatsApp de pruebas.";
    static final String MOTIVO_BLOQUEADO = "El contacto ha bloqueado los mensajes de este número.";
    static final String MOTIVO_RECHAZADO_SIN_CODIGO = "WhatsApp no ha aceptado el mensaje.";
    static final String MOTIVO_SIN_CONFIRMAR = "No sabemos si ha llegado. Compruébalo antes de volver a enviarlo.";

    private ReglasRespuestaWhatsapp() {
    }

    /** Si el Contacto pertenece a la gestoria del JWT (revision T1, N3; como la decision 15 de A1). */
    static boolean contactoDeLaGestoria(Contacto contacto, Long gestoriaId) {
        return contacto != null && contacto.getGestoria() != null
                && Objects.equals(contacto.getGestoria().getId(), gestoriaId);
    }

    /** Hasta cuando esta abierta la ventana: ultimo mensaje del contacto + 24 h; null sin mensajes. */
    public static Instant ventanaAbiertaHasta(Instant ultimoMensaje) {
        return ultimoMensaje == null ? null : ultimoMensaje.plus(VENTANA);
    }

    /** Abierta si {@code ahora} es estrictamente anterior a {@code abiertaHasta}. */
    static boolean ventanaAbierta(Instant abiertaHasta, Instant ahora) {
        return abiertaHasta != null && ahora.isBefore(abiertaHasta);
    }

    /**
     * Por que no se puede responder, o null si se puede. Orden: origen, estado, contacto, ventana. No
     * incluye la configuracion de Twilio (eso es un 503, no una regla del tramite).
     */
    public static String motivoNoDisponible(OrigenTramite origen, EstadoTramite estado, boolean contactoActivo,
                                            Instant ventanaAbiertaHasta, Instant ahora) {
        if (origen != OrigenTramite.WHATSAPP) {
            return MOTIVO_NO_ES_WHATSAPP;
        }
        if (!ESTADOS_PERMITIDOS.contains(estado)) {
            return MOTIVO_ESTADO_NO_PERMITIDO;
        }
        if (!contactoActivo) {
            return MOTIVO_CONTACTO_INACTIVO;
        }
        if (!ventanaAbierta(ventanaAbiertaHasta, ahora)) {
            return MOTIVO_FUERA_DE_VENTANA;
        }
        return null;
    }

    /** Motivo en espanol de un rechazo de Twilio por su codigo (D9). */
    static String motivoPorCodigo(Integer codigo) {
        if (codigo == null) {
            return MOTIVO_RECHAZADO_SIN_CODIGO;
        }
        return switch (codigo) {
            case 63016 -> MOTIVO_FUERA_DE_VENTANA;
            case 63015 -> MOTIVO_NO_UNIDO_AL_SANDBOX;
            case 21610 -> MOTIVO_BLOQUEADO;
            default -> "WhatsApp no ha aceptado el mensaje (código " + codigo + ").";
        };
    }

    /** Motivo que acompana a una respuesta en su estado (efectivo); null si no hay error. */
    static String motivoError(EstadoRespuestaWhatsapp estado, Integer codigo) {
        return switch (estado) {
            case FALLIDO -> motivoPorCodigo(codigo);
            case SIN_CONFIRMAR -> MOTIVO_SIN_CONFIRMAR;
            default -> null;
        };
    }

    /** ENVIANDO de hace mas de 2 minutos se expone como SIN_CONFIRMAR; no se reescribe en BD. */
    static EstadoRespuestaWhatsapp estadoEfectivo(EstadoRespuestaWhatsapp estado, Instant creadoEn, Instant ahora) {
        if (estado == EstadoRespuestaWhatsapp.ENVIANDO && ahora.isAfter(creadoEn.plus(ENVIANDO_MAXIMO))) {
            return EstadoRespuestaWhatsapp.SIN_CONFIRMAR;
        }
        return estado;
    }
}
