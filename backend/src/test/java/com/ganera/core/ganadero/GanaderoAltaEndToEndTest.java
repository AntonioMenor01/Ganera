package com.ganera.core.ganadero;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganera.core.auth.LoginRequest;
import com.ganera.core.auth.LoginResponse;
import com.ganera.core.facturacion.SuscripcionRepository;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.gestoria.UsuarioRepository;
import com.ganera.core.gestoria.Usuario;
import com.ganera.core.onboarding.OnboardingRequest;
import com.ganera.core.onboarding.OnboardingResponse;
import com.ganera.core.shared.auditoria.AccionAuditoria;
import com.ganera.core.shared.auditoria.ActorTipo;
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
@ExtendWith(GuardiaDmlExtension.class)
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
    @Autowired
    private AuditoriaRepository auditoriaRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String tokenA;
    private String tokenB;
    private Long gestoriaIdA;
    private Long gestoriaIdB;
    private Long usuarioIdA;

    @BeforeEach
    void preparar() {
        onboarding("Gestoria Alta A", "altaA@test.com");
        onboarding("Gestoria Alta B", "altaB@test.com");
        tokenA = login("altaA@test.com");
        tokenB = login("altaB@test.com");
        Usuario a = usuarioRepository.findByEmail("altaA@test.com").orElseThrow();
        usuarioIdA = a.getId();
        gestoriaIdA = a.getGestoria().getId();
        gestoriaIdB = usuarioRepository.findByEmail("altaB@test.com").orElseThrow().getGestoria().getId();
        // El onboarding y el login dejan filas de auditoría (GESTORIA_CREADA, USUARIO_CREADO,
        // SUSCRIPCION_CREADA, LOGIN_CORRECTO): se borran para que cada test parta de auditoría vacía y
        // las afirmaciones de «una fila» / «ninguna fila» sean exactas. Es setup (antes de que la
        // GuardiaDmlExtension se arme), así que no cuenta como escritura del escenario.
        LimpiezaAuditoria.borrar(jdbcTemplate);
    }

    /** E2E sobre HTTP: no hay rollback por test. Se vacía primero `auditoria` (las filas confirmadas
     * tienen FK sin ON DELETE a usuario/gestoría) y luego en orden de claves ajenas, para que cada
     * método parta de cero. */
    @AfterEach
    void limpiar() {
        LimpiezaAuditoria.borrar(jdbcTemplate);
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

        // Auditoría: EXACTAMENTE una fila GANADERO_CREADO en la gestoría de A (actor USUARIO del JWT,
        // entidad GANADERO con el id creado, origen MANUAL) y NINGUNA en B.
        List<Auditoria> filasA = auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoriaIdA);
        assertThat(filasA).hasSize(1);
        Auditoria fila = filasA.get(0);
        assertThat(fila.getAccion()).isEqualTo(AccionAuditoria.GANADERO_CREADO);
        assertThat(fila.getActorTipo()).isEqualTo(ActorTipo.USUARIO);
        assertThat(fila.getUsuarioId()).isEqualTo(usuarioIdA);
        assertThat(fila.getEntidadTipo()).isEqualTo(EntidadAuditada.GANADERO);
        assertThat(fila.getEntidadId()).isEqualTo(creado.get("id").asLong());
        // El JSON canónico usa la clave en minúsculas; el valor es el name() del enum.
        assertThat(objectMapper.readTree(fila.getDespues()).get("origen").asText())
                .isEqualTo(OrigenAuditoria.MANUAL.name());
        assertThat(auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoriaIdB)).isEmpty();
    }

    @Test
    void altaConTelefonoEscritoAManoSeGuardaNormalizadoYNoSaleEnLaRespuesta() throws Exception {
        // Nombre SIN la palabra «telefono» a proposito: asi el doesNotContain de abajo prueba la ausencia del campo,
        // no se confunde con el nombre.
        ResponseEntity<String> respuesta = crear(tokenA, "Finca del Olmo SL", NIF_VALIDO, "612 345 678");
        assertThat(respuesta.getStatusCode().value()).isEqualTo(201);

        // D9: la respuesta del alta (GanaderoResumenResponse) NO lleva el telefono ni sus digitos.
        assertThat(respuesta.getBody()).doesNotContain("telefono").doesNotContain("612345678");
        JsonNode creado = objectMapper.readTree(respuesta.getBody());
        assertThat(creado.has("telefono")).isFalse();

        // Se ha guardado normalizado a E.164.
        Ganadero ganadero = ganaderoRepository.findAll().stream()
                .filter(g -> NIF_VALIDO.equals(g.getNif())).findFirst().orElseThrow();
        assertThat(ganadero.getTelefono()).isEqualTo("+34612345678");

        // D5: GANADERO_CREADO identico (no deja constancia de si se informo telefono): solo ORIGEN MANUAL, sin
        // el telefono en ningun sitio.
        List<Auditoria> filasA = auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoriaIdA);
        assertThat(filasA).hasSize(1);
        Auditoria fila = filasA.get(0);
        assertThat(fila.getAccion()).isEqualTo(AccionAuditoria.GANADERO_CREADO);
        assertThat(fila.getDespues()).isEqualTo("{\"origen\":\"MANUAL\"}");
        assertThat(fila.getAntes()).isNull();
        assertThat(fila.getDespues()).doesNotContain("612345678");
    }

    @Test
    void altaSinTelefonoLoDejaNull() {
        assertThat(crear(tokenA, "Finca sin telefono", NIF_VALIDO).getStatusCode().value()).isEqualTo(201);
        Ganadero ganadero = ganaderoRepository.findAll().stream()
                .filter(g -> NIF_VALIDO.equals(g.getNif())).findFirst().orElseThrow();
        assertThat(ganadero.getTelefono()).isNull();
    }

    @Test
    void altaConTelefonoInvalidoDa400SinCrearNadaNiAuditar() throws Exception {
        ResponseEntity<String> respuesta = crear(tokenA, "Finca telefono malo", NIF_VALIDO, "123");
        assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
        assertThat(motivo(respuesta)).isEqualTo(GanaderoController.MOTIVO_TELEFONO_INVALIDO);
        // Nada creado y ninguna fila de auditoria (el 400 es previo a tocar la BD).
        assertThat(ganaderoRepository.findAll()).isEmpty();
        assertThat(auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoriaIdA)).isEmpty();
    }

    @Test
    void elTelefonoSeValidaDespuesDelNif() throws Exception {
        // NIF invalido Y telefono invalido: gana el 400 del NIF (se comprueba antes).
        ResponseEntity<String> respuesta = crear(tokenA, "Finca", "12345678A", "123");
        assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
        assertThat(motivo(respuesta)).isEqualTo(GanaderoController.MOTIVO_NIF_INVALIDO);
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
        // El 409 (rollback) no deja fila: solo la del alta válida del primero.
        assertThat(auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoriaIdA))
                .extracting(Auditoria::getAccion)
                .containsExactly(AccionAuditoria.GANADERO_CREADO);
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
        // El 409 por UNIQUE global (rollback) no deja NINGUNA fila de auditoría en B; A tiene la suya.
        assertThat(auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoriaIdB)).isEmpty();
        assertThat(auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoriaIdA))
                .extracting(Auditoria::getAccion)
                .containsExactly(AccionAuditoria.GANADERO_CREADO);
    }

    @Test
    void sinTokenNoSePuedeCrear() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> respuesta = restTemplate.exchange(
                "/ganaderos", HttpMethod.POST,
                new HttpEntity<>(new GanaderoCrearRequest("Sin sesión", NIF_VALIDO, null), headers), String.class);
        assertThat(respuesta.getStatusCode().value()).isBetween(401, 403);
    }

    // --- helpers -----------------------------------------------------------------------------

    private ResponseEntity<String> crear(String token, String nombre, String nif) {
        return crear(token, nombre, nif, null);
    }

    private ResponseEntity<String> crear(String token, String nombre, String nif, String telefono) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange("/ganaderos", HttpMethod.POST,
                new HttpEntity<>(new GanaderoCrearRequest(nombre, nif, telefono), headers), String.class);
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
