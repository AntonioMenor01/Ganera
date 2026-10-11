package com.ganera.core.whatsapp;

import com.twilio.http.HttpClient;
import com.twilio.http.HttpMethod;
import com.twilio.http.Request;
import com.twilio.http.Response;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Revision T1, I1: el HttpClient del SDK de Twilio 10.6.4 reintenta cualquier 5xx hasta 3 veces
 * (HttpClient.reliableRequest con RETRY_CODES = {ANY_500}), lo que podria enviar el MISMO mensaje varias
 * veces. El cliente de Ganera nunca reintenta. Sin red: makeRequest se sustituye por una respuesta
 * construida a mano.
 */
class HttpClientSinReintentosTest {

    @Test
    void shouldRetryEsFalsoParaUnCincoCientosConstruidoAMano() {
        TwilioEnvioWhatsapp.HttpClientSinReintentos cliente = new TwilioEnvioWhatsapp.HttpClientSinReintentos(
                TwilioEnvioWhatsapp.tiempos());

        for (int status : new int[]{500, 502, 503, 504}) {
            assertThat(cliente.reintentaria(new Response("{}", status))).as("status " + status).isFalse();
        }
        // El HttpClient base del SDK SI lo reintentaria (por eso se sobrescribe).
        assertThat(new HttpClient() {
            @Override
            public Response makeRequest(Request request) {
                throw new UnsupportedOperationException();
            }

            boolean base(Response respuesta) {
                return shouldRetry(respuesta, RETRY_CODES);
            }
        }.base(new Response("{}", 503))).isTrue();
    }

    @Test
    void reliableRequestHaceUnaSolaPeticionAunqueTwilioDevuelvaCincoCientos() {
        AtomicInteger peticiones = new AtomicInteger();
        TwilioEnvioWhatsapp.HttpClientSinReintentos cliente =
                new TwilioEnvioWhatsapp.HttpClientSinReintentos(TwilioEnvioWhatsapp.tiempos()) {
                    @Override
                    public Response makeRequest(Request request) {
                        peticiones.incrementAndGet();
                        return new Response("{\"message\":\"Service Unavailable\"}", 503);
                    }
                };

        Response respuesta = cliente.reliableRequest(new Request(HttpMethod.POST, "https://api.twilio.invalid/x"));

        assertThat(peticiones.get()).isEqualTo(1);
        assertThat(respuesta.getStatusCode()).isEqualTo(503);
    }

    @Test
    void losTimeoutsSiguenSiendoDeDiezSegundos() {
        assertThat(TwilioEnvioWhatsapp.tiempos().getConnectTimeout()).isEqualTo(10_000);
        assertThat(TwilioEnvioWhatsapp.tiempos().getConnectionRequestTimeout()).isEqualTo(10_000);
        assertThat(TwilioEnvioWhatsapp.tiempos().getSocketTimeout()).isEqualTo(10_000);
    }
}
