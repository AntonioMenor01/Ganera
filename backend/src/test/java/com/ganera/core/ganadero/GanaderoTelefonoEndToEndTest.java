package com.ganera.core.ganadero;

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
import com.ganera.core.shared.auditoria.ActorTipo;
import com.ganera.core.shared.auditoria.Auditoria;
import com.ganera.core.shared.auditoria.AuditoriaRepository;
import com.ganera.core.shared.auditoria.EntidadAuditada;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E real (servidor embebido + TestRestTemplate) de PUT /ganaderos/{id}/telefono (plan 2026-11-10) con DOS
 * Gestorias y su JWT: poner/cambiar/borrar, el borrado explicito (A6: sin cuerpo o {} -> 400), la validacion
 * (400 antes de tocar la BD, mismo para propio/ajeno/inexistente), el 404 sin cuerpo (ajeno o inexistente) con su
 * fila ACCESO_OTRA_GESTORIA en la gestoria del actor, la auditoria exacta del PUT correcto (una sola fila
 * GANADERO_MODIFICADO, cero LECTURA_GANADERO, sin el telefono en el JSON), que el telefono no sale en listados ni
 * buscador (D9) y el 401 sin token. {@code Accept: application/json} explicito (ver la nota de TestRestTemplate en
 * CLAUDE.md).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(GuardiaDmlExtension.class)
class GanaderoTelefonoEndToEndTest {

    private static final String SECRETO_ONBOARDING = "test-onboarding-secret";
    private static final String NIF_A = "12345678Z"; // 12345678 % 23 = 14 -> 'Z'
    private static final String NIF_B = "87654321X"; // 87654321 % 23 = 10 -> 'X'

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
    private Long ganaderoA;
    private Long ganaderoB;

    @BeforeEach
    void preparar() throws Exception {
        onboarding("Gestoria Tel A", "telA@test.com");
        onboarding("Gestoria Tel B", "telB@test.com");
        tokenA = login("telA@test.com");
        tokenB = login("telB@test.com");
        Usuario a = usuarioRepository.findByEmail("telA@test.com").orElseThrow();
        usuarioIdA = a.getId();
        gestoriaIdA = a.getGestoria().getId();
        gestoriaIdB = usuarioRepository.findByEmail("telB@test.com").orElseThrow().getGestoria().getId();

        ganaderoA = crearGanadero(tokenA, "Finca Telefono A SL", NIF_A);
        ganaderoB = crearGanadero(tokenB, "Finca Telefono B SL", NIF_B);
        // Las filas del onboarding, login y las dos altas se borran para que cada test parta de auditoria vacia y
        // las afirmaciones de «una fila» / «ninguna fila» sean exactas (setup, antes de armarse la GuardiaDml).
        LimpiezaAuditoria.borrar(jdbcTemplate);
    }

    @AfterEach
    void limpiar() {
        LimpiezaAuditoria.borrar(jdbcTemplate);
        ganaderoRepository.deleteAll();
        suscripcionRepository.deleteAll();
        usuarioRepository.deleteAll();
        gestoriaRepository.deleteAll();
    }

    // --- poner / cambiar / borrar ---

