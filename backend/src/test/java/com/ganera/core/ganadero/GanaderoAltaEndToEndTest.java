package com.ganera.core.ganadero;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganera.core.auth.LoginRequest;
import com.ganera.core.auth.LoginResponse;
import com.ganera.core.facturacion.SuscripcionRepository;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.gestoria.UsuarioRepository;
import com.ganera.core.onboarding.OnboardingRequest;
import com.ganera.core.onboarding.OnboardingResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E real (servidor embebido + TestRestTemplate) de POST /ganaderos (alta manual). Dos Gestorias
 * con su JWT: comprueba el alta válida, la validación (nombre y NIF), el duplicado dentro de la
 * gestoría, el aislamiento multi-tenant (un NIF creado por A no se crea ni se ve desde B, y B no
 * puede «ver» el de A ni al chocar con el UNIQUE global) y que sin token no se puede crear.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GanaderoAltaEndToEndTest {

    private static final String SECRETO_ONBOARDING = "test-onboarding-secret";
    /** DNI válido (12345678 % 23 = 14 -> 'Z'). */
    private static final String NIF_VALIDO = "12345678Z";

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private GanaderoRepository ganaderoRepository;
    @Autowired
    private SuscripcionRepository suscripcionRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private GestoriaRepository gestoriaRepository;

    private String tokenA;
    private String tokenB;

    @BeforeEach
    void preparar() {
        onboarding("Gestoria Alta A", "altaA@test.com");
        onboarding("Gestoria Alta B", "altaB@test.com");
        tokenA = login("altaA@test.com");
        tokenB = login("altaB@test.com");
    }

    /** E2E sobre HTTP: no hay rollback por test. Se limpia en orden de claves ajenas (ganaderos y
     * dependientes de la gestoría antes que la gestoría) para que cada método parta de cero. */
    @AfterEach
    void limpiar() {
        ganaderoRepository.deleteAll();
        suscripcionRepository.deleteAll();
        usuarioRepository.deleteAll();
        gestoriaRepository.deleteAll();
    }

    @Test
    void altaValidaDevuelve201YApareceSoloEnSuGestoria() throws Exception {
        ResponseEntity<String> respuesta = crear(tokenA, "Finca del Roble SL", " 12345678-z ");
        assertThat(respuesta.getStatusCode().value()).isEqualTo(201);

        JsonNode creado = objectMapper.readTree(respuesta.getBody());
        assertThat(creado.get("nombre").asText()).isEqualTo("Finca del Roble SL");
        // El NIF se guarda normalizado (sin espacios/guiones, en mayúsculas).
        assertThat(creado.get("nif").asText()).isEqualTo(NIF_VALIDO);
        assertThat(creado.get("numeroExplotaciones").asLong()).isZero();
        assertThat(creado.get("id").asLong()).isPositive();

        assertThat(nifsDe(tokenA)).contains(NIF_VALIDO);
        // Aislamiento: la gestoría B no lo ve.
        assertThat(nifsDe(tokenB)).doesNotContain(NIF_VALIDO);
    }

    @Test
    void nombreEnBlancoDa400() throws Exception {
        ResponseEntity<String> respuesta = crear(tokenA, "   ", NIF_VALIDO);
        assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
        assertThat(motivo(respuesta)).isEqualTo(GanaderoController.MOTIVO_NOMBRE_OBLIGATORIO);
    }

    @Test
    void nifInvalidoDa400() throws Exception {
        ResponseEntity<String> respuesta = crear(tokenA, "Ganadero X", "12345678A");
        assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
        assertThat(motivo(respuesta)).isEqualTo(GanaderoController.MOTIVO_NIF_INVALIDO);
    }

    @Test
    void nifDuplicadoEnLaMismaGestoriaDa409ConMotivoClaro() throws Exception {
        assertThat(crear(tokenA, "Primero", NIF_VALIDO).getStatusCode().value()).isEqualTo(201);
        ResponseEntity<String> repetido = crear(tokenA, "Otro nombre", NIF_VALIDO);
        assertThat(repetido.getStatusCode().value()).isEqualTo(409);
        assertThat(motivo(repetido)).isEqualTo(GanaderoAltaService.MOTIVO_NIF_DUPLICADO_PROPIO);
    }

    @Test
    void nifDeOtraGestoriaChocaConElUniqueGlobalPeroNoRevelaNadaNiLoCrea() throws Exception {
        assertThat(crear(tokenA, "De A", NIF_VALIDO).getStatusCode().value()).isEqualTo(201);

        ResponseEntity<String> desdeB = crear(tokenB, "Intento de B", NIF_VALIDO);
        assertThat(desdeB.getStatusCode().value()).isEqualTo(409);
        // Motivo que no revela que el NIF es de otra gestoría.
        assertThat(motivo(desdeB)).isEqualTo(GanaderoAltaService.MOTIVO_NIF_NO_DISPONIBLE);
        // No se ha creado nada en B.
        assertThat(nifsDe(tokenB)).doesNotContain(NIF_VALIDO);
    }

    @Test
    void sinTokenNoSePuedeCrear() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> respuesta = restTemplate.exchange(
                "/ganaderos", HttpMethod.POST,
                new HttpEntity<>(new GanaderoCrearRequest("Sin sesión", NIF_VALIDO), headers), String.class);
        assertThat(respuesta.getStatusCode().value()).isBetween(401, 403);
    }

    // --- helpers -----------------------------------------------------------------------------

    private ResponseEntity<String> crear(String token, String nombre, String nif) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange("/ganaderos", HttpMethod.POST,
                new HttpEntity<>(new GanaderoCrearRequest(nombre, nif), headers), String.class);
    }

    private List<String> nifsDe(String token) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<String> respuesta = restTemplate.exchange(
                "/ganaderos?size=100", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        JsonNode content = objectMapper.readTree(respuesta.getBody()).get("content");
        List<String> nifs = new ArrayList<>();
        content.forEach(fila -> nifs.add(fila.get("nif").asText()));
        return nifs;
    }

    private String motivo(ResponseEntity<String> respuesta) throws Exception {
        return objectMapper.readTree(respuesta.getBody()).get("motivo").asText();
    }

    private void onboarding(String nombreGestoria, String email) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Secret", SECRETO_ONBOARDING);
        ResponseEntity<OnboardingResponse> respuesta = restTemplate.postForEntity(
                "/internal/onboarding/gestoria",
                new HttpEntity<>(new OnboardingRequest(nombreGestoria, email, "password123", "Usuario"), headers),
                OnboardingResponse.class);
        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
    }

    private String login(String email) {
        ResponseEntity<LoginResponse> respuesta = restTemplate.postForEntity(
                "/auth/login", new LoginRequest(email, "password123"), LoginResponse.class);
        return respuesta.getBody().token();
    }
}
