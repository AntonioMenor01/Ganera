package com.ganera.core.whatsapp;

import org.junit.jupiter.api.AfterEach;
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
 * Webhook de Twilio por HTTP real (POST de formulario de verdad, para que getParameterMap sea el
 * de Tomcat). La URL configurada a proposito NO es la del servidor de test (localhost:puerto):
 * prueba que la firma se valida contra TWILIO_WEBHOOK_URL y no contra getRequestURL(), que detras
 * de un tunel seria http://localhost:8080/...
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "twilio.auth-token=" + TwilioWebhookFirmaEndToEndTest.AUTH_TOKEN,
        "ganera.twilio.webhook-url=" + TwilioWebhookFirmaEndToEndTest.URL_PUBLICA})
class TwilioWebhookFirmaEndToEndTest {

    static final String AUTH_TOKEN = "token-de-prueba-inventado";
    static final String URL_PUBLICA = "https://ganera.example.test/webhooks/twilio/whatsapp";
    private static final String RUTA = "/webhooks/twilio/whatsapp";

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private MensajeCampoRepository mensajeCampoRepository;

    @AfterEach
    void limpiar() {
        mensajeCampoRepository.deleteAll();
    }

    /** Parametros como los que manda Twilio de verdad para un WhatsApp entrante, no solo los tres que usamos. */
    private static Map<String, String> parametrosDeTwilio(String messageSid) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("MessageSid", messageSid);
        p.put("SmsMessageSid", messageSid);
        p.put("AccountSid", "AC00000000000000000000000000000000");
        p.put("From", "whatsapp:+34600111222");
        p.put("To", "whatsapp:+14155238886");
        p.put("Body", "Alta del ternero 1234, crotal ES-0100 0000 5678 & más");
        p.put("NumMedia", "0");
        p.put("ProfileName", "José Pérez");
        p.put("WaId", "34600111222");
        return p;
    }

    private ResponseEntity<String> enviar(Map<String, String> parametros, String firma) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        if (firma != null) {
            headers.set("X-Twilio-Signature", firma);
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        parametros.forEach(form::add);
        return restTemplate.postForEntity(RUTA, new HttpEntity<>(form, headers), String.class);
    }

    @Test
    void firmaConTodosLosParametrosYLaUrlPublicaSeAceptaYGuardaElMensaje() {
        Map<String, String> parametros = parametrosDeTwilio("SM-firma-ok");
        String firma = FirmaTwilioDePrueba.calcular(AUTH_TOKEN, URL_PUBLICA, parametros);

        ResponseEntity<String> respuesta = enviar(parametros, firma);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(mensajeCampoRepository.findAll()).singleElement().satisfies(m -> {
            assertThat(m.getMessageSid()).isEqualTo("SM-firma-ok");
            assertThat(m.getTelefonoOrigen()).isEqualTo("+34600111222");
            assertThat(m.getCuerpo()).isEqualTo("Alta del ternero 1234, crotal ES-0100 0000 5678 & más");
        });
    }

    @Test
    void mensajeSinBodyComoUnaFotoSolaSeAceptaYGuardaCuerpoVacio() {
        Map<String, String> parametros = parametrosDeTwilio("SM-solo-foto");
        parametros.remove("Body");
        parametros.put("NumMedia", "1");
        parametros.put("MediaUrl0", "https://api.twilio.com/2010-04-01/Accounts/AC0/Messages/MM0/Media/ME0");
        parametros.put("MediaContentType0", "image/jpeg");
        String firma = FirmaTwilioDePrueba.calcular(AUTH_TOKEN, URL_PUBLICA, parametros);

        ResponseEntity<String> respuesta = enviar(parametros, firma);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(mensajeCampoRepository.findAll()).singleElement()
                .satisfies(m -> assertThat(m.getCuerpo()).isEmpty());
    }

    @Test
    void unParametroAlteradoTrasFirmarDevuelve403YNoGuardaNada() {
        Map<String, String> parametros = parametrosDeTwilio("SM-alterado");
        String firma = FirmaTwilioDePrueba.calcular(AUTH_TOKEN, URL_PUBLICA, parametros);
        // Un parametro que el validador antiguo ni miraba: tambien tiene que invalidar la firma.
        parametros.put("ProfileName", "Otro Nombre");

        ResponseEntity<String> respuesta = enviar(parametros, firma);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(403);
        assertThat(mensajeCampoRepository.count()).isZero();
    }

    @Test
    void unParametroAnadidoTrasFirmarDevuelve403YNoGuardaNada() {
        Map<String, String> parametros = parametrosDeTwilio("SM-anadido");
        String firma = FirmaTwilioDePrueba.calcular(AUTH_TOKEN, URL_PUBLICA, parametros);
        parametros.put("Extra", "valor");

        ResponseEntity<String> respuesta = enviar(parametros, firma);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(403);
        assertThat(mensajeCampoRepository.count()).isZero();
    }

    @Test
    void firmaCalculadaParaOtraUrlDevuelve403YNoGuardaNada() {
        Map<String, String> parametros = parametrosDeTwilio("SM-otra-url");
        // La URL a la que de verdad llega la peticion en el test: si el controller validara con
        // getRequestURL(), esta firma pasaria.
        String urlLocal = restTemplate.getRootUri() + RUTA;
        String firma = FirmaTwilioDePrueba.calcular(AUTH_TOKEN, urlLocal, parametros);

        ResponseEntity<String> respuesta = enviar(parametros, firma);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(403);
        assertThat(mensajeCampoRepository.count()).isZero();
    }

    @Test
    void firmaConOtroTokenDevuelve403YNoGuardaNada() {
        Map<String, String> parametros = parametrosDeTwilio("SM-otro-token");
        String firma = FirmaTwilioDePrueba.calcular("otro-token-inventado", URL_PUBLICA, parametros);

        ResponseEntity<String> respuesta = enviar(parametros, firma);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(403);
        assertThat(mensajeCampoRepository.count()).isZero();
    }

    @Test
    void sinCabeceraDeFirmaDevuelve403YNoGuardaNada() {
        ResponseEntity<String> respuesta = enviar(parametrosDeTwilio("SM-sin-firma"), null);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(403);
        assertThat(mensajeCampoRepository.count()).isZero();
    }

    @Test
    void firmaBasuraDevuelve403YNoGuardaNada() {
        ResponseEntity<String> respuesta = enviar(parametrosDeTwilio("SM-basura"), "esto-no-es-una-firma");

        assertThat(respuesta.getStatusCode().value()).isEqualTo(403);
        assertThat(mensajeCampoRepository.count()).isZero();
    }

    /** Sin firma, la forma del formulario no se mira: 403 aunque falte MessageSid (nunca un 400). */
    @Test
    void sinFirmaYSinMessageSidDevuelve403YNo400() {
        Map<String, String> parametros = parametrosDeTwilio("SM-x");
        parametros.remove("MessageSid");
        parametros.remove("SmsMessageSid");

        ResponseEntity<String> respuesta = enviar(parametros, null);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(403);
        assertThat(mensajeCampoRepository.count()).isZero();
    }

    @Test
    void firmaValidaSinMessageSidDevuelve400YNoGuardaNada() {
        Map<String, String> parametros = parametrosDeTwilio("SM-x");
        parametros.remove("MessageSid");
        parametros.remove("SmsMessageSid");
        String firma = FirmaTwilioDePrueba.calcular(AUTH_TOKEN, URL_PUBLICA, parametros);

        ResponseEntity<String> respuesta = enviar(parametros, firma);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
        assertThat(mensajeCampoRepository.count()).isZero();
    }

    @Test
    void firmaValidaSinFromDevuelve400YNoGuardaNada() {
        Map<String, String> parametros = parametrosDeTwilio("SM-sin-from");
        parametros.remove("From");
        String firma = FirmaTwilioDePrueba.calcular(AUTH_TOKEN, URL_PUBLICA, parametros);

        ResponseEntity<String> respuesta = enviar(parametros, firma);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
        assertThat(mensajeCampoRepository.count()).isZero();
    }

    @Test
    void unaEntregaRepetidaConFirmaValidaNoDuplicaElMensaje() {
        Map<String, String> parametros = parametrosDeTwilio("SM-repetido");
        String firma = FirmaTwilioDePrueba.calcular(AUTH_TOKEN, URL_PUBLICA, parametros);

        assertThat(enviar(parametros, firma).getStatusCode().value()).isEqualTo(200);
        assertThat(enviar(parametros, firma).getStatusCode().value()).isEqualTo(200);

        assertThat(mensajeCampoRepository.count()).isEqualTo(1);
    }
}
