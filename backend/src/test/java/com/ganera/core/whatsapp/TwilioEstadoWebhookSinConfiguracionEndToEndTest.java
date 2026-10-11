package com.ganera.core.whatsapp;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fail-closed del callback de estado (D3): sin TWILIO_AUTH_TOKEN o sin TWILIO_STATUS_CALLBACK_URL (o con
 * una URL que no sirve) responde 503 aunque la firma cuadre con lo que haya, y no toca nada. Que estos
 * contextos levanten ya prueba que la app arranca con esas variables vacias.
 */
class TwilioEstadoWebhookSinConfiguracionEndToEndTest {

    private static final String TOKEN = "token-de-prueba-inventado";
    private static final String URL_CALLBACK = "https://ganera.example.test/webhooks/twilio/whatsapp/estado";

    abstract static class Casos {

        @Autowired
        TestRestTemplate restTemplate;

        @Test
        void devuelve503AunqueLaFirmaCuadre() {
            Map<String, String> parametros = new LinkedHashMap<>();
            parametros.put("MessageSid", "SM-sin-config");
            parametros.put("MessageStatus", "delivered");
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.set("X-Twilio-Signature", FirmaTwilioDePrueba.calcular(TOKEN, URL_CALLBACK, parametros));
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            parametros.forEach(form::add);

            ResponseEntity<String> respuesta = restTemplate.postForEntity(
                    "/webhooks/twilio/whatsapp/estado", new HttpEntity<>(form, headers), String.class);

            assertThat(respuesta.getStatusCode().value()).isEqualTo(503);
        }

        @Test
        void sinNingunParametroNiFirmaTambienEs503() {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            ResponseEntity<String> respuesta = restTemplate.postForEntity("/webhooks/twilio/whatsapp/estado",
                    new HttpEntity<>(new LinkedMultiValueMap<>(), headers), String.class);

            assertThat(respuesta.getStatusCode().value()).isEqualTo(503);
        }
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
            "twilio.auth-token=", "ganera.twilio.status-callback-url=" + URL_CALLBACK})
    class SinAuthToken extends Casos {
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
            "twilio.auth-token=" + TOKEN, "ganera.twilio.status-callback-url="})
    class SinUrlDeCallback extends Casos {
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
            "twilio.auth-token=" + TOKEN, "ganera.twilio.status-callback-url=" + URL_CALLBACK + "?x=1"})
    class ConUrlQueNoSirve extends Casos {
    }
}
