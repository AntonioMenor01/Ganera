package com.ganera.core.tramite;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganera.core.auth.LoginRequest;
import com.ganera.core.auth.LoginResponse;
import com.ganera.core.explotacion.Explotacion;
import com.ganera.core.explotacion.ExplotacionRepository;
import com.ganera.core.ganadero.Ganadero;
import com.ganera.core.ganadero.GanaderoRepository;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.gestoria.Usuario;
import com.ganera.core.gestoria.UsuarioRepository;
import com.ganera.core.shared.auditoria.LimpiezaAuditoria;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Aislamiento multi-tenant de GET /resumen sobre servidor real y DOS gestorías con sus JWT. El resumen
 * agrega por gestoriaId (parámetro real del JWT, nunca findById desnudo ni el filtro ambiente de Hibernate):
 * nº de trámites PENDIENTE_REVISION, nº de ganaderos, nº de alertas activas y la previa (primeras 5) de la
 * cola con el nombre del ganadero. Este guard afirma que los contadores de A no incluyen datos de B y que la
 * previa de la cola de una gestoría NUNCA trae un ganadero/explotación de la otra. Si algún finder dejara de
 * estar scoped por gestoría, los contadores se mezclarían o la previa filtraría un nombre ajeno y el test iría
 * en rojo. Mismo patrón que AlertaEndToEndTest / TenantIsolationEndToEndTest.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ResumenEndToEndTest {

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private GestoriaRepository gestoriaRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private GanaderoRepository ganaderoRepository;
    @Autowired
    private ExplotacionRepository explotacionRepository;
    @Autowired
    private TramiteRepository tramiteRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void limpiar() {
        LimpiezaAuditoria.borrar(jdbcTemplate);
        tramiteRepository.deleteAll();
        explotacionRepository.deleteAll();
        ganaderoRepository.deleteAll();
        usuarioRepository.deleteAll();
        gestoriaRepository.deleteAll();
    }

    @Test
    void elResumenDeAnoMezclaContadoresNiLaPreviaDeColaConLosDeB() throws Exception {
        // --- Gestoría A: 2 ganaderos, 3 trámites pendientes (uno dispara una alerta de plazo), 1 aprobado ---
        Gestoria gestoriaA = gestoriaRepository.save(new Gestoria("Gestoria Resumen A"));
        crearUsuario(gestoriaA, "resumenA@test.com");
        Ganadero ganaderoA1 = nuevoGanadero(gestoriaA, "Ganadero A1");
        Ganadero ganaderoA2 = nuevoGanadero(gestoriaA, "Ganadero A2");
        Explotacion explotacionA1 = nuevaExplotacion(gestoriaA, ganaderoA1, "ESA00000000001", "Finca A1");
        Explotacion explotacionA2 = nuevaExplotacion(gestoriaA, ganaderoA2, "ESA00000000002", "Finca A2");

        // Pendiente que además dispara UNA alerta determinista: BAJA_MUERTE con la muerte hace 7 días -> el
        // plazo de 7 días vence hoy, dentro de la ventana [hoy-1, hoy+3] de AlertaService. Creado hoy -> no
        // cuenta como "revisión atascada" (eso serían 2 días), así que A tiene exactamente 1 alerta.
        Tramite pendienteA1 = nuevoTramite(gestoriaA, EstadoTramite.PENDIENTE_REVISION, explotacionA1);
        pendienteA1.setTipoTramite(TipoTramite.BAJA_MUERTE);
        pendienteA1.setFechaHecho(LocalDate.now().minusDays(7));
        tramiteRepository.save(pendienteA1);
        nuevoTramite(gestoriaA, EstadoTramite.PENDIENTE_REVISION, explotacionA2);
        // Pendiente sin explotación (cola ambigua): la previa debe traerlo con ganaderoNombre null (left join).
        nuevoTramite(gestoriaA, EstadoTramite.PENDIENTE_REVISION, null);
        // Aprobado: NO cuenta como pendiente de revisión.
        nuevoTramite(gestoriaA, EstadoTramite.APROBADO, explotacionA1);

        // --- Gestoría B: 1 ganadero, 1 pendiente, sin alertas ---
        Gestoria gestoriaB = gestoriaRepository.save(new Gestoria("Gestoria Resumen B"));
        crearUsuario(gestoriaB, "resumenB@test.com");
        Ganadero ganaderoB1 = nuevoGanadero(gestoriaB, "Ganadero B1");
        Explotacion explotacionB1 = nuevaExplotacion(gestoriaB, ganaderoB1, "ESB00000000001", "Finca B1");
        nuevoTramite(gestoriaB, EstadoTramite.PENDIENTE_REVISION, explotacionB1);

        String tokenA = login("resumenA@test.com");
        String tokenB = login("resumenB@test.com");

        ResponseEntity<String> resumenA = get("/resumen", tokenA);
        ResponseEntity<String> resumenB = get("/resumen", tokenB);

        assertThat(resumenA.getStatusCode().value()).isEqualTo(200);
        JsonNode a = objectMapper.readTree(resumenA.getBody());
        assertThat(a.get("tramitesPendientes").asLong()).isEqualTo(3);
        assertThat(a.get("ganaderos").asLong()).isEqualTo(2);
        assertThat(a.get("alertasActivas").asInt()).isEqualTo(1);
        assertThat(a.get("cola")).hasSize(3);
        List<String> nombresColaA = a.get("cola").findValuesAsText("ganaderoNombre");
        assertThat(nombresColaA).contains("Ganadero A1", "Ganadero A2");
        // La fila sin explotación trae ganaderoNombre null, no revienta.
        assertThat(a.get("cola").toString()).contains("\"ganaderoNombre\":null");
        // A NUNCA ve nada de B.
        assertThat(resumenA.getBody()).doesNotContain("Ganadero B1").doesNotContain("ESB00000000001");

        assertThat(resumenB.getStatusCode().value()).isEqualTo(200);
        JsonNode b = objectMapper.readTree(resumenB.getBody());
        assertThat(b.get("tramitesPendientes").asLong()).isEqualTo(1);
        assertThat(b.get("ganaderos").asLong()).isEqualTo(1);
        assertThat(b.get("alertasActivas").asInt()).isEqualTo(0);
        assertThat(b.get("cola")).hasSize(1);
        assertThat(b.get("cola").findValuesAsText("ganaderoNombre")).containsExactly("Ganadero B1");
        // B NUNCA ve nada de A: ni en los contadores ni en la previa de la cola.
        assertThat(resumenB.getBody())
                .doesNotContain("Ganadero A1")
                .doesNotContain("Ganadero A2")
                .doesNotContain("ESA00000000001")
                .doesNotContain("ESA00000000002");
    }

    @Test
    void laPreviaDeLaColaSeLimitaA5AunqueHayaMasPendientes() throws Exception {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria Resumen Limite"));
        crearUsuario(gestoria, "resumenLimite@test.com");
        Ganadero ganadero = nuevoGanadero(gestoria, "Ganadero Limite");
        Explotacion explotacion = nuevaExplotacion(gestoria, ganadero, "ESL00000000001", "Finca Limite");
        for (int i = 0; i < 7; i++) {
            nuevoTramite(gestoria, EstadoTramite.PENDIENTE_REVISION, explotacion);
        }

        ResponseEntity<String> resumen = get("/resumen", login("resumenLimite@test.com"));

        assertThat(resumen.getStatusCode().value()).isEqualTo(200);
        JsonNode cuerpo = objectMapper.readTree(resumen.getBody());
        assertThat(cuerpo.get("tramitesPendientes").asLong()).isEqualTo(7);
        assertThat(cuerpo.get("cola")).hasSize(5);
    }

    private Tramite nuevoTramite(Gestoria gestoria, EstadoTramite estado, Explotacion explotacion) {
        Tramite tramite = new Tramite();
        tramite.setGestoria(gestoria);
        tramite.setEstado(estado);
        tramite.setExplotacion(explotacion);
        return tramiteRepository.save(tramite);
    }

    private Ganadero nuevoGanadero(Gestoria gestoria, String nombre) {
        Ganadero ganadero = new Ganadero();
        ganadero.setGestoria(gestoria);
        ganadero.setNombre(nombre);
        return ganaderoRepository.save(ganadero);
    }

    private Explotacion nuevaExplotacion(Gestoria gestoria, Ganadero ganadero, String codigoRega, String nombre) {
        Explotacion explotacion = new Explotacion();
        explotacion.setGestoria(gestoria);
        explotacion.setGanadero(ganadero);
        explotacion.setCodigoRega(codigoRega);
        explotacion.setNombre(nombre);
        return explotacionRepository.save(explotacion);
    }

    private void crearUsuario(Gestoria gestoria, String email) {
        Usuario usuario = new Usuario();
        usuario.setGestoria(gestoria);
        usuario.setEmail(email);
        usuario.setPasswordHash(passwordEncoder.encode("password123"));
        usuario.setNombre("Usuario Resumen");
        usuario.setActivo(true);
        usuarioRepository.save(usuario);
    }

    private String login(String email) {
        ResponseEntity<LoginResponse> respuesta = restTemplate.postForEntity(
                "/auth/login", new LoginRequest(email, "password123"), LoginResponse.class);
        return respuesta.getBody().token();
    }

    private ResponseEntity<String> get(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }
}
