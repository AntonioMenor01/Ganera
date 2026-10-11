package com.ganera.core.whatsapp;

import com.ganera.core.whatsapp.CallbackEstadoWhatsapp.Cambio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.util.EnumSet;

import static com.ganera.core.whatsapp.EstadoRespuestaWhatsapp.ENTREGADO;
import static com.ganera.core.whatsapp.EstadoRespuestaWhatsapp.ENVIADO;
import static com.ganera.core.whatsapp.EstadoRespuestaWhatsapp.ENVIANDO;
import static com.ganera.core.whatsapp.EstadoRespuestaWhatsapp.FALLIDO;
import static com.ganera.core.whatsapp.EstadoRespuestaWhatsapp.LEIDO;
import static com.ganera.core.whatsapp.EstadoRespuestaWhatsapp.SIN_CONFIRMAR;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Logica pura del callback de estado de Twilio (B2 reducido, T4): URL del StatusCallback, conversion de
 * MessageStatus, orden de estados (D1/D2) y ErrorCode. Sin Spring, sin red.
 */
class CallbackEstadoWhatsappTest {

    // ------------------------------------------------------------- URL del StatusCallback

    @Test
    void unaUrlHttpsConfiguradaSeUsaTalCualSinEspacios() {
        assertThat(CallbackEstadoWhatsapp.urlCallback("  https://ganera.example.test/webhooks/twilio/whatsapp/estado  "))
                .isEqualTo(URI.create("https://ganera.example.test/webhooks/twilio/whatsapp/estado"));
    }

    @Test
    void httpTambienValeParaUnTunelLocal() {
        assertThat(CallbackEstadoWhatsapp.urlCallback("http://abc.tunel.example/webhooks/twilio/whatsapp/estado"))
                .isEqualTo(URI.create("http://abc.tunel.example/webhooks/twilio/whatsapp/estado"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "no es una url", "ftp://ganera.example.test/estado",
            "https:///sin-host", "/webhooks/twilio/whatsapp/estado",
            "https://ganera.example.test/estado?clave=x", "https://ganera.example.test/estado#x",
            "https://usuario:clave@ganera.example.test/estado"})
    void unaUrlVaciaOQueNoSirveNoSeConfigura(String valor) {
        assertThat(CallbackEstadoWhatsapp.urlCallback(valor)).isNull();
    }

    @Test
    void unaUrlNulaNoSeConfigura() {
        assertThat(CallbackEstadoWhatsapp.urlCallback(null)).isNull();
    }

    // ------------------------------------------------------------- ErrorCode

    @ParameterizedTest
    @CsvSource({"63016,63016", "1,1", "999999,999999", "' 30008 ',30008", "007,7"})
    void unErrorCodeNumericoDeHastaSeisDigitosSeGuarda(String valor, int esperado) {
        assertThat(CallbackEstadoWhatsapp.codigoError(valor)).isEqualTo(esperado);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "1234567", "abc", "63016x", "-1", "+5", "6301.6", "１２３"})
    void unErrorCodeNoNumericoOLargoSeIgnora(String valor) {
        assertThat(CallbackEstadoWhatsapp.codigoError(valor)).isNull();
    }

    @Test
    void sinErrorCodeEsNull() {
        assertThat(CallbackEstadoWhatsapp.codigoError(null)).isNull();
    }

    // ------------------------------------------------------------- conversion de MessageStatus

    @ParameterizedTest
    @ValueSource(strings = {"queued", "accepted", "sending", "sent", "SENT", " sent "})
    void losEstadosDeSalidaSonEnviado(String status) {
        assertThat(CallbackEstadoWhatsapp.destino(status, null)).hasValueSatisfying(d -> {
            assertThat(d.estado()).isEqualTo(ENVIADO);
            assertThat(d.codigoError()).isNull();
            assertThat(d.anteriores()).containsExactlyInAnyOrder(ENVIANDO, SIN_CONFIRMAR);
        });
    }

    @Test
    void deliveredEsEntregadoYVaciaElCodigo() {
        assertThat(CallbackEstadoWhatsapp.destino("delivered", "63016")).hasValueSatisfying(d -> {
            assertThat(d.estado()).isEqualTo(ENTREGADO);
            assertThat(d.codigoError()).isNull();
            assertThat(d.anteriores()).containsExactlyInAnyOrder(ENVIANDO, SIN_CONFIRMAR, ENVIADO, FALLIDO);
        });
    }