    @Test
    void ponerCambiarYBorrarElTelefono() throws Exception {
        // Poner (escrito a mano -> E.164).
        ResponseEntity<String> puesto = put(tokenA, ganaderoA, "{\"telefono\":\"612 345 678\"}");
        assertThat(puesto.getStatusCode().value()).isEqualTo(200);
        assertThat(objectMapper.readTree(puesto.getBody()).get("telefono").asText()).isEqualTo("+34612345678");
        assertThat(telefonoEnBd(ganaderoA)).isEqualTo("+34612345678");

        // Cambiar.
        ResponseEntity<String> cambiado = put(tokenA, ganaderoA, "{\"telefono\":\"699 888 777\"}");
        assertThat(cambiado.getStatusCode().value()).isEqualTo(200);
        assertThat(telefonoEnBd(ganaderoA)).isEqualTo("+34699888777");

        // Borrar con null explicito.
        ResponseEntity<String> conNull = put(tokenA, ganaderoA, "{\"telefono\":null}");
        assertThat(conNull.getStatusCode().value()).isEqualTo(200);
        assertThat(objectMapper.readTree(conNull.getBody()).get("telefono").isNull()).isTrue();
        assertThat(telefonoEnBd(ganaderoA)).isNull();

        // Poner de nuevo y borrar con cadena vacia.
        assertThat(put(tokenA, ganaderoA, "{\"telefono\":\"612345678\"}").getStatusCode().value()).isEqualTo(200);
        ResponseEntity<String> conVacio = put(tokenA, ganaderoA, "{\"telefono\":\"   \"}");
        assertThat(conVacio.getStatusCode().value()).isEqualTo(200);
        assertThat(telefonoEnBd(ganaderoA)).isNull();
    }

    // --- auditoria del PUT correcto ---

    @Test
    void elPutCorrectoDejaExactamenteUnaFilaGanaderoModificadoYNingunaLecturaNiElTelefono() throws Exception {
        ResponseEntity<String> respuesta = put(tokenA, ganaderoA, "{\"telefono\":\"612 345 678\"}");
        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);

