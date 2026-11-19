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
 * E2E real (servidor embebido + TestRestTemplate) de PUT y DELETE /ganaderos/{id}/credenciales-ovz (escalon 2, T1)
 * con DOS Gestorias y su JWT: guardar/modificar/borrar, el flag de certificado, la validacion (400 antes de tocar
 * la BD), el 404 sin cuerpo (ajeno o inexistente) con su fila ACCESO_OTRA_GESTORIA en la gestoria del actor, la
 * auditoria exacta (CREDENCIAL_OVZ_GUARDADA/_MODIFICADA/_BORRADA, sin usuario ni contrasena en el JSON), que la
 * contrasena se guarda cifrada y NUNCA vuelve en ninguna respuesta (ni en el detalle), y el 401 sin token.
 * Contrasenas y usuarios INVENTADOS solo para el test. {@code Accept: application/json} explicito (nota de
 * TestRestTemplate en CLAUDE.md).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(GuardiaDmlExtension.class)
class CredencialesOvzEndToEndTest {

    private static final String SECRETO_ONBOARDING = "test-onboarding-secret";
    private static final String NIF_A = "12345678Z";
    private static final String USUARIO_OVZ = "usuario-ovz-inventado";
    private static final String PASSWORD_OVZ = "contrasena-ovz-inventada-A";

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

    @BeforeEach
    void preparar() throws Exception {
        onboarding("Gestoria Cred A", "credA@test.com");
        onboarding("Gestoria Cred B", "credB@test.com");
        tokenA = login("credA@test.com");
        tokenB = login("credB@test.com");
        Usuario a = usuarioRepository.findByEmail("credA@test.com").orElseThrow();
        usuarioIdA = a.getId();
        gestoriaIdA = a.getGestoria().getId();
        gestoriaIdB = usuarioRepository.findByEmail("credB@test.com").orElseThrow().getGestoria().getId();
        ganaderoA = crearGanadero(tokenA, "Finca Credenciales A SL", NIF_A);
        LimpiezaAuditoria.borrar(jdbcTemplate);
    }

    @AfterEach
    void limpiar() {
        LimpiezaAuditoria.borrar(jdbcTemplate);
        ganaderoRepository.deleteAllInBatch();
        suscripcionRepository.deleteAll();
        usuarioRepository.deleteAll();
        gestoriaRepository.deleteAll();
    }

    // --- guardar / modificar / borrar ---

