package com.ganera.core.ovz;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganera.core.auth.LoginRequest;
import com.ganera.core.auth.LoginResponse;
import com.ganera.core.facturacion.SuscripcionRepository;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.gestoria.Usuario;
import com.ganera.core.gestoria.UsuarioRepository;
import com.ganera.core.onboarding.OnboardingRequest;
import com.ganera.core.onboarding.OnboardingResponse;
import com.ganera.core.shared.auditoria.AccionAuditoria;
import com.ganera.core.shared.auditoria.AuditoriaRepository;
import com.ganera.core.shared.auditoria.GuardiaDmlExtension;
import com.ganera.core.shared.auditoria.LimpiezaAuditoria;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(GuardiaDmlExtension.class)
class PreferenciaOvzEndToEndTest {

    private static final String SECRETO_ONBOARDING = "test-onboarding-secret";

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PreferenciaOvzRepository preferenciaOvzRepository;
    @Autowired
    private SuscripcionRepository suscripcionRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private GestoriaRepository gestoriaRepository;
    @Autowired
    private AuditoriaRepository auditoriaRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String tokenA;
    private String tokenB;
    private Long gestoriaIdA;

    @BeforeEach
    void preparar() {
        onboarding("Gestoria Pref A", "prefA@test.com");
        onboarding("Gestoria Pref B", "prefB@test.com");
        tokenA = login("prefA@test.com");
        tokenB = login("prefB@test.com");
        gestoriaIdA = usuarioRepository.findByEmail("prefA@test.com").orElseThrow().getGestoria().getId();
        LimpiezaAuditoria.borrar(jdbcTemplate);
    }

    @AfterEach
    void limpiar() {
        LimpiezaAuditoria.borrar(jdbcTemplate);
        preferenciaOvzRepository.deleteAll();
        suscripcionRepository.deleteAll();
        usuarioRepository.deleteAll();
        gestoriaRepository.deleteAll();
    }

    @Test
    void getVacioYPutGuardaYAislaEntreGestorias() throws Exception {
        ResponseEntity<String> vacio = restTemplate.exchange(
                "/configuracion/preferencias-ovz", HttpMethod.GET, conToken(tokenA), String.class);
        assertThat(vacio.getStatusCode().value()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(vacio.getBody());
        assertThat(body.get("oficinaDi").isNull()).isTrue();

        Map<String, Object> payload = Map.of(
                "oficinaDi", "MERIDA",
                "oficinaMer", "BADAJOZ",
                "telefonoMovimiento", "600111222",
                "medioTransporte", "Camión");
        ResponseEntity<String> put = restTemplate.exchange(
                "/configuracion/preferencias-ovz", HttpMethod.PUT,
                conTokenJson(tokenA, payload), String.class);
        assertThat(put.getStatusCode().value()).isEqualTo(200);
        JsonNode guardado = objectMapper.readTree(put.getBody());
        assertThat(guardado.get("oficinaDi").asText()).isEqualTo("MERIDA");
        assertThat(guardado.get("telefonoMovimiento").asText()).isEqualTo("+34600111222");

        ResponseEntity<String> deB = restTemplate.exchange(
                "/configuracion/preferencias-ovz", HttpMethod.GET, conToken(tokenB), String.class);
        assertThat(objectMapper.readTree(deB.getBody()).get("oficinaDi").isNull()).isTrue();

        assertThat(auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoriaIdA))
                .anyMatch(a -> a.getAccion() == AccionAuditoria.PREFERENCIA_OVZ_GUARDADA);
    }

    @Test
    void listasOvzIncluyenTransporteYMarcanProvisionales() throws Exception {
        ResponseEntity<String> resp = restTemplate.exchange(
                "/configuracion/listas-ovz", HttpMethod.GET, conToken(tokenA), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        JsonNode listas = objectMapper.readTree(resp.getBody()).get("listas");
        assertThat(listas.isArray()).isTrue();
        boolean transporte = false;
        boolean sexoProvisional = false;
        for (JsonNode lista : listas) {
            if ("medio_transporte".equals(lista.get("id").asText())) {
                transporte = true;
                assertThat(lista.get("provisional").asBoolean()).isFalse();
                assertThat(lista.get("valores").toString()).contains("Camión");
            }
            if ("sexo".equals(lista.get("id").asText())) {
                sexoProvisional = lista.get("provisional").asBoolean();
            }
        }
        assertThat(transporte).isTrue();
        assertThat(sexoProvisional).isTrue();
    }

    @Test
    void alertasSinTramitesVacias() {
        ResponseEntity<String> resp = restTemplate.exchange(
                "/alertas", HttpMethod.GET, conToken(tokenA), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isEqualTo("[]");
    }

    private void onboarding(String nombre, String email) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Secret", SECRETO_ONBOARDING);
        ResponseEntity<OnboardingResponse> resp = restTemplate.postForEntity(
                "/internal/onboarding/gestoria",
                new HttpEntity<>(new OnboardingRequest(nombre, email, "password123", "Prueba"), headers),
                OnboardingResponse.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    private String login(String email) {
        ResponseEntity<LoginResponse> resp = restTemplate.postForEntity(
                "/auth/login", new LoginRequest(email, "password123"), LoginResponse.class);
        assertThat(resp.getBody()).isNotNull();
        return resp.getBody().token();
    }

    private HttpEntity<Void> conToken(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    private HttpEntity<Object> conTokenJson(String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
