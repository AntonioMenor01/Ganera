package com.ganera.core.tramite;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganera.core.auth.LoginRequest;
import com.ganera.core.auth.LoginResponse;
import com.ganera.core.explotacion.Explotacion;
import com.ganera.core.explotacion.ExplotacionRepository;
import com.ganera.core.facturacion.SuscripcionRepository;
import com.ganera.core.ganadero.Ganadero;
import com.ganera.core.ganadero.GanaderoRepository;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.gestoria.Usuario;
import com.ganera.core.gestoria.UsuarioRepository;
import com.ganera.core.onboarding.OnboardingRequest;
import com.ganera.core.onboarding.OnboardingResponse;
import com.ganera.core.shared.auditoria.AccionAuditoria;
import com.ganera.core.shared.auditoria.Auditoria;
import com.ganera.core.shared.auditoria.AuditoriaRepository;
import com.ganera.core.shared.auditoria.EntidadAuditada;
import com.ganera.core.shared.auditoria.GuardiaDmlExtension;
import com.ganera.core.shared.auditoria.LimpiezaAuditoria;
import com.ganera.core.shared.auditoria.OrigenAuditoria;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E de POST /tramites (alta MANUAL). Dos gestorías: alta válida, validación, aislamiento
 * multi-tenant y auditoría TRAMITE_CREADO con ORIGEN MANUAL.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(GuardiaDmlExtension.class)
class TramiteAltaEndToEndTest {

    private static final String SECRETO_ONBOARDING = "test-onboarding-secret";

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TramiteRepository tramiteRepository;
    @Autowired
    private TramiteCrotalRepository tramiteCrotalRepository;
    @Autowired
    private ExplotacionRepository explotacionRepository;
    @Autowired
    private GanaderoRepository ganaderoRepository;
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
    private Long usuarioIdA;
    private Long explotacionA;
    private Long explotacionB;

    @BeforeEach
    void preparar() {
        onboarding("Gestoria Tramite Alta A", "tramiteAltaA@test.com");
        onboarding("Gestoria Tramite Alta B", "tramiteAltaB@test.com");
        tokenA = login("tramiteAltaA@test.com");
        tokenB = login("tramiteAltaB@test.com");
        Usuario a = usuarioRepository.findByEmail("tramiteAltaA@test.com").orElseThrow();
        usuarioIdA = a.getId();
        gestoriaIdA = a.getGestoria().getId();
        Gestoria gestoriaA = a.getGestoria();
        Gestoria gestoriaB = usuarioRepository.findByEmail("tramiteAltaB@test.com").orElseThrow().getGestoria();
        explotacionA = crearExplotacion(gestoriaA, "Ganadero Alta A", "11111111H", "ES880000000001", "Finca Alta A").getId();
        explotacionB = crearExplotacion(gestoriaB, "Ganadero Alta B", "22222222J", "ES880000000002", "Finca Alta B").getId();
        LimpiezaAuditoria.borrar(jdbcTemplate);
    }

    @AfterEach
    void limpiar() {
        LimpiezaAuditoria.borrar(jdbcTemplate);
        tramiteCrotalRepository.deleteAll();
        tramiteRepository.deleteAll();
        explotacionRepository.deleteAll();
        ganaderoRepository.deleteAll();
        suscripcionRepository.deleteAll();
        usuarioRepository.deleteAll();
        gestoriaRepository.deleteAll();
    }