    @Test
    void readEsLeidoYVaciaElCodigo() {
        assertThat(CallbackEstadoWhatsapp.destino("read", "63016")).hasValueSatisfying(d -> {
            assertThat(d.estado()).isEqualTo(LEIDO);
            assertThat(d.codigoError()).isNull();
            assertThat(d.anteriores()).containsExactlyInAnyOrder(ENVIANDO, SIN_CONFIRMAR, ENVIADO, ENTREGADO, FALLIDO);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"failed", "undelivered"})
    void failedYUndeliveredSonFallidoConSuCodigo(String status) {
        assertThat(CallbackEstadoWhatsapp.destino(status, "63016")).hasValueSatisfying(d -> {
            assertThat(d.estado()).isEqualTo(FALLIDO);
            assertThat(d.codigoError()).isEqualTo(63016);
            assertThat(d.anteriores()).containsExactlyInAnyOrder(ENVIANDO, SIN_CONFIRMAR, ENVIADO);
        });
    }

    @Test
    void failedConUnCodigoQueNoSirveEsFallidoSinCodigo() {
        assertThat(CallbackEstadoWhatsapp.destino("failed", "no-numerico")).hasValueSatisfying(d -> {
            assertThat(d.estado()).isEqualTo(FALLIDO);
            assertThat(d.codigoError()).isNull();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "receiving", "received", "canceled", "scheduled", "partially_delivered", "otro"})
    void otroValorNoCambiaNada(String status) {
        assertThat(CallbackEstadoWhatsapp.destino(status, null)).isEmpty();
    }

    @Test
    void sinMessageStatusNoCambiaNada() {
        assertThat(CallbackEstadoWhatsapp.destino(null, null)).isEmpty();
    }

    // ------------------------------------------------------------- tabla completa de transiciones (D1/D2)

    @ParameterizedTest(name = "{0} + {1} -> {2}")
    @CsvSource({
            // actual, MessageStatus, nuevo (vacio = sin cambios)
            "ENVIANDO,      sent,        ENVIADO",
            "SIN_CONFIRMAR, sent,        ENVIADO",
            "ENVIADO,       sent,        ",
            "ENTREGADO,     sent,        ",
            "LEIDO,         sent,        ",
            "FALLIDO,       sent,        ",
            "ENVIANDO,      delivered,   ENTREGADO",
            "SIN_CONFIRMAR, delivered,   ENTREGADO",
            "ENVIADO,       delivered,   ENTREGADO",
            "ENTREGADO,     delivered,   ",
            "LEIDO,         delivered,   ",
            "FALLIDO,       delivered,   ENTREGADO",
            "ENVIANDO,      read,        LEIDO",
            "SIN_CONFIRMAR, read,        LEIDO",
            "ENVIADO,       read,        LEIDO",
            "ENTREGADO,     read,        LEIDO",
            "LEIDO,         read,        ",
            "FALLIDO,       read,        LEIDO",
            "ENVIANDO,      failed,      FALLIDO",
            "SIN_CONFIRMAR, failed,      FALLIDO",
            "ENVIADO,       failed,      FALLIDO",
            "ENTREGADO,     failed,      ",
            "LEIDO,         failed,      ",
            "FALLIDO,       failed,      ",
            "ENVIADO,       undelivered, FALLIDO",
            "ENTREGADO,     undelivered, ",
            "ENVIADO,       otro,        ",
    })
    void tablaDeTransiciones(EstadoRespuestaWhatsapp actual, String status, EstadoRespuestaWhatsapp nuevo) {
        if (nuevo == null) {
            assertThat(CallbackEstadoWhatsapp.transicion(actual, status, "30008")).isEmpty();
        } else {
            assertThat(CallbackEstadoWhatsapp.transicion(actual, status, "30008")).map(Cambio::estado).hasValue(nuevo);
        }
    }

    @Test
    void elCodigoSoloSeGuardaAlFallarYSeVaciaAlCorregirUnFallido() {
        assertThat(CallbackEstadoWhatsapp.transicion(ENVIADO, "failed", "30008"))
                .hasValue(new Cambio(FALLIDO, 30008));
        assertThat(CallbackEstadoWhatsapp.transicion(FALLIDO, "delivered", "30008"))
                .hasValue(new Cambio(ENTREGADO, null));
        assertThat(CallbackEstadoWhatsapp.transicion(FALLIDO, "read", null))
                .hasValue(new Cambio(LEIDO, null));
        assertThat(CallbackEstadoWhatsapp.transicion(SIN_CONFIRMAR, "sent", "30008"))
                .hasValue(new Cambio(ENVIADO, null));
    }

    /** Nunca se retrocede: ningun estado se puede alcanzar desde uno posterior en el orden de D1. */
    @Test
    void ningunDestinoAdmiteComoAnteriorUnEstadoPosterior() {
        for (String status : new String[]{"queued", "sent", "delivered", "read", "failed", "undelivered"}) {
            CallbackEstadoWhatsapp.Destino destino = CallbackEstadoWhatsapp.destino(status, null).orElseThrow();
            assertThat(destino.anteriores()).doesNotContain(destino.estado());
            if (destino.estado() == ENVIADO || destino.estado() == FALLIDO) {
                assertThat(destino.anteriores()).doesNotContainAnyElementsOf(EnumSet.of(ENTREGADO, LEIDO));
            }
            if (destino.estado() == ENTREGADO) {
                assertThat(destino.anteriores()).doesNotContain(LEIDO);
            }
        }
    }
}
