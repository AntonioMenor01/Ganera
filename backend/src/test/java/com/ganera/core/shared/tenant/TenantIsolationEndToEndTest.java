package com.ganera.core.shared.tenant;

import com.ganera.core.auth.LoginRequest;
import com.ganera.core.auth.LoginResponse;
import com.ganera.core.contacto.Contacto;
import com.ganera.core.contacto.ContactoRepository;
import com.ganera.core.explotacion.Explotacion;
import com.ganera.core.explotacion.ExplotacionRepository;
import com.ganera.core.facturacion.EstadoSuscripcion;
import com.ganera.core.facturacion.Suscripcion;
import com.ganera.core.facturacion.SuscripcionRepository;
import com.ganera.core.ganadero.Ganadero;
import com.ganera.core.ganadero.GanaderoRepository;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.gestoria.Usuario;
import com.ganera.core.gestoria.UsuarioRepository;
import com.ganera.core.tramite.EstadoTramite;
import com.ganera.core.tramite.TipoTramite;
import com.ganera.core.tramite.Tramite;
import com.ganera.core.tramite.TramiteCrotalRepository;
import com.ganera.core.tramite.TramiteRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Auditoria dirigida (pedida por Antonio tras el bug de WebMvcTenantConfig): TODOS los endpoints
 * que leen o escriben una entidad GestoriaScopedEntity via el filtro AMBIENTE de Hibernate (no via
 * un parametro gestoriaId explicito en la query) necesitan su PROPIA prueba end-to-end con dos
 * Gestorias reales y servidor embebido -- "usa el mismo Repository" no es suficiente, el bug de
 * hoy fue de ORDEN DE INTERCEPTOR, no de query, y eso solo se ve en un despacho HTTP real.
 *
 * Cubiertos aqui: GET /explotaciones, GET /tramites, GET /tramites/{id},
 * POST /tramites/{id}/aprobar, POST /tramites/{id}/rechazar, GET /auth/me,
 * POST /explotaciones/importar.
 *
 * NO cubiertos aqui a proposito, con la razon documentada in situ:
 * GET /facturacion/suscripcion -- no usa el filtro ambiente; resuelve la Suscripcion via
 * SuscripcionRepository.findByGestoriaId(gestoriaId), query con el gestoriaId como parametro
 * EXPLICITO de la query derivada, inmune a este tipo de bug por construccion (fallaria igual con
 * o sin el interceptor activo). El antiguo checkout de la app ya no existe (plan 2026-10-04).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TenantIsolationEndToEndTest {

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private GestoriaRepository gestoriaRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private ExplotacionRepository explotacionRepository;
    @Autowired
    private GanaderoRepository ganaderoRepository;
    @Autowired
    private TramiteRepository tramiteRepository;
    @Autowired
    private TramiteCrotalRepository tramiteCrotalRepository;
    @Autowired
    private ContactoRepository contactoRepository;
    @Autowired
    private SuscripcionRepository suscripcionRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void limpiar() {
        // tramite_crotal antes que tramite (FK): si otra clase deja crotales, no arrastra errores aqui.
        tramiteCrotalRepository.deleteAll();
        tramiteRepository.deleteAll();
        contactoRepository.deleteAll();
        suscripcionRepository.deleteAll();
        explotacionRepository.deleteAll();
        ganaderoRepository.deleteAll();
        usuarioRepository.deleteAll();
        gestoriaRepository.deleteAll();
    }

    @Test
    void explotacionesListadoNuncaDevuelveExplotacionesDeOtraGestoria() {
        Gestoria gestoriaA = gestoriaRepository.save(new Gestoria("Gestoria E2E A"));
        Gestoria gestoriaB = gestoriaRepository.save(new Gestoria("Gestoria E2E B"));
        crearUsuario(gestoriaA, "e2eA@test.com");
        crearUsuario(gestoriaB, "e2eB@test.com");

        Ganadero ganadero = nuevoGanadero(gestoriaA);
        Explotacion explotacion = new Explotacion();
        explotacion.setGestoria(gestoriaA);
        explotacion.setGanadero(ganadero);
        explotacion.setCodigoRega("ES900000000001");
        explotacion.setNombre("Finca E2E");
        explotacionRepository.save(explotacion);

        String tokenA = login("e2eA@test.com");
        String tokenB = login("e2eB@test.com");

        ResponseEntity<String> respuestaA = get("/explotaciones", tokenA);
        ResponseEntity<String> respuestaB = get("/explotaciones", tokenB);

        assertThat(respuestaA.getBody()).contains("ES900000000001");
        assertThat(respuestaB.getBody()).doesNotContain("ES900000000001");
        assertThat(respuestaB.getBody()).contains("\"totalElements\":0");
    }

    @Test
    void tramitesListadoNuncaDevuelveTramitesDeOtraGestoria() {
        Gestoria gestoriaA = gestoriaRepository.save(new Gestoria("Gestoria E2E tramites A"));
        Gestoria gestoriaB = gestoriaRepository.save(new Gestoria("Gestoria E2E tramites B"));
        crearUsuario(gestoriaA, "tramA@test.com");
        crearUsuario(gestoriaB, "tramB@test.com");

        Tramite tramiteA = nuevoTramite(gestoriaA, "+34600000101");

        String tokenA = login("tramA@test.com");
        String tokenB = login("tramB@test.com");

        ResponseEntity<String> respuestaA = get("/tramites", tokenA);
        ResponseEntity<String> respuestaB = get("/tramites", tokenB);

        assertThat(respuestaA.getBody()).contains("\"id\":" + tramiteA.getId());
        assertThat(respuestaB.getBody()).doesNotContain("\"id\":" + tramiteA.getId());
        assertThat(respuestaB.getBody()).contains("\"totalElements\":0");
    }

    @Test
    void tramiteDetallePorIdDeOtraGestoriaDevuelve404NoLosDatos() {
        Gestoria gestoriaA = gestoriaRepository.save(new Gestoria("Gestoria E2E detalle A"));
        Gestoria gestoriaB = gestoriaRepository.save(new Gestoria("Gestoria E2E detalle B"));
        crearUsuario(gestoriaA, "detA@test.com");
        crearUsuario(gestoriaB, "detB@test.com");

        Tramite tramiteA = nuevoTramite(gestoriaA, "+34600000102");
        String tokenB = login("detB@test.com");

        ResponseEntity<String> respuesta = get("/tramites/" + tramiteA.getId(), tokenB);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
        assertThat(respuesta.getBody()).isNullOrEmpty();
    }

    @Test
    void aprobarTramiteDeOtraGestoriaDevuelve404YNoCambiaSuEstado() {
        Gestoria gestoriaA = gestoriaRepository.save(new Gestoria("Gestoria E2E aprobar A"));
        Gestoria gestoriaB = gestoriaRepository.save(new Gestoria("Gestoria E2E aprobar B"));
        crearUsuario(gestoriaA, "aprA@test.com");
        crearUsuario(gestoriaB, "aprB@test.com");
        // Suscripcion ACTIVA para B: si no, el 403 de puedeAprobarTramites enmascararia el
        // chequeo de aislamiento (nunca llegaria a buscar el Tramite por id).
        suscripcionActiva(gestoriaB);

        Tramite tramiteA = nuevoTramite(gestoriaA, "+34600000103");
        // Aprobable (explotacion + tipo): si el aislamiento fallara, B lo aprobaria (200) en vez
        // de recibir un 409 por las reglas de aprobacion de Task 6 que enmascararia la fuga.
        Explotacion explotacionA = new Explotacion();
        explotacionA.setGestoria(gestoriaA);
        explotacionA.setGanadero(nuevoGanadero(gestoriaA));
        explotacionA.setCodigoRega("ES900000000103");
        explotacionA.setNombre("Finca E2E aprobar");
        tramiteA.setExplotacion(explotacionRepository.save(explotacionA));
        tramiteA.setTipoTramite(TipoTramite.ALTA);
        tramiteA = tramiteRepository.save(tramiteA);
        String tokenB = login("aprB@test.com");

        // Decision 27: B envia la version ACTUAL y correcta del Tramite de A -- si el aislamiento
        // fallara, la aprobacion pasaria (200); la version no puede abrir la puerta a un 404.
        Long versionA = tramiteA.getVersion();
        ResponseEntity<String> respuesta = postJson("/tramites/" + tramiteA.getId() + "/aprobar", tokenB,
                java.util.Map.of("version", versionA));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
        Tramite despues = tramiteRepository.findById(tramiteA.getId()).orElseThrow();
        assertThat(despues.getEstado()).isEqualTo(EstadoTramite.PENDIENTE_REVISION);
        assertThat(despues.getVersion()).isEqualTo(versionA);
    }

    @Test
    void rechazarTramiteDeOtraGestoriaDevuelve404YNoCambiaSuEstado() {
        Gestoria gestoriaA = gestoriaRepository.save(new Gestoria("Gestoria E2E rechazar A"));
        Gestoria gestoriaB = gestoriaRepository.save(new Gestoria("Gestoria E2E rechazar B"));
        crearUsuario(gestoriaA, "rechA@test.com");
        crearUsuario(gestoriaB, "rechB@test.com");

        Tramite tramiteA = nuevoTramite(gestoriaA, "+34600000104");
        String tokenB = login("rechB@test.com");

        // Mini-prompt tras A2 (punto 4): rechazar exige version. B envia la version ACTUAL y
        // correcta del Tramite de A -- si el aislamiento fallara, el rechazo pasaria (200).
        Long versionA = tramiteA.getVersion();
        ResponseEntity<String> respuesta = postJson("/tramites/" + tramiteA.getId() + "/rechazar", tokenB,
                java.util.Map.of("version", versionA));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
        Tramite despues = tramiteRepository.findById(tramiteA.getId()).orElseThrow();
        assertThat(despues.getEstado()).isEqualTo(EstadoTramite.PENDIENTE_REVISION);
        assertThat(despues.getVersion()).isEqualTo(versionA);
    }

    @Test
    void authMeDevuelveSiempreLosDatosDelPropioUsuarioAutenticado() {
        Gestoria gestoriaA = gestoriaRepository.save(new Gestoria("Gestoria E2E me A"));
        Gestoria gestoriaB = gestoriaRepository.save(new Gestoria("Gestoria E2E me B"));
        crearUsuario(gestoriaA, "meA@test.com");
        crearUsuario(gestoriaB, "meB@test.com");

        String tokenB = login("meB@test.com");
        ResponseEntity<String> respuesta = get("/auth/me", tokenB);

        assertThat(respuesta.getBody()).contains("meB@test.com");
        assertThat(respuesta.getBody()).contains("\"gestoriaId\":" + gestoriaB.getId());
        assertThat(respuesta.getBody()).doesNotContain("meA@test.com");
    }

    @Test
    void importarExcelDeUnaGestoriaNuncaCreaDatosVisiblesParaOtraGestoria() throws IOException {
        Gestoria gestoriaA = gestoriaRepository.save(new Gestoria("Gestoria E2E import A"));
        Gestoria gestoriaB = gestoriaRepository.save(new Gestoria("Gestoria E2E import B"));
        crearUsuario(gestoriaA, "impA@test.com");
        crearUsuario(gestoriaB, "impB@test.com");

        String tokenA = login("impA@test.com");
        String tokenB = login("impB@test.com");

        ResponseEntity<String> respuestaImport = importarExcel(tokenA, construirExcelMinimo());
        assertThat(respuestaImport.getStatusCode().value()).isEqualTo(200);
        assertThat(respuestaImport.getBody()).contains("\"creadas\":1");

        ResponseEntity<String> respuestaA = get("/explotaciones", tokenA);
        ResponseEntity<String> respuestaB = get("/explotaciones", tokenB);

        assertThat(respuestaA.getBody()).contains("ES910000000001");
        assertThat(respuestaB.getBody()).doesNotContain("ES910000000001");
        assertThat(respuestaB.getBody()).contains("\"totalElements\":0");
    }

    private void suscripcionActiva(Gestoria gestoria) {
        Suscripcion suscripcion = new Suscripcion();
        suscripcion.setGestoria(gestoria);
        suscripcion.setEstado(EstadoSuscripcion.ACTIVA);
        suscripcionRepository.save(suscripcion);
    }

    private Ganadero nuevoGanadero(Gestoria gestoria) {
        Ganadero ganadero = new Ganadero();
        ganadero.setGestoria(gestoria);
        ganadero.setNombre("Ganadero E2E");
        return ganaderoRepository.save(ganadero);
    }

    private Tramite nuevoTramite(Gestoria gestoria, String telefono) {
        Contacto contacto = new Contacto();
        contacto.setTelefono(telefono);
        contacto.setNombre("Contacto E2E");
        contacto.setGestoria(gestoria);
        contactoRepository.save(contacto);

        Tramite tramite = new Tramite();
        tramite.setGestoria(gestoria);
        tramite.setContacto(contacto);
        tramite.setEstado(EstadoTramite.PENDIENTE_REVISION);
        return tramiteRepository.save(tramite);
    }

    private void crearUsuario(Gestoria gestoria, String email) {
        Usuario usuario = new Usuario();
        usuario.setGestoria(gestoria);
        usuario.setEmail(email);
        usuario.setPasswordHash(passwordEncoder.encode("password123"));
        usuario.setNombre("Usuario E2E");
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
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> postJson(String path, String token, Object cuerpo) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(cuerpo, headers), String.class);
    }

    private ResponseEntity<String> importarExcel(String token, byte[] contenido) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("archivo", new ByteArrayResource(contenido) {
            @Override
            public String getFilename() {
                return "prueba-e2e.xlsx";
            }
        });

        return restTemplate.postForEntity(
                "/explotaciones/importar", new HttpEntity<>(body, headers), String.class);
    }

    private static byte[] construirExcelMinimo() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Row cabeceraExplotaciones = workbook.createSheet("Explotaciones").createRow(0);
            String[] columnasExplotaciones = {"codigo_rega", "nombre", "nif_ganadero", "nombre_ganadero"};
            for (int c = 0; c < columnasExplotaciones.length; c++) {
                cabeceraExplotaciones.createCell(c).setCellValue(columnasExplotaciones[c]);
            }
            Row filaExplotacion = workbook.getSheet("Explotaciones").createRow(1);
            String[] valoresExplotacion = {"ES910000000001", "Finca Import E2E", "99999999R", "Ganadero Import E2E"};
            for (int c = 0; c < valoresExplotacion.length; c++) {
                filaExplotacion.createCell(c).setCellValue(valoresExplotacion[c]);
            }

            Row cabeceraAnimales = workbook.createSheet("Animales").createRow(0);
            String[] columnasAnimales = {"crotal", "especie", "codigo_rega_explotacion"};
            for (int c = 0; c < columnasAnimales.length; c++) {
                cabeceraAnimales.createCell(c).setCellValue(columnasAnimales[c]);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