        List<Auditoria> filas = auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoriaIdA);
        assertThat(filas).hasSize(1);
        Auditoria fila = filas.get(0);
        assertThat(fila.getAccion()).isEqualTo(AccionAuditoria.GANADERO_MODIFICADO);
        assertThat(fila.getActorTipo()).isEqualTo(ActorTipo.USUARIO);
        assertThat(fila.getUsuarioId()).isEqualTo(usuarioIdA);
        assertThat(fila.getEntidadTipo()).isEqualTo(EntidadAuditada.GANADERO);
        assertThat(fila.getEntidadId()).isEqualTo(ganaderoA);
        assertThat(fila.getAntes()).isNull();
        assertThat(fila.getDespues()).isEqualTo("{\"campos_cambiados\":[\"TELEFONO\"],\"origen\":\"MANUAL\"}");
        // El JSON de la fila NUNCA lleva el telefono.
        assertThat(fila.getDespues()).doesNotContain("612345678");
        // A1: ninguna LECTURA_GANADERO, aunque la respuesta incluya el detalle.
        assertThat(filas).noneMatch(f -> f.getAccion() == AccionAuditoria.LECTURA_GANADERO);
    }

    @Test
    void reenviarElMismoValorDejaCamposCambiadosVacio() throws Exception {
        assertThat(put(tokenA, ganaderoA, "{\"telefono\":\"612345678\"}").getStatusCode().value()).isEqualTo(200);
        LimpiezaAuditoria.borrar(jdbcTemplate);

        ResponseEntity<String> otraVez = put(tokenA, ganaderoA, "{\"telefono\":\"612 345 678\"}");
        assertThat(otraVez.getStatusCode().value()).isEqualTo(200);

        List<Auditoria> filas = auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoriaIdA);
        assertThat(filas).hasSize(1);
        assertThat(filas.get(0).getAccion()).isEqualTo(AccionAuditoria.GANADERO_MODIFICADO);
        assertThat(filas.get(0).getDespues()).isEqualTo("{\"campos_cambiados\":[],\"origen\":\"MANUAL\"}");
    }

    // --- 400: borrado explicito (A6) y validacion, antes de tocar la BD ---

    @Test
    void sinCuerpoDa400SinCambiosNiFila() throws Exception {
        assertThat(put(tokenA, ganaderoA, "{\"telefono\":\"612345678\"}").getStatusCode().value()).isEqualTo(200);
        LimpiezaAuditoria.borrar(jdbcTemplate);

        HttpHeaders headers = cabeceras(tokenA);
        ResponseEntity<String> respuesta = restTemplate.exchange(
                "/ganaderos/" + ganaderoA + "/telefono", HttpMethod.PUT, new HttpEntity<>(headers), String.class);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
        assertThat(motivo(respuesta)).isEqualTo(GanaderoController.MOTIVO_TELEFONO_FALTA_CLAVE);
        assertThat(telefonoEnBd(ganaderoA)).isEqualTo("+34612345678");
        assertThat(auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoriaIdA)).isEmpty();
    }

    @Test
    void objetoVacioDa400SinCambiosNiFila() throws Exception {
        ResponseEntity<String> respuesta = put(tokenA, ganaderoA, "{}");
        assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
        assertThat(motivo(respuesta)).isEqualTo(GanaderoController.MOTIVO_TELEFONO_FALTA_CLAVE);
        assertThat(telefonoEnBd(ganaderoA)).isNull();
        assertThat(auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoriaIdA)).isEmpty();
    }

    @Test
    void valorNoTextualNiNullDa400() throws Exception {
        ResponseEntity<String> respuesta = put(tokenA, ganaderoA, "{\"telefono\":123}");
        assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
        assertThat(motivo(respuesta)).isEqualTo(GanaderoController.MOTIVO_TELEFONO_INVALIDO);
        assertThat(telefonoEnBd(ganaderoA)).isNull();
        assertThat(auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoriaIdA)).isEmpty();
    }

    @Test
    void telefonoInvalidoDa400SinCambiosNiFila() throws Exception {
        for (String malo : List.of("123", "+34123")) {
            ResponseEntity<String> respuesta = put(tokenA, ganaderoA, "{\"telefono\":\"" + malo + "\"}");
            assertThat(respuesta.getStatusCode().value()).as(malo).isEqualTo(400);
            assertThat(motivo(respuesta)).as(malo).isEqualTo(GanaderoController.MOTIVO_TELEFONO_INVALIDO);
        }
        assertThat(telefonoEnBd(ganaderoA)).isNull();
        assertThat(auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoriaIdA)).isEmpty();
    }

    @Test
    void elMismo400ConIdAjenoEInexistenteSinFilaDeAccesoAjeno() {
        // El 400 es previo a la BD: no se mira de quien es el id, asi que un id ajeno o inexistente con cuerpo
        // invalido da el mismo 400 y NO deja fila ACCESO_OTRA_GESTORIA.
        assertThat(put(tokenB, ganaderoA, "{\"telefono\":\"123\"}").getStatusCode().value()).isEqualTo(400);
        assertThat(put(tokenA, 999_999L, "{\"telefono\":\"123\"}").getStatusCode().value()).isEqualTo(400);
        assertThat(auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoriaIdA)).isEmpty();
        assertThat(auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoriaIdB)).isEmpty();
    }

    // --- 404 ---

    @Test
    void inexistenteConCuerpoValidoDa404SinCuerpoNiFila() {
        ResponseEntity<String> respuesta = put(tokenA, 999_999L, "{\"telefono\":\"612345678\"}");
        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
        assertThat(respuesta.getBody()).isNullOrEmpty();
        assertThat(auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoriaIdA)).isEmpty();
    }

    @Test
    void ajenoConCuerpoValidoDa404YDejaUnaFilaAccesoOtraGestoriaEnElActor() {
        // B intenta cambiar el telefono de un ganadero de A.
        ResponseEntity<String> respuesta = put(tokenB, ganaderoA, "{\"telefono\":\"612345678\"}");
        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
        assertThat(respuesta.getBody()).isNullOrEmpty();
        // El ganadero de A sigue sin telefono (nada cambio).
        assertThat(telefonoEnBd(ganaderoA)).isNull();
        // Exactamente una fila ACCESO_OTRA_GESTORIA en la gestoria de B (el actor), con EDITAR_GANADERO.
        List<Auditoria> enB = auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoriaIdB);
        assertThat(enB).hasSize(1);
        assertThat(enB.get(0).getAccion()).isEqualTo(AccionAuditoria.ACCESO_OTRA_GESTORIA);
        assertThat(enB.get(0).getEntidadTipo()).isEqualTo(EntidadAuditada.GANADERO);
        assertThat(enB.get(0).getEntidadId()).isEqualTo(ganaderoA);
        assertThat(enB.get(0).getDespues()).isEqualTo("{\"operacion\":\"EDITAR_GANADERO\"}");
        // Ninguna fila nueva en A.
        assertThat(auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoriaIdA)).isEmpty();
    }

    // --- no exposicion de credenciales y D9 (listados/buscador) ---

    @Test
    void laRespuestaDelPutNoContieneCredencialesOvz() {
        ResponseEntity<String> respuesta = put(tokenA, ganaderoA, "{\"telefono\":\"612345678\"}");
        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(respuesta.getBody().toLowerCase(java.util.Locale.ROOT)).doesNotContain("ovz");
    }

    @Test
    void elTelefonoNoSaleEnListadoNiBuscador() throws Exception {
        assertThat(put(tokenA, ganaderoA, "{\"telefono\":\"612345678\"}").getStatusCode().value()).isEqualTo(200);

        // GET /ganaderos: ninguna fila lleva la clave telefono ni los digitos.
        ResponseEntity<String> listado = exchange("/ganaderos", HttpMethod.GET, tokenA, null);
        assertThat(listado.getStatusCode().value()).isEqualTo(200);
        assertThat(listado.getBody()).doesNotContain("612345678").doesNotContain("+34612345678");
        objectMapper.readTree(listado.getBody()).get("content")
                .forEach(fila -> assertThat(fila.has("telefono")).isFalse());

        // GET /buscar: tampoco.
        ResponseEntity<String> buscar = exchange("/buscar?q=Finca", HttpMethod.GET, tokenA, null);
        assertThat(buscar.getStatusCode().value()).isEqualTo(200);
        assertThat(buscar.getBody()).doesNotContain("612345678").doesNotContain("+34612345678");

        // Pero el detalle SI lo trae.
        ResponseEntity<String> detalle = exchange("/ganaderos/" + ganaderoA, HttpMethod.GET, tokenA, null);
        assertThat(objectMapper.readTree(detalle.getBody()).get("telefono").asText()).isEqualTo("+34612345678");
    }

    // --- 401 ---

    @Test
    void sinJwtDa401() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        ResponseEntity<String> respuesta = restTemplate.exchange(
                "/ganaderos/" + ganaderoA + "/telefono", HttpMethod.PUT,
                new HttpEntity<>("{\"telefono\":\"612345678\"}", headers), String.class);
        assertThat(respuesta.getStatusCode().value()).isEqualTo(401);
    }

    // --- helpers ---

    private ResponseEntity<String> put(String token, Long ganaderoId, String cuerpoJson) {
        HttpHeaders headers = cabeceras(token);
        return restTemplate.exchange("/ganaderos/" + ganaderoId + "/telefono", HttpMethod.PUT,
                new HttpEntity<>(cuerpoJson, headers), String.class);
    }

    private ResponseEntity<String> exchange(String path, HttpMethod metodo, String token, String cuerpo) {
        HttpHeaders headers = cabeceras(token);
        return restTemplate.exchange(path, metodo,
                cuerpo == null ? new HttpEntity<>(headers) : new HttpEntity<>(cuerpo, headers), String.class);
    }

    private static HttpHeaders cabeceras(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    private Long crearGanadero(String token, String nombre, String nif) throws Exception {
        HttpHeaders headers = cabeceras(token);
        ResponseEntity<String> respuesta = restTemplate.exchange("/ganaderos", HttpMethod.POST,
                new HttpEntity<>(new GanaderoCrearRequest(nombre, nif, null), headers), String.class);
        assertThat(respuesta.getStatusCode().value()).isEqualTo(201);
        return objectMapper.readTree(respuesta.getBody()).get("id").asLong();
    }

    private String telefonoEnBd(Long ganaderoId) {
        return ganaderoRepository.findAll().stream()
                .filter(g -> g.getId().equals(ganaderoId)).findFirst().orElseThrow().getTelefono();
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
