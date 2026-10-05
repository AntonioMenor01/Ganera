package com.ganera.core.whatsapp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
 * Fail-closed: sin TWILIO_AUTH_TOKEN o sin TWILIO_WEBHOOK_URL el webhook responde 503 y no guarda
 * nada, aunque la peticion traiga una firma "valida" para lo que haya configurado. El aviso en el
 * log no lleva ni el texto ni el telefono del mensaje. Que estos contextos levanten ya prueba que
 * la app arranca con esas variables vacias.
 */
class TwilioWebhookSinConfiguracionEndToEndTest {

    private static final String TOKEN = "token-de-prueba-inventado";
    private static final String URL_PUBLICA = "https://ganera.example.test/webhooks/twilio/whatsapp";
    private static final String TELEFONO = "whatsapp:+34600999888";
    private static final String TEXTO = "Baja del 4321 texto-que-no-debe-salir-en-el-log";

    @ExtendWith(OutputCaptureExtension.class)
    abstract static class Casos {

        @Autowired
        TestRestTemplate restTemplate;
        @Autowired
        MensajeCampoRepository mensajeCampoRepository;

        @AfterEach
        void limpiar() {
            mensajeCampoRepository.deleteAll();
        }

        @Test
        void devuelve503NoGuardaNadaYAvisaSinDatosDelMensaje(CapturedOutput salida) {
            Map<String, String> parametros = new LinkedHashMap<>();
            parametros.put("MessageSid", "SM-sin-config");
            parametros.put("From", TELEFONO);
            parametros.put("Body", TEXTO);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.set("X-Twilio-Signature", FirmaTwilioDePrueba.calcular(TOKEN, URL_PUBLICA, parametros));
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            parametros.forEach(form::add);

            ResponseEntity<String> respuesta = restTemplate.postForEntity(
                    "/webhooks/twilio/whatsapp", new HttpEntity<>(form, headers), String.class);

            assertThat(respuesta.getStatusCode().value()).isEqualTo(503);
            assertThat(mensajeCampoRepository.count()).isZero();
            assertThat(salida.getOut()).contains("WARN").contains("Webhook de Twilio sin configurar");
            assertThat(salida.getOut()).doesNotContain("34600999888").doesNotContain("texto-que-no-debe-salir");
        }

        @Test
        void sinNingunParametroTambienDevuelve503AntesDeMirarElFormulario() {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            ResponseEntity<String> respuesta = restTemplate.postForEntity(
                    "/webhooks/twilio/whatsapp", new HttpEntity<>(new LinkedMultiValueMap<>(), headers), String.class);

            assertThat(respuesta.getStatusCode().value()).isEqualTo(503);
            assertThat(mensajeCampoRepository.count()).isZero();
        }
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
            "twilio.auth-token=", "ganera.twilio.webhook-url=" + URL_PUBLICA})
    class SinAuthToken extends Casos {
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
            "twilio.auth-token=" + TOKEN, "ganera.twilio.webhook-url="})
    class SinUrlPublica extends Casos {
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
            "twilio.auth-token=", "ganera.twilio.webhook-url="})
    class AmbosVacios extends Casos {
    }
}
