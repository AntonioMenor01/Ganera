package com.ganera.core.explotacion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganera.core.auth.LoginRequest;
import com.ganera.core.auth.LoginResponse;
import com.ganera.core.contacto.ContactoExplotacionRepository;
import com.ganera.core.contacto.ContactoRepository;
import com.ganera.core.facturacion.SuscripcionRepository;
import com.ganera.core.ganadero.GanaderoRepository;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.gestoria.UsuarioRepository;
import com.ganera.core.onboarding.OnboardingRequest;
import com.ganera.core.onboarding.OnboardingResponse;
import jakarta.persistence.EntityManagerFactory;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E real (servidor embebido + TestRestTemplate) de GET /explotaciones/{id} y de
 * GET /explotaciones?q= con DOS Gestorias, cada una con su JWT y sus datos importados por Excel.
 * Las dos Gestorias tienen a proposito explotaciones cuyo nombre contiene "Robledo": un q que
 * coincide en ambas solo devuelve (y solo cuenta en totalElements) las de quien pregunta.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class ExplotacionEndToEndTest {

    private static final String SECRETO_ONBOARDING = "test-onboarding-secret";

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Autowired
    private ContactoExplotacionRepository contactoExplotacionRepository;
    @Autowired
    private ContactoRepository contactoRepository;
    @Autowired
    private AnimalRepository animalRepository;
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

    private String tokenA;
    private String tokenB;
    private Long explotacionA1;
    private Long explotacionB1;

    @BeforeEach
    void preparar() throws IOException {
        onboarding("Gestoria Explotaciones A", "explotacionesA@test.com");
        onboarding("Gestoria Explotaciones B", "explotacionesB@test.com");
        tokenA = login("explotacionesA@test.com");
        tokenB = login("explotacionesB@test.com");

        importar(tokenA, List.<String[]>of(
                new String[]{"ES950000000001", "Finca Robledo Alto", "71000001A", "Ganadero Comun A"},
                new String[]{"ES950000000002", "Finca Robledo Bajo", "71000001A", "Ganadero Comun A"},
                new String[]{"ES950000000003", "Cortijo Llano", "71000002B", "Ganadero Comun A Dos"}));
        importar(tokenB, List.<String[]>of(
                new String[]{"ES950000000101", "Finca Robledo de B", "71000003C", "Pastor Exclusivo B"},
                new String[]{"ES950000000102", "Dehesa Sur", "71000003C", "Pastor Exclusivo B"}));

        explotacionA1 = idsExplotaciones(tokenA).get("ES950000000001");
        explotacionB1 = idsExplotaciones(tokenB).get("ES950000000101");
        assertThat(explotacionA1).isNotNull();
        assertThat(explotacionB1).isNotNull();
    }

    @AfterEach
    void limpiar() {
        contactoExplotacionRepository.deleteAll();
        contactoRepository.deleteAll();
        animalRepository.deleteAll();
        explotacionRepository.deleteAll();
        ganaderoRepository.deleteAll();
        suscripcionRepository.deleteAll();
        usuarioRepository.deleteAll();
        gestoriaRepository.deleteAll();
    }

    // --- GET /explotaciones/{id} ---

    @Test
    void detalleDeExplotacionPropiaDevuelve200ConSusDatos() throws IOException {
        ResponseEntity<String> respuesta = get("/explotaciones/" + explotacionA1, tokenA);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        JsonNode json = objectMapper.readTree(respuesta.getBody());
        assertThat(json.get("id").asLong()).isEqualTo(explotacionA1);
        assertThat(json.get("codigoRega").asText()).isEqualTo("ES950000000001");
        assertThat(json.get("nombre").asText()).isEqualTo("Finca Robledo Alto");
        assertThat(json.get("ganaderoId").asLong()).isPositive();
        assertThat(json.get("nombreGanadero").asText()).isEqualTo("Ganadero Comun A");
    }

    @Test
    void detalleDeExplotacionDeOtraGestoriaDevuelve404SinCuerpo() {
        ResponseEntity<String> respuesta = get("/explotaciones/" + explotacionA1, tokenB);
        ResponseEntity<String> alReves = get("/explotaciones/" + explotacionB1, tokenA);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
        assertThat(respuesta.getBody()).isNullOrEmpty();
        assertThat(alReves.getStatusCode().value()).isEqualTo(404);
        assertThat(alReves.getBody()).isNullOrEmpty();
    }

    @Test
    void detalleDeExplotacionInexistenteDevuelve404SinCuerpo() {
        ResponseEntity<String> respuesta = get("/explotaciones/999999", tokenA);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
        assertThat(respuesta.getBody()).isNullOrEmpty();
    }

    @Test
    void detalleConIdNoNumericoDevuelve400() {
        assertThat(get("/explotaciones/abc", tokenA).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void sinJwtDetalleYBusquedaDevuelven401() {
        for (String ruta : List.of("/explotaciones/" + explotacionA1, "/explotaciones?q=robledo")) {
            ResponseEntity<String> respuesta = restTemplate.getForEntity(ruta, String.class);
            assertThat(respuesta.getStatusCode().value()).as(ruta).isEqualTo(401);
        }
    }

    // --- GET /explotaciones?q= ---

    @Test
    void qQueCoincideEnAmbasGestoriasSoloDevuelveYCuentaLasPropias() throws IOException {
        JsonNode paginaA = objectMapper.readTree(get("/explotaciones?q=ROBLEDO", tokenA).getBody());
        JsonNode paginaB = objectMapper.readTree(get("/explotaciones?q=ROBLEDO", tokenB).getBody());

        assertThat(codigos(paginaA)).containsExactly("ES950000000001", "ES950000000002");
        assertThat(paginaA.get("totalElements").asLong()).isEqualTo(2);
        assertThat(codigos(paginaB)).containsExactly("ES950000000101");
        assertThat(paginaB.get("totalElements").asLong()).isEqualTo(1);
    }

    @Test
    void qPorRegaComunAAmbasGestoriasSoloCuentaLasPropiasAlPaginar() throws IOException {
        JsonNode pagina = objectMapper.readTree(get("/explotaciones?q=es9500&size=2&page=1", tokenA).getBody());

        assertThat(pagina.get("totalElements").asLong()).isEqualTo(3);
        assertThat(pagina.get("totalPages").asInt()).isEqualTo(2);
        assertThat(codigos(pagina)).containsExactly("ES950000000003");
    }

    @Test
    void qQueSoloCoincideConElGanaderoDeOtraGestoriaDevuelveVacio() throws IOException {
        JsonNode pagina = objectMapper.readTree(get("/explotaciones?q=pastor exclusivo", tokenA).getBody());
        JsonNode propia = objectMapper.readTree(get("/explotaciones?q=pastor exclusivo", tokenB).getBody());

        assertThat(pagina.get("content")).isEmpty();
        assertThat(pagina.get("totalElements").asLong()).isZero();
        assertThat(codigos(propia)).containsExactly("ES950000000101", "ES950000000102");
    }

    @Test
    void qPorNombreDeGanaderoPropio() throws IOException {
        JsonNode pagina = objectMapper.readTree(get("/explotaciones?q=comun a dos", tokenA).getBody());

        assertThat(codigos(pagina)).containsExactly("ES950000000003");
        assertThat(pagina.get("content").get(0).get("nombreGanadero").asText()).isEqualTo("Ganadero Comun A Dos");
    }

    @Test
    void qDemasiadoLargaDevuelve400ConMotivo() throws IOException {
        ResponseEntity<String> respuesta = get("/explotaciones?q=" + "a".repeat(101), tokenA);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
        assertThat(objectMapper.readTree(respuesta.getBody()).get("motivo").asText())
                .isEqualTo(ExplotacionController.MOTIVO_BUSQUEDA_DEMASIADO_LARGA);
    }

    @Test
    void qConSortSigueValidandoLaListaBlancaYOrdenando() throws IOException {
        ResponseEntity<String> noPermitido = get("/explotaciones?q=robledo&sort=ganadero.nombre", tokenA);
        assertThat(noPermitido.getStatusCode().value()).isEqualTo(400);
        assertThat(objectMapper.readTree(noPermitido.getBody()).get("motivo").asText())
                .isEqualTo("Campo de ordenación no permitido.");

        JsonNode porCodigoDesc = objectMapper.readTree(
                get("/explotaciones?q=robledo&sort=codigoRega,desc", tokenA).getBody());
        assertThat(codigos(porCodigoDesc)).containsExactly("ES950000000002", "ES950000000001");
    }

    @Test
    void qEnBlancoDevuelveElListadoCompleto() throws IOException {
        JsonNode pagina = objectMapper.readTree(get("/explotaciones?q=   ", tokenA).getBody());

        assertThat(pagina.get("totalElements").asLong()).isEqualTo(3);
    }

    /** La busqueda trae el Ganadero en la misma consulta (join fetch): pagina + count como mucho,
     * sin una consulta extra por Ganadero distinto de la pagina. */
    @Test
    void qNoHaceNMasUnoSobreElGanadero() {
        Statistics estadisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        estadisticas.clear();
        assertThat(get("/explotaciones?q=es9500", tokenA).getStatusCode().value()).isEqualTo(200);
        long consultas = estadisticas.getPrepareStatementCount();

        assertThat(consultas).as("consultas de la busqueda").isLessThanOrEqualTo(2);
    }

    /** El listado sin q tambien trae el Ganadero en la consulta de la pagina (@EntityGraph): la
     * pagina de A tiene dos Ganaderos distintos y no debe cargar ninguno por separado. Con
     * size=2 la pagina esta llena, asi que tambien corre la consulta de count. */
    @Test
    void listadoSinQNoHaceNMasUnoSobreElGanadero() throws IOException {
        Statistics estadisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        estadisticas.clear();
        ResponseEntity<String> respuesta = get("/explotaciones?size=2&sort=codigoRega,desc", tokenA);
        long consultas = estadisticas.getPrepareStatementCount();
        long cargasSueltas = estadisticas.getEntityFetchCount();

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        JsonNode pagina = objectMapper.readTree(respuesta.getBody());
        assertThat(pagina.get("totalElements").asLong()).isEqualTo(3);
        List<String> ganaderos = new ArrayList<>();
        pagina.get("content").forEach(e -> ganaderos.add(e.get("nombreGanadero").asText()));
        assertThat(ganaderos).containsExactly("Ganadero Comun A Dos", "Ganadero Comun A");
        assertThat(cargasSueltas).as("Ganaderos cargados uno a uno").isZero();
        assertThat(consultas).as("pagina + count").isEqualTo(2);
    }

    // --- utilidades ---

    private static List<String> codigos(JsonNode pagina) {
        List<String> codigos = new ArrayList<>();
        pagina.get("content").forEach(e -> codigos.add(e.get("codigoRega").asText()));
        return codigos;
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

    private Map<String, Long> idsExplotaciones(String token) throws IOException {
        Map<String, Long> ids = new HashMap<>();
        objectMapper.readTree(get("/explotaciones", token).getBody()).get("content")
                .forEach(e -> ids.put(e.get("codigoRega").asText(), e.get("id").asLong()));
        return ids;
    }

    private ResponseEntity<String> get(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private void importar(String token, List<String[]> filasExplotaciones) throws IOException {
        byte[] contenido;
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            escribirHoja(workbook.createSheet("Explotaciones"),
                    new String[]{"codigo_rega", "nombre", "nif_ganadero", "nombre_ganadero"}, filasExplotaciones);
            escribirHoja(workbook.createSheet("Animales"),
                    new String[]{"crotal", "especie", "codigo_rega_explotacion"}, List.of());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            contenido = out.toByteArray();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("archivo", new ByteArrayResource(contenido) {
            @Override
            public String getFilename() {
                return "explotaciones-e2e.xlsx";
            }
        });
        ResponseEntity<String> respuesta = restTemplate.postForEntity(
                "/explotaciones/importar", new HttpEntity<>(body, headers), String.class);
        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(objectMapper.readTree(respuesta.getBody()).get("errores")).isEmpty();
    }

    private static void escribirHoja(Sheet hoja, String[] cabecera, List<String[]> filas) {
        List<String[]> todas = new ArrayList<>();
        todas.add(cabecera);
        todas.addAll(filas);
        for (int i = 0; i < todas.size(); i++) {
            Row fila = hoja.createRow(i);
            String[] valores = todas.get(i);
            for (int c = 0; c < valores.length; c++) {
                fila.createCell(c).setCellValue(valores[c]);
            }
        }
    }
}
