package com.ganera.core.whatsapp;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static com.ganera.core.whatsapp.EstadoRespuestaWhatsapp.ENTREGADO;
import static com.ganera.core.whatsapp.EstadoRespuestaWhatsapp.ENVIADO;
import static com.ganera.core.whatsapp.EstadoRespuestaWhatsapp.ENVIANDO;
import static com.ganera.core.whatsapp.EstadoRespuestaWhatsapp.FALLIDO;
import static com.ganera.core.whatsapp.EstadoRespuestaWhatsapp.LEIDO;
import static com.ganera.core.whatsapp.EstadoRespuestaWhatsapp.SIN_CONFIRMAR;

/**
 * Logica pura del callback de estado de Twilio (B2 reducido, T4; skill test-sin-mocks-externos). Sin Spring,
 * sin red: la usan {@link TwilioEnvioWhatsapp} (si pide callback y a que URL), el controlador del callback (la
 * misma URL para validar la firma) y {@link EstadoRespuestaWhatsappService} (a que estado pasa y desde cuales).
 *
 * <p><b>Orden (D1):</b> {@code ENVIANDO}/{@code SIN_CONFIRMAR} &lt; {@code ENVIADO} &lt; {@code ENTREGADO} &lt;
 * {@code LEIDO}. {@code FALLIDO} gana a {@code ENVIANDO}, {@code SIN_CONFIRMAR} y {@code ENVIADO}, no a
 * {@code ENTREGADO}/{@code LEIDO}; un {@code delivered}/{@code read} posterior corrige un {@code FALLIDO} y
 * vacia su codigo. <b>D2:</b> un {@code SIN_CONFIRMAR} con SID guardado se corrige con el callback. Un estado
 * nunca es anterior de si mismo: un callback repetido no cambia nada (ni {@code actualizado_en}).
 */
final class CallbackEstadoWhatsapp {

    /** Tope del cuerpo del callback (el de Twilio ocupa menos de 1 KB). */
    static final int TAMANO_MAXIMO_CUERPO = 8192;

    /** Longitud maxima de un ErrorCode de Twilio que se guarda (sus codigos tienen 5 cifras). */
    private static final int DIGITOS_MAXIMOS_CODIGO = 6;

    private static final Set<EstadoRespuestaWhatsapp> ANTES_DE_ENVIADO = Collections.unmodifiableSet(
            EnumSet.of(ENVIANDO, SIN_CONFIRMAR));
    private static final Set<EstadoRespuestaWhatsapp> ANTES_DE_ENTREGADO = Collections.unmodifiableSet(
            EnumSet.of(ENVIANDO, SIN_CONFIRMAR, ENVIADO, FALLIDO));
    private static final Set<EstadoRespuestaWhatsapp> ANTES_DE_LEIDO = Collections.unmodifiableSet(
            EnumSet.of(ENVIANDO, SIN_CONFIRMAR, ENVIADO, ENTREGADO, FALLIDO));
    private static final Set<EstadoRespuestaWhatsapp> ANTES_DE_FALLIDO = Collections.unmodifiableSet(
            EnumSet.of(ENVIANDO, SIN_CONFIRMAR, ENVIADO));

    private CallbackEstadoWhatsapp() {
    }

    /**
     * A que estado lleva un MessageStatus, con que codigo de error, y desde que estados actuales se permite
     * (los anteriores en el orden de D1). El UPDATE condicional usa exactamente {@code anteriores}.
     */
    record Destino(EstadoRespuestaWhatsapp estado, Integer codigoError, Set<EstadoRespuestaWhatsapp> anteriores) {
    }

    /** Cambio que se aplicaria a una fila: nuevo estado y codigo de error (null lo vacia). */
    record Cambio(EstadoRespuestaWhatsapp estado, Integer codigoError) {
    }

    /**
     * URL del StatusCallback configurada ({@code TWILIO_STATUS_CALLBACK_URL}), sin espacios alrededor, o null si
     * esta vacia o no sirve: tiene que ser absoluta, {@code http(s)}, con host, sin usuario, sin query string
     * (sus parametros entrarian en el formulario y la firma no cuadraria nunca) y sin fragmento. null significa
     * "no configurada": el envio no pide callback y el endpoint responde 503.
     */
    static URI urlCallback(String configurada) {
        if (configurada == null || configurada.isBlank()) {
            return null;
        }
        URI uri;
        try {
            uri = new URI(configurada.strip());
        } catch (URISyntaxException e) {
            return null;
        }
        String esquema = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        boolean valida = (esquema.equals("https") || esquema.equals("http"))
                && uri.getHost() != null
                && uri.getRawUserInfo() == null
                && uri.getRawQuery() == null
                && uri.getRawFragment() == null;
        return valida ? uri : null;
    }

    /** ErrorCode de Twilio si son solo cifras ASCII (1–6, sin signo); si no, null y se ignora. */
    static Integer codigoError(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.strip();
        if (limpio.isEmpty() || limpio.length() > DIGITOS_MAXIMOS_CODIGO || !limpio.matches("[0-9]+")) {
            return null;
        }
        return Integer.valueOf(limpio);
    }

    /**
     * Conversion de MessageStatus (sin distinguir mayusculas): {@code queued/accepted/sending/sent} → ENVIADO;
     * {@code delivered} → ENTREGADO; {@code read} → LEIDO; {@code failed/undelivered} → FALLIDO con su codigo.
     * Cualquier otro valor → vacio (sin cambios). El codigo solo se guarda al fallar; los demas destinos lo vacian.
     */
    static Optional<Destino> destino(String messageStatus, String errorCode) {
        if (messageStatus == null) {
            return Optional.empty();
        }
        return switch (messageStatus.strip().toLowerCase(Locale.ROOT)) {
            case "queued", "accepted", "sending", "sent" -> Optional.of(new Destino(ENVIADO, null, ANTES_DE_ENVIADO));
            case "delivered" -> Optional.of(new Destino(ENTREGADO, null, ANTES_DE_ENTREGADO));
            case "read" -> Optional.of(new Destino(LEIDO, null, ANTES_DE_LEIDO));
            case "failed", "undelivered" ->
                    Optional.of(new Destino(FALLIDO, codigoError(errorCode), ANTES_DE_FALLIDO));
            default -> Optional.empty();
        };
    }

    /** Lo que haria el callback sobre una fila en {@code actual}: el cambio, o vacio si no avanza. */
    static Optional<Cambio> transicion(EstadoRespuestaWhatsapp actual, String messageStatus, String errorCode) {
        return destino(messageStatus, errorCode)
                .filter(destino -> destino.anteriores().contains(actual))
                .map(destino -> new Cambio(destino.estado(), destino.codigoError()));
    }
}