    @Test
    void guardarCredencialesLasCifraYNoLasDevuelve() throws Exception {
        ResponseEntity<String> guardado = put(tokenA, ganaderoA,
                "{\"usuario\":\"" + USUARIO_OVZ + "\",\"password\":\"" + PASSWORD_OVZ + "\",\"usaCertificado\":false}");
        assertThat(guardado.getStatusCode().value()).isEqualTo(200);

        JsonNode cuerpo = objectMapper.readTree(guardado.getBody());
        assertThat(cuerpo.get("tieneCredencialesOvz").asBoolean()).isTrue();
        assertThat(cuerpo.get("usaCertificadoOvz").asBoolean()).isFalse();
        // La contrasena y el usuario NUNCA vuelven en la respuesta.
        assertThat(guardado.getBody()).doesNotContain(PASSWORD_OVZ).doesNotContain(USUARIO_OVZ)
                .doesNotContain("ovzPassword").doesNotContain("ovzUsuario").doesNotContain("password");

        // Cifrada en la BD: ni es el texto plano ni lo contiene.
        String cifrado = cifradoEnBd(ganaderoA);
        assertThat(cifrado).isNotBlank().isNotEqualTo(PASSWORD_OVZ).doesNotContain(PASSWORD_OVZ);

        // Una fila CREDENCIAL_OVZ_GUARDADA, sin el usuario ni la contrasena.
        List<Auditoria> filas = auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoriaIdA);
        assertThat(filas).hasSize(1);
        Auditoria fila = filas.get(0);
        assertThat(fila.getAccion()).isEqualTo(AccionAuditoria.CREDENCIAL_OVZ_GUARDADA);
        assertThat(fila.getActorTipo()).isEqualTo(ActorTipo.USUARIO);
        assertThat(fila.getUsuarioId()).isEqualTo(usuarioIdA);
        assertThat(fila.getEntidadTipo()).isEqualTo(EntidadAuditada.GANADERO);
        assertThat(fila.getEntidadId()).isEqualTo(ganaderoA);
        assertThat(fila.getDespues())
                .isEqualTo("{\"campos_cambiados\":[\"USUARIO\",\"PASSWORD\"],\"origen\":\"MANUAL\"}");
        assertThat(fila.getDespues()).doesNotContain(PASSWORD_OVZ).doesNotContain(USUARIO_OVZ);
    }

    @Test
    void volverAGuardarDejaCredencialOvzModificada() throws Exception {
        assertThat(put(tokenA, ganaderoA,
                "{\"usuario\":\"" + USUARIO_OVZ + "\",\"password\":\"" + PASSWORD_OVZ + "\"}")
                .getStatusCode().value()).isEqualTo(200);
        LimpiezaAuditoria.borrar(jdbcTemplate);

        ResponseEntity<String> otra = put(tokenA, ganaderoA,
                "{\"usuario\":\"" + USUARIO_OVZ + "\",\"password\":\"otra-contrasena-inventada\"}");
        assertThat(otra.getStatusCode().value()).isEqualTo(200);

        List<Auditoria> filas = auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoriaIdA);
        assertThat(filas).hasSize(1);
        assertThat(filas.get(0).getAccion()).isEqualTo(AccionAuditoria.CREDENCIAL_OVZ_MODIFICADA);
        // El usuario no cambio: solo PASSWORD en campos_cambiados.
        assertThat(filas.get(0).getDespues())
                .isEqualTo("{\"campos_cambiados\":[\"PASSWORD\"],\"origen\":\"MANUAL\"}");
    }

    @Test
    void marcarCertificadoBorraUsuarioYContrasenaYQuedaModificada() throws Exception {
        assertThat(put(tokenA, ganaderoA,
                "{\"usuario\":\"" + USUARIO_OVZ + "\",\"password\":\"" + PASSWORD_OVZ + "\"}")
                .getStatusCode().value()).isEqualTo(200);
        LimpiezaAuditoria.borrar(jdbcTemplate);

        ResponseEntity<String> cert = put(tokenA, ganaderoA, "{\"usaCertificado\":true}");
        assertThat(cert.getStatusCode().value()).isEqualTo(200);
        JsonNode cuerpo = objectMapper.readTree(cert.getBody());
        assertThat(cuerpo.get("usaCertificadoOvz").asBoolean()).isTrue();
        assertThat(cuerpo.get("tieneCredencialesOvz").asBoolean()).isFalse();
        // La contrasena cifrada se borro en la BD.
        assertThat(cifradoEnBd(ganaderoA)).isNull();

        List<Auditoria> filas = auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoriaIdA);
        assertThat(filas).hasSize(1);
        assertThat(filas.get(0).getAccion()).isEqualTo(AccionAuditoria.CREDENCIAL_OVZ_MODIFICADA);
        assertThat(filas.get(0).getDespues()).isEqualTo(
                "{\"campos_cambiados\":[\"USUARIO\",\"PASSWORD\",\"USA_CERTIFICADO\"],\"origen\":\"MANUAL\"}");
    }

    @Test
    void certificadoSinUsuarioNiContrasenaSeAcepta() throws Exception {
        ResponseEntity<String> cert = put(tokenA, ganaderoA, "{\"usaCertificado\":true}");
        assertThat(cert.getStatusCode().value()).isEqualTo(200);
        JsonNode cuerpo = objectMapper.readTree(cert.getBody());
        assertThat(cuerpo.get("usaCertificadoOvz").asBoolean()).isTrue();
        assertThat(cuerpo.get("tieneCredencialesOvz").asBoolean()).isFalse();
        assertThat(auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoriaIdA)).singleElement()
                .satisfies(f -> assertThat(f.getAccion()).isEqualTo(AccionAuditoria.CREDENCIAL_OVZ_GUARDADA));
    }

    @Test
    void borrarCredencialesDejaCredencialOvzBorrada() throws Exception {
        assertThat(put(tokenA, ganaderoA,
                "{\"usuario\":\"" + USUARIO_OVZ + "\",\"password\":\"" + PASSWORD_OVZ + "\"}")
                .getStatusCode().value()).isEqualTo(200);
        LimpiezaAuditoria.borrar(jdbcTemplate);

        ResponseEntity<String> borrado = delete(tokenA, ganaderoA);
        assertThat(borrado.getStatusCode().value()).isEqualTo(200);
        JsonNode cuerpo = objectMapper.readTree(borrado.getBody());
        assertThat(cuerpo.get("tieneCredencialesOvz").asBoolean()).isFalse();
        assertThat(cuerpo.get("usaCertificadoOvz").asBoolean()).isFalse();
        assertThat(cifradoEnBd(ganaderoA)).isNull();

        List<Auditoria> filas = auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoriaIdA);
        assertThat(filas).hasSize(1);
        assertThat(filas.get(0).getAccion()).isEqualTo(AccionAuditoria.CREDENCIAL_OVZ_BORRADA);
    }

    // --- 400: validacion antes de la BD ---

    @Test
    void sinCertificadoFaltandoUsuarioOContrasenaDa400SinCambiosNiFila() throws Exception {
        for (String cuerpo : List.of("{\"usuario\":\"soloUsuario\"}", "{\"password\":\"soloPassword\"}",
                "{\"usuario\":\"\",\"password\":\"\"}", "{}")) {
            ResponseEntity<String> respuesta = put(tokenA, ganaderoA, cuerpo);
            assertThat(respuesta.getStatusCode().value()).as(cuerpo).isEqualTo(400);
            assertThat(motivo(respuesta)).as(cuerpo)
                    .isEqualTo(GanaderoController.MOTIVO_CREDENCIALES_OVZ_FALTAN);
        }
        assertThat(cifradoEnBd(ganaderoA)).isNull();
        assertThat(auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoriaIdA)).isEmpty();
    }

    @Test
    void sinCuerpoDa400() {
        HttpHeaders headers = cabeceras(tokenA);
        ResponseEntity<String> respuesta = restTemplate.exchange(
                "/ganaderos/" + ganaderoA + "/credenciales-ovz", HttpMethod.PUT,
                new HttpEntity<>(headers), String.class);
        assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
        assertThat(auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoriaIdA)).isEmpty();
    }

    // --- 404 (ajeno e inexistente) ---

    @Test
    void ajenoDa404YDejaAccesoOtraGestoriaEnElActor() {
        ResponseEntity<String> respuesta = put(tokenB, ganaderoA,
                "{\"usuario\":\"" + USUARIO_OVZ + "\",\"password\":\"" + PASSWORD_OVZ + "\"}");
        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
        assertThat(respuesta.getBody()).isNullOrEmpty();
        // No se guardo nada en el ganadero de A.
        assertThat(cifradoEnBd(ganaderoA)).isNull();
        // Una fila ACCESO_OTRA_GESTORIA en la gestoria de B (el actor), con EDITAR_CREDENCIALES_OVZ.
        List<Auditoria> enB = auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoriaIdB);
        assertThat(enB).hasSize(1);
        assertThat(enB.get(0).getAccion()).isEqualTo(AccionAuditoria.ACCESO_OTRA_GESTORIA);
        assertThat(enB.get(0).getEntidadTipo()).isEqualTo(EntidadAuditada.GANADERO);
        assertThat(enB.get(0).getEntidadId()).isEqualTo(ganaderoA);
        assertThat(enB.get(0).getDespues()).isEqualTo("{\"operacion\":\"EDITAR_CREDENCIALES_OVZ\"}");
        assertThat(auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoriaIdA)).isEmpty();
    }

    @Test
    void borrarAjenoDa404YDejaAccesoOtraGestoria() {
        ResponseEntity<String> respuesta = delete(tokenB, ganaderoA);
        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
        assertThat(respuesta.getBody()).isNullOrEmpty();
        List<Auditoria> enB = auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoriaIdB);
        assertThat(enB).hasSize(1);
        assertThat(enB.get(0).getAccion()).isEqualTo(AccionAuditoria.ACCESO_OTRA_GESTORIA);
        assertThat(enB.get(0).getDespues()).isEqualTo("{\"operacion\":\"EDITAR_CREDENCIALES_OVZ\"}");
    }

    @Test
    void inexistenteDa404SinCuerpoNiFila() {
        ResponseEntity<String> respuesta = put(tokenA, 999_999L,
                "{\"usuario\":\"" + USUARIO_OVZ + "\",\"password\":\"" + PASSWORD_OVZ + "\"}");
        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
        assertThat(respuesta.getBody()).isNullOrEmpty();
        assertThat(auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoriaIdA)).isEmpty();
    }

    // --- la contrasena nunca sale, tampoco en el detalle ---

    @Test
    void elDetalleNuncaTraeLaContrasena() throws Exception {
        assertThat(put(tokenA, ganaderoA,
                "{\"usuario\":\"" + USUARIO_OVZ + "\",\"password\":\"" + PASSWORD_OVZ + "\"}")
                .getStatusCode().value()).isEqualTo(200);

        ResponseEntity<String> detalle = exchange("/ganaderos/" + ganaderoA, HttpMethod.GET, tokenA, null);
        assertThat(detalle.getStatusCode().value()).isEqualTo(200);
        assertThat(detalle.getBody()).doesNotContain(PASSWORD_OVZ).doesNotContain(USUARIO_OVZ)
                .doesNotContain("ovzPassword").doesNotContain("ovzUsuario").doesNotContain("password");
        JsonNode cuerpo = objectMapper.readTree(detalle.getBody());
        assertThat(cuerpo.get("tieneCredencialesOvz").asBoolean()).isTrue();
    }

    // --- 401 ---

    @Test
    void sinJwtDa401() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        ResponseEntity<String> respuesta = restTemplate.exchange(
                "/ganaderos/" + ganaderoA + "/credenciales-ovz", HttpMethod.PUT,
                new HttpEntity<>("{\"usuario\":\"x\",\"password\":\"y\"}", headers), String.class);
        assertThat(respuesta.getStatusCode().value()).isEqualTo(401);
    }

    // --- helpers ---

    private ResponseEntity<String> put(String token, Long ganaderoId, String cuerpoJson) {
        return restTemplate.exchange("/ganaderos/" + ganaderoId + "/credenciales-ovz", HttpMethod.PUT,
                new HttpEntity<>(cuerpoJson, cabeceras(token)), String.class);
    }

    private ResponseEntity<String> delete(String token, Long ganaderoId) {
        return restTemplate.exchange("/ganaderos/" + ganaderoId + "/credenciales-ovz", HttpMethod.DELETE,
                new HttpEntity<>(cabeceras(token)), String.class);
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
        ResponseEntity<String> respuesta = restTemplate.exchange("/ganaderos", HttpMethod.POST,
                new HttpEntity<>(new GanaderoCrearRequest(nombre, nif, null), cabeceras(token)), String.class);
        assertThat(respuesta.getStatusCode().value()).isEqualTo(201);
        return objectMapper.readTree(respuesta.getBody()).get("id").asLong();
    }

    private String cifradoEnBd(Long ganaderoId) {
        return jdbcTemplate.queryForObject(
                "select ovz_password_cifrada from ganadero where id = ?", String.class, ganaderoId);
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
