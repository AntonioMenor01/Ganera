package com.ganera.core.shared.auditoria;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.gestoria.Usuario;
import com.ganera.core.gestoria.UsuarioRepository;
import com.ganera.core.facturacion.SuscripcionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Auditoria base — T4 (procesos de SISTEMA por HTTP): el alta interna de una gestoria piloto
 * (POST /internal/onboarding/gestoria) y el alta publica (POST /gestorias/registro, abierta solo en
 * este test). Cada una deja sus filas en la gestoria recien creada con actor SISTEMA, usuario_id NULL,
 * ip de loopback y request_id = la cabecera X-Request-Id de ESA respuesta. Los webhooks de Twilio, el de
 * Stripe, el callback de estado y los planificadores se prueban en sus paquetes (recepcion en
 * WhatsAppRecepcionEndToEndTest, Stripe en StripeWebhookServiceTest, sincronizacion en
 * SuscripcionSyncSchedulerAuditoriaTest, extraccion en ExtraccionTramiteServiceTest, retencion en
 * RetencionMensajesServiceTest/RetencionPorGestoriaPostgresTest, callback en TwilioEstadoWebhookEndToEndTest).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "ganera.registro.abierto=true",
        "ganera.onboarding.secret=" + AuditoriaProcesosTest.SECRETO})
class AuditoriaProcesosTest {

    static final String SECRETO = "secreto-onboarding-auditoria";

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private AuditoriaRepository auditoriaRepository;
    @Autowired
    private SuscripcionRepository suscripcionRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private GestoriaRepository gestoriaRepository;

    @AfterEach
    void limpiar() {
        LimpiezaAuditoria.borrar(jdbcTemplate);
        suscripcionRepository.deleteAll();
        usuarioRepository.deleteAll();
        gestoriaRepository.deleteAll();
    }

    @Test
    void elOnboardingInternoDejaLasTresFilasDeSistema() throws IOException {
        String body = "{\"nombreGestoria\":\"Gestoria Onboarding\",\"emailUsuario\":\"onb@test.com\","
                + "\"passwordUsuario\":\"password123\",\"nombreUsuario\":\"Empleado\"}";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Secret", SECRETO);
        ResponseEntity<String> r = restTemplate.postForEntity("/internal/onboarding/gestoria",
                new HttpEntity<>(body, headers), String.class);
        assertThat(r.getStatusCode().value()).isEqualTo(200);

        long gestoriaId = objectMapper.readTree(r.getBody()).get("gestoriaId").asLong();
        long usuarioId = objectMapper.readTree(r.getBody()).get("usuarioId").asLong();
        List<Auditoria> filas = auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoriaId);
        assertThat(filas).extracting(Auditoria::getAccion).containsExactly(
                AccionAuditoria.GESTORIA_CREADA, AccionAuditoria.USUARIO_CREADO, AccionAuditoria.SUSCRIPCION_CREADA);
        for (Auditoria fila : filas) {
            assertThat(fila.getActorTipo()).isEqualTo(ActorTipo.SISTEMA);
            assertThat(fila.getUsuarioId()).isNull();
            assertThat(fila.getIp()).isIn("127.0.0.1", "0:0:0:0:0:0:0:1");
            assertThat(fila.getRequestId()).isNotBlank().isEqualTo(r.getHeaders().getFirst("X-Request-Id"));
        }
        assertThat(filas.get(0).getEntidadId()).isEqualTo(gestoriaId);
        assertThat(filas.get(1).getEntidadTipo()).isEqualTo(EntidadAuditada.USUARIO);
        assertThat(filas.get(1).getEntidadId()).isEqualTo(usuarioId);
        assertThat(despues(filas.get(2)).get("estado").asText()).isEqualTo("ACTIVA");
    }

    @Test
    void elRegistroPublicoDejaGestoriaYUsuarioConOrigenRegistro() throws IOException {
        // Stripe sin configurar -> el checkout devuelve 503, pero crearGestoriaYUsuario ya ha confirmado la
        // gestoria, el usuario y sus dos filas de auditoria (SUSCRIPCION_CREADA/CANTIDAD_FIJADA necesitan Stripe).
        String body = "{\"nombreGestoria\":\"Gestoria Registro\",\"nombreUsuario\":\"Empleado\","
                + "\"email\":\"registro@test.com\",\"password\":\"password123\",\"rangoClientes\":\"UNO_A_DIEZ\"}";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> r = restTemplate.postForEntity("/gestorias/registro",
                new HttpEntity<>(body, headers), String.class);
        assertThat(r.getStatusCode().value()).isEqualTo(503);

        Usuario usuario = usuarioRepository.findByEmail("registro@test.com").orElseThrow();
        long gestoriaId = usuario.getGestoria().getId();
        List<Auditoria> filas = auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoriaId);
        assertThat(filas).extracting(Auditoria::getAccion)
                .containsExactly(AccionAuditoria.GESTORIA_CREADA, AccionAuditoria.USUARIO_CREADO);
        assertThat(filas).allSatisfy(fila -> {
            assertThat(fila.getActorTipo()).isEqualTo(ActorTipo.SISTEMA);
            assertThat(fila.getUsuarioId()).isNull();
            assertThat(fila.getIp()).isIn("127.0.0.1", "0:0:0:0:0:0:0:1");
            assertThat(fila.getRequestId()).isNotBlank();
        });
        assertThat(despues(filas.get(0)).get("origen").asText()).isEqualTo("REGISTRO");
        assertThat(despues(filas.get(1)).get("origen").asText()).isEqualTo("REGISTRO");
    }

    private JsonNode despues(Auditoria fila) throws IOException {
        return objectMapper.readTree(fila.getDespues());
    }
}
