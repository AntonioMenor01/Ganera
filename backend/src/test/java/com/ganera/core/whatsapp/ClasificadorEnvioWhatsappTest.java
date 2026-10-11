package com.ganera.core.whatsapp;

import com.twilio.exception.ApiConnectionException;
import com.twilio.exception.ApiException;
import com.twilio.exception.AuthenticationException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Clasificacion del resultado de un envio (skill test-sin-mocks-externos): funcion pura sobre
 * excepciones construidas de verdad (nada mockeado del SDK). Nunca hay reintento: un error de red
 * es SinConfirmar porque no se sabe si el mensaje salio.
 */
class ClasificadorEnvioWhatsappTest {

    @Test
    void unSidDevueltoEsAceptado() {
        assertThat(ClasificadorEnvioWhatsapp.aceptado("SM0123456789abcdef"))
                .isEqualTo(new ResultadoEnvioWhatsapp.Aceptado("SM0123456789abcdef"));
    }

    @Test
    void sinSidNoSabemosSiSalio() {
        assertThat(ClasificadorEnvioWhatsapp.aceptado(null)).isEqualTo(new ResultadoEnvioWhatsapp.SinConfirmar());
        assertThat(ClasificadorEnvioWhatsapp.aceptado("  ")).isEqualTo(new ResultadoEnvioWhatsapp.SinConfirmar());
    }

    @Test
    void unRechazoDeLaApiEsRechazadoConSuCodigo() {
        assertThat(ClasificadorEnvioWhatsapp.clasificar(new ApiException("Message failed", 63016, null, 400, null)))
                .isEqualTo(new ResultadoEnvioWhatsapp.Rechazado(63016));
        assertThat(ClasificadorEnvioWhatsapp.clasificar(new ApiException("Blocked", 21610, null, 403, null)))
                .isEqualTo(new ResultadoEnvioWhatsapp.Rechazado(21610));
        // 4xx sin codigo de Twilio: rechazado, sin codigo.
        assertThat(ClasificadorEnvioWhatsapp.clasificar(new ApiException("Bad request", null, null, 400, null)))
                .isEqualTo(new ResultadoEnvioWhatsapp.Rechazado(null));
    }

    /** Revision T1, I1: un 5xx de Twilio (incluidos 502/504 de pasarela) puede llegar con el mensaje ya
     * creado; no se sabe si salio -> SinConfirmar, nunca FALLIDO. */
    @Test
    void unCincoCientosDeLaApiEsSinConfirmarAunqueTraigaCodigo() {
        for (int status : new int[]{500, 502, 503, 504}) {
            assertThat(ClasificadorEnvioWhatsapp.clasificar(new ApiException("Server error", 20500, null, status, null)))
                    .as("status " + status).isEqualTo(new ResultadoEnvioWhatsapp.SinConfirmar());
        }
        // Lo que lanza el SDK con un 5xx sin cuerpo JSON: ApiException(String, Integer status).
        assertThat(ClasificadorEnvioWhatsapp.clasificar(new ApiException("Server Error, no content", 503)))
                .isEqualTo(new ResultadoEnvioWhatsapp.SinConfirmar());
    }

    /** Sin status HTTP no sabemos si Twilio llego a contestar: SinConfirmar. */
    @Test
    void unaApiExceptionSinStatusEsSinConfirmar() {
        assertThat(ClasificadorEnvioWhatsapp.clasificar(new ApiException("sin status")))
                .isEqualTo(new ResultadoEnvioWhatsapp.SinConfirmar());
        assertThat(ClasificadorEnvioWhatsapp.clasificar(new ApiException("sin status", 63016, null, null, null)))
                .isEqualTo(new ResultadoEnvioWhatsapp.SinConfirmar());
    }

    @Test
    void unErrorDeConexionTimeoutOEntradaSalidaEsSinConfirmar() {
        assertThat(ClasificadorEnvioWhatsapp.clasificar(new ApiConnectionException("timeout",
                new SocketTimeoutException("Read timed out"))))
                .isEqualTo(new ResultadoEnvioWhatsapp.SinConfirmar());
        assertThat(ClasificadorEnvioWhatsapp.clasificar(new UncheckedIOException(new IOException("reset"))))
                .isEqualTo(new ResultadoEnvioWhatsapp.SinConfirmar());
    }

    @Test
    void cualquierOtroErrorTambienEsSinConfirmar() {
        assertThat(ClasificadorEnvioWhatsapp.clasificar(new AuthenticationException("x")))
                .isEqualTo(new ResultadoEnvioWhatsapp.SinConfirmar());
        assertThat(ClasificadorEnvioWhatsapp.clasificar(new IllegalStateException("x")))
                .isEqualTo(new ResultadoEnvioWhatsapp.SinConfirmar());
    }

    @Test
    void elRemitenteSeNormalizaAWhatsappMasE164() {
        assertThat(ClasificadorEnvioWhatsapp.normalizarRemitente("whatsapp:+14155238886")).isEqualTo("whatsapp:+14155238886");
        assertThat(ClasificadorEnvioWhatsapp.normalizarRemitente("+14155238886")).isEqualTo("whatsapp:+14155238886");
        assertThat(ClasificadorEnvioWhatsapp.normalizarRemitente(" WhatsApp:+14155238886 ")).isEqualTo("whatsapp:+14155238886");
        assertThat(ClasificadorEnvioWhatsapp.normalizarRemitente("")).isNull();
        assertThat(ClasificadorEnvioWhatsapp.normalizarRemitente(null)).isNull();
        assertThat(ClasificadorEnvioWhatsapp.normalizarRemitente("14155238886")).isNull();
        assertThat(ClasificadorEnvioWhatsapp.normalizarRemitente("whatsapp:")).isNull();
    }

    @Test
    void elDestinoEsWhatsappMasElTelefonoE164() {
        assertThat(ClasificadorEnvioWhatsapp.destino("+34600111222")).isEqualTo("whatsapp:+34600111222");
    }

    @Test
    void configuradoSoloConLasTresPropiedades() {
        assertThat(new TwilioEnvioWhatsapp("AC1", "token", "whatsapp:+14155238886", "").configurado()).isTrue();
        assertThat(new TwilioEnvioWhatsapp("", "token", "whatsapp:+14155238886", "").configurado()).isFalse();
        assertThat(new TwilioEnvioWhatsapp("AC1", " ", "whatsapp:+14155238886", "").configurado()).isFalse();
        assertThat(new TwilioEnvioWhatsapp("AC1", "token", "", "").configurado()).isFalse();
        assertThat(new TwilioEnvioWhatsapp("AC1", "token", "no-es-un-numero", "").configurado()).isFalse();
        assertThat(new TwilioEnvioWhatsapp(null, null, null, "").configurado()).isFalse();
    }
}
