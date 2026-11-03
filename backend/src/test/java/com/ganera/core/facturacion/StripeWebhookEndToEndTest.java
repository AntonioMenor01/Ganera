package com.ganera.core.facturacion;

import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.shared.auditoria.GuardiaDmlExtension;
import com.ganera.core.shared.auditoria.LimpiezaAuditoria;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Despacho HTTP real del webhook de Stripe (POST /webhooks/stripe), con la firma HMAC de verdad y
 * payloads en formato de la API 2026-09-30.endive (los eventos reales del webhook live). Prueba que
 * la extraccion por JSON crudo funciona de punta a punta pese al desajuste de version con el SDK:
 *   - checkout.session.completed de la landing (metadata source=ganera-web) -> 200 + alta_pendiente;
 *   - invoice.payment_succeeded SIN "subscription" en la raiz (solo en parent.subscription_details)
 *     -> 200 y la Suscripcion enlazada pasa a ACTIVA (el fallback endive se lee de verdad);
 *   - un evento no mapeado (charge.succeeded) -> 200;
 *   - el mismo checkout.session.completed dos veces no duplica alta_pendiente (idempotencia).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "stripe.webhook-secret=whsec_test_e2e_secret")
@ExtendWith(GuardiaDmlExtension.class)
class StripeWebhookEndToEndTest {

    private static final String SECRETO = "whsec_test_e2e_secret";

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private AltaPendienteRepository altaPendienteRepository;
    @Autowired
    private SuscripcionRepository suscripcionRepository;
    @Autowired
    private GestoriaRepository gestoriaRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Gestoria gestoria;

    @AfterEach
    void limpiar() {
        // El webhook audita los cambios de estado de la suscripcion (T4), asi que hay que vaciar auditoria
        // antes de borrar la gestoria: la FK fk_auditoria_gestoria no tiene ON DELETE (a proposito).
        LimpiezaAuditoria.borrar(jdbcTemplate);
        altaPendienteRepository.findByStripeSessionId("cs_e2e").ifPresent(altaPendienteRepository::delete);
        suscripcionRepository.findByStripeSubscriptionId("sub_linked").ifPresent(suscripcionRepository::delete);
        if (gestoria != null) {
            gestoriaRepository.findById(gestoria.getId()).ifPresent(gestoriaRepository::delete);
        }
    }

    @Test
    void checkoutDeLaLandingEnFormatoEndiveCrea200YUnaAltaPendiente() {
        String payload = envoltorio("evt_checkout", "checkout.session.completed", """
                {
                  "id": "cs_e2e", "object": "checkout.session",
                  "customer": "cus_e2e", "subscription": "sub_e2e",
                  "metadata": {"plan": "base", "periodo": "mensual", "prueba": "true", "source": "ganera-web"},
                  "customer_details": {
                    "email": "e2e@example.com", "name": "Gestoría E2E S.L.",
                    "address": {"line1": "Calle 1", "city": "Badajoz", "country": "ES"},
                    "tax_ids": [{"type": "es_cif", "value": "B11111111"}]
                  }
                }""");

        // Baseline limpio para contar filas de auditoria sin depender del orden de los tests.
        LimpiezaAuditoria.borrar(jdbcTemplate);

        ResponseEntity<String> respuesta = enviar(payload);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        AltaPendiente alta = altaPendienteRepository.findByStripeSessionId("cs_e2e").orElseThrow();
        assertThat(alta.getEstado()).isEqualTo(EstadoAltaPendiente.PENDIENTE);
        assertThat(alta.getSubscriptionId()).isEqualTo("sub_e2e");
        assertThat(alta.getPlan()).isEqualTo("base");
        assertThat(alta.getPeriodo()).isEqualTo("mensual");
        assertThat(alta.getNif()).isEqualTo("B11111111");
        // Exclusion documentada (T6 a): el alta de la landing es pre-tenant (sin gestoria), asi que NO deja
        // fila de auditoria; su transicion a una Gestoria real se audita en el onboarding. La guardia DML da
        // por buena esta escritura solo porque alta_pendiente esta en TABLAS_EXENTAS (exencion vigilada).
        assertThat(jdbcTemplate.queryForObject("select count(*) from auditoria", Long.class)).isZero();
    }

    @Test
    void invoicePagadoEnFormatoEndiveLeeLaSubscriptionDeParentYMarcaActiva() {
        gestoria = gestoriaRepository.save(new Gestoria("Gestoria E2E invoice"));
        Suscripcion suscripcion = new Suscripcion();
        suscripcion.setGestoria(gestoria);
        suscripcion.setEstado(EstadoSuscripcion.TRIAL);
        suscripcion.setStripeSubscriptionId("sub_linked");
        suscripcionRepository.save(suscripcion);

        String payload = envoltorio("evt_invoice", "invoice.payment_succeeded", """
                {
                  "id": "in_e2e", "object": "invoice", "subscription": null,
                  "parent": {"type": "subscription_details",
                             "subscription_details": {"subscription": "sub_linked"}}
                }""");

        ResponseEntity<String> respuesta = enviar(payload);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        Suscripcion recargada = suscripcionRepository.findByStripeSubscriptionId("sub_linked").orElseThrow();
        assertThat(recargada.getEstado()).isEqualTo(EstadoSuscripcion.ACTIVA);
    }

    @Test
    void unEventoNoManejadoDevuelve200() {
        String payload = envoltorio("evt_charge", "charge.succeeded",
                "{\"id\": \"ch_e2e\", \"object\": \"charge\"}");

        ResponseEntity<String> respuesta = enviar(payload);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void elMismoCheckoutDosVecesNoDuplicaAltaPendiente() {
        String payload = envoltorio("evt_checkout", "checkout.session.completed", """
                {
                  "id": "cs_e2e", "object": "checkout.session",
                  "customer": "cus_e2e", "subscription": "sub_e2e",
                  "metadata": {"plan": "plus", "periodo": "anual", "prueba": "false", "source": "ganera-web"},
                  "customer_details": {"email": "e2e@example.com", "name": "Gestoría E2E"}
                }""");

        assertThat(enviar(payload).getStatusCode().value()).isEqualTo(200);
        assertThat(enviar(payload).getStatusCode().value()).isEqualTo(200);

        assertThat(altaPendienteRepository.findAll())
                .filteredOn(a -> a.getStripeSessionId().equals("cs_e2e"))
                .hasSize(1);
    }

    // --- ayudantes ---

    /** Envoltorio de evento Stripe (envelope) en formato endive, con un created reciente. */
    private static String envoltorio(String eventoId, String tipo, String objeto) {
        long created = Instant.now().getEpochSecond();
        return "{\"id\":\"" + eventoId + "\",\"object\":\"event\",\"api_version\":\"2026-09-30.endive\","
                + "\"created\":" + created + ",\"type\":\"" + tipo + "\",\"data\":{\"object\":" + objeto + "}}";
    }

    private ResponseEntity<String> enviar(String payload) {
        long ts = Instant.now().getEpochSecond();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Stripe-Signature", firmar(payload, ts));
        return restTemplate.exchange("/webhooks/stripe", HttpMethod.POST,
                new HttpEntity<>(payload, headers), String.class);
    }

    /** Firma Stripe real: "t=<ts>,v1=<hex HMAC-SHA256(secreto, ts + "." + payload)>". */
    private static String firmar(String payload, long ts) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRETO.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal((ts + "." + payload).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return "t=" + ts + ",v1=" + hex;
        } catch (Exception e) {
            throw new IllegalStateException("No se ha podido firmar el payload de prueba", e);
        }
    }
}
