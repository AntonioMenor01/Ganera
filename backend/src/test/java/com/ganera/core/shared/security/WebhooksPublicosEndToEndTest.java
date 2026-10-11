package com.ganera.core.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SecurityConfig (B2 reducido, T4, ajuste de Antonio): solo tres webhooks exactos son publicos y solo por
 * POST: {@code /webhooks/twilio/whatsapp}, {@code /webhooks/twilio/whatsapp/estado} y {@code /webhooks/stripe}.
 * Cualquier otra ruta bajo {@code /webhooks/} sin JWT es 401, nunca un 200 (ni un 404) anonimo. Con las
 * propiedades de la suite (Twilio y Stripe vacios) los tres webhooks llegan a su controlador sin JWT: 503
 * los de Twilio (fail-closed) y 400 el de Stripe (firma invalida), nunca 401.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebhooksPublicosEndToEndTest {

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private JwtService jwtService;

    private ResponseEntity<String> peticion(HttpMethod metodo, String ruta, String jwt) {
        HttpHeaders headers = new HttpHeaders();
        if (jwt != null) {
            headers.setBearerAuth(jwt);
        }
        Object cuerpo = null;
        if (metodo == HttpMethod.POST) {
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("MessageSid", "SM-inventado");
            cuerpo = form;
        }
        return restTemplate.exchange(ruta, metodo, new HttpEntity<>(cuerpo, headers), String.class);
    }

    @Test
    void losTresWebhooksSiguenSiendoPublicosPorPost() {
        assertThat(peticion(HttpMethod.POST, "/webhooks/twilio/whatsapp", null).getStatusCode().value()).isEqualTo(503);
        assertThat(peticion(HttpMethod.POST, "/webhooks/twilio/whatsapp/estado", null).getStatusCode().value())
                .isEqualTo(503);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Stripe-Signature", "t=1,v1=firma-que-no-encaja");
        ResponseEntity<String> stripe = restTemplate.exchange("/webhooks/stripe", HttpMethod.POST,
                new HttpEntity<>("{\"id\":\"evt_inventado\"}", headers), String.class);
        assertThat(stripe.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void unaRutaInventadaBajoWebhooksSinJwtEs401() {
        for (String ruta : new String[]{"/webhooks/inventada", "/webhooks/twilio/whatsapp/estado/x",
                "/webhooks/twilio/inventada", "/webhooks/stripe/x"}) {
            assertThat(peticion(HttpMethod.POST, ruta, null).getStatusCode().value()).as("POST " + ruta).isEqualTo(401);
            assertThat(peticion(HttpMethod.GET, ruta, null).getStatusCode().value()).as("GET " + ruta).isEqualTo(401);
        }
    }

    @Test
    void losWebhooksPorGetSinJwtNoSonPublicos() {
        for (String ruta : new String[]{"/webhooks/twilio/whatsapp/estado", "/webhooks/twilio/whatsapp",
                "/webhooks/stripe"}) {
            int status = peticion(HttpMethod.GET, ruta, null).getStatusCode().value();
            assertThat(status).as("GET " + ruta).isNotEqualTo(200).isEqualTo(401);
        }
    }

    @Test
    void unaRutaInventadaBajoWebhooksConJwtEs404() {
        String jwt = jwtService.generarToken(new GaneraUserPrincipal(1L, 1L, "alguien@gestoria.test"));

        assertThat(peticion(HttpMethod.GET, "/webhooks/inventada", jwt).getStatusCode().value()).isEqualTo(404);
        assertThat(peticion(HttpMethod.POST, "/webhooks/inventada", jwt).getStatusCode().value()).isEqualTo(404);
    }
}