    @Test
    void altaManualValidaDevuelve201ConOrigenManualYAuditoria() throws Exception {
        ResponseEntity<String> respuesta = crear(tokenA, Map.of(
                "tipoTramite", "BAJA_MUERTE",
                "explotacionId", explotacionA,
                "crotales", List.of("1234"),
                "fechaHecho", "2026-03-01"));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(201);
        JsonNode json = objectMapper.readTree(respuesta.getBody());
        assertThat(json.get("tipoTramite").asText()).isEqualTo("BAJA_MUERTE");
        assertThat(json.get("estado").asText()).isEqualTo("PENDIENTE_REVISION");
        assertThat(json.get("origen").asText()).isEqualTo("MANUAL");
        assertThat(json.get("explotacionId").asLong()).isEqualTo(explotacionA);
        assertThat(json.get("fechaHecho").asText()).isEqualTo("2026-03-01");
        assertThat(json.get("crotales")).hasSize(1);
        assertThat(json.get("whatsapp").isNull()).isTrue();

        assertThat(tramiteRepository.findByIdAndGestoriaId(json.get("id").asLong(), gestoriaIdA)).isPresent();
        assertThat(listarIds(tokenB)).doesNotContain(json.get("id").asLong());

        List<Auditoria> filas = auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoriaIdA).stream()
                .filter(a -> a.getAccion() == AccionAuditoria.TRAMITE_CREADO)
                .toList();
        assertThat(filas).hasSize(1);
        assertThat(filas.get(0).getEntidadTipo()).isEqualTo(EntidadAuditada.TRAMITE);
        assertThat(filas.get(0).getUsuarioId()).isEqualTo(usuarioIdA);
        assertThat(objectMapper.readTree(filas.get(0).getDespues()).get("origen").asText())
                .isEqualTo(OrigenAuditoria.MANUAL.name());
    }

    @Test
    void altaSinTipoOSinExplotacionDevuelve400() {
        assertThat(crear(tokenA, Map.of("explotacionId", explotacionA)).getStatusCode().value()).isEqualTo(400);
        assertThat(crear(tokenA, Map.of("tipoTramite", "BAJA_MUERTE")).getStatusCode().value()).isEqualTo(400);
        assertThat(crear(tokenA, Map.of("tipoTramite", "NO_EXISTE", "explotacionId", explotacionA))
                .getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void altaConExplotacionDeOtraGestoriaDevuelve404SinCuerpo() {
        ResponseEntity<String> respuesta = crear(tokenA, Map.of(
                "tipoTramite", "BAJA_MUERTE",
                "explotacionId", explotacionB));
        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
        assertThat(respuesta.getBody()).isNullOrEmpty();
        assertThat(tramiteRepository.findAll()).isEmpty();
    }

    @Test
    void sinJwtDevuelve401() {
        ResponseEntity<String> respuesta = restTemplate.postForEntity("/tramites",
                new HttpEntity<>(Map.of("tipoTramite", "BAJA_MUERTE", "explotacionId", explotacionA),
                        jsonHeaders(null)),
                String.class);
        assertThat(respuesta.getStatusCode().value()).isEqualTo(401);
    }

    private ResponseEntity<String> crear(String token, Map<String, Object> cuerpo) {
        return restTemplate.exchange("/tramites", HttpMethod.POST,
                new HttpEntity<>(cuerpo, jsonHeaders(token)), String.class);
    }

    private List<Long> listarIds(String token) throws Exception {
        ResponseEntity<String> respuesta = restTemplate.exchange("/tramites?size=50", HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(token)), String.class);
        JsonNode content = objectMapper.readTree(respuesta.getBody()).get("content");
        java.util.ArrayList<Long> ids = new java.util.ArrayList<>();
        content.forEach(n -> ids.add(n.get("id").asLong()));
        return ids;
    }

    private HttpHeaders jsonHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }

    private Explotacion crearExplotacion(Gestoria gestoria, String nombreGanadero, String nif,
                                         String codigoRega, String nombreExplotacion) {
        Ganadero ganadero = new Ganadero();
        ganadero.setGestoria(gestoria);
        ganadero.setNombre(nombreGanadero);
        ganadero.setNif(nif);
        ganaderoRepository.save(ganadero);
        Explotacion explotacion = new Explotacion();
        explotacion.setGestoria(gestoria);
        explotacion.setGanadero(ganadero);
        explotacion.setCodigoRega(codigoRega);
        explotacion.setNombre(nombreExplotacion);
        return explotacionRepository.save(explotacion);
    }

    private void onboarding(String nombreGestoria, String email) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Secret", SECRETO_ONBOARDING);
        ResponseEntity<OnboardingResponse> respuesta = restTemplate.postForEntity(
                "/internal/onboarding/gestoria",
                new HttpEntity<>(new OnboardingRequest(nombreGestoria, email, "password123", "Usuario"), headers),
                OnboardingResponse.class);
        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
    }

    private String login(String email) {
        ResponseEntity<LoginResponse> respuesta = restTemplate.postForEntity("/auth/login",
                new LoginRequest(email, "password123"), LoginResponse.class);
        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        return respuesta.getBody().token();
    }
}
