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
import org.springframework.web.util.UriComponentsBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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

    /** La busqueda trae el Ganadero en la consulta de la pagina (@EntityGraph sobre
     * findAll(Specification, Pageable)): con la pagina llena (size=2 de 3, asi corre tambien el
     * count) son exactamente pagina + count y ningun Ganadero cargado por separado. */
    @Test
    void qNoHaceNMasUnoSobreElGanadero() throws IOException {
        Statistics estadisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        estadisticas.clear();
        ResponseEntity<String> respuesta = get("/explotaciones?q=es9500&size=2&sort=codigoRega,desc", tokenA);
        long consultas = estadisticas.getPrepareStatementCount();
        long cargasSueltas = estadisticas.getEntityFetchCount();

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        JsonNode pagina = objectMapper.readTree(respuesta.getBody());
        assertThat(pagina.get("totalElements").asLong()).isEqualTo(3);
        assertThat(nombresGanaderos(pagina)).containsExactly("Ganadero Comun A Dos", "Ganadero Comun A");
        assertThat(cargasSueltas).as("Ganaderos cargados uno a uno").isZero();
        assertThat(consultas).as("pagina + count").isEqualTo(2);
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

    // --- GET /explotaciones?q= por palabras y sin tildes (plan 2026-10-04, T3) ---

    @Test
    void sinTildesMartinezEncuentraMartinezConCualquierGrafia() throws IOException {
        importarDatosDeBusqueda();

        for (String q : List.of("martinez", "MARTÍNEZ", "martínez", "Martínez")) {
            JsonNode pagina = buscar(tokenA, q);
            assertThat(codigos(pagina)).as(q).containsExactly("ES130000000003");
            assertThat(pagina.get("content").get(0).get("nombreGanadero").asText()).isEqualTo("José Martínez");
        }
    }

    @Test
    void regaYGanaderoJuntosSoloEncuentranLaQueCumpleAmbas() throws IOException {
        importarDatosDeBusqueda();

        JsonNode pagina = buscar(tokenA, "ES12 perez");

        // No sale ES120000000002 (ES12 de Ana García) ni ES130000000001 (de Pérez con otro REGA).
        assertThat(codigos(pagina)).containsExactly("ES120000000001");
        assertThat(pagina.get("totalElements").asLong()).isEqualTo(1);
    }

    @Test
    void palabraDelGanaderoMasPalabraDelRegaEnLaPropiaGestoria() throws IOException {
        importarDatosDeBusqueda();

        assertThat(codigos(buscar(tokenA, "pedro es1300"))).containsExactly("ES130000000001");
        assertThat(codigos(buscar(tokenA, "es1300 PEDRO"))).containsExactly("ES130000000001");
    }

    @Test
    void palabraQueSoloExisteEnLaOtraGestoriaNoEncuentraNiCuenta() throws IOException {
        importarDatosDeBusqueda();

        JsonNode deA = buscar(tokenA, "zarzalejo");
        JsonNode deB = buscar(tokenB, "zarzalejo");

        assertThat(deA.get("content")).isEmpty();
        assertThat(deA.get("totalElements").asLong()).isZero();
        assertThat(codigos(deB)).containsExactly("ES140000000101");
    }

    /** "olivos" (ES120000000001) y "garcia" (Ana García, ES120000000002) existen en A, pero en
     * explotaciones distintas; las dos juntas solo se cumplen en ES140000000102, de B. */
    @Test
    void dosPalabrasQueJuntasSoloSeCumplenEnLaOtraGestoriaNoEncuentranNada() throws IOException {
        importarDatosDeBusqueda();

        assertThat(codigos(buscar(tokenA, "olivos"))).containsExactly("ES120000000001");
        assertThat(codigos(buscar(tokenA, "garcia"))).containsExactly("ES120000000002", "ES130000000004");

        JsonNode deA = buscar(tokenA, "olivos garcia");
        assertThat(deA.get("content")).isEmpty();
        assertThat(deA.get("totalElements").asLong()).isZero();
        assertThat(codigos(buscar(tokenB, "olivos garcia"))).containsExactly("ES140000000102");
    }

    @Test
    void enieYNSeEncuentranMutuamente() throws IOException {
        importarDatosDeBusqueda();

        // ES130000000003 "Finca La Peña" y ES130000000004 "Corral Pena".
        assertThat(codigos(buscar(tokenA, "peña"))).containsExactly("ES130000000003", "ES130000000004");
        assertThat(codigos(buscar(tokenA, "pena"))).containsExactly("ES130000000003", "ES130000000004");
        assertThat(codigos(buscar(tokenA, "PEÑA"))).containsExactly("ES130000000003", "ES130000000004");
    }

    @Test
    void guionesSeEliminanYLaPalabraVaciaSeDescarta() throws IOException {
        importarDatosDeBusqueda();

        for (String q : List.of("martin-perez", "martinperez", "martin - perez", "Martín-Pérez")) {
            assertThat(codigos(buscar(tokenA, q))).as(q).containsExactly("ES130000000005");
        }
    }

    @Test
    void qSoloConComodinesDevuelvePaginaVaciaConLaFormaDeSiempre() throws IOException {
        importarDatosDeBusqueda();

        URI uri = uriBusqueda("%%", Map.of("size", "5", "page", "1"));
        assertThat(uri.getRawQuery()).contains("q=%25%25");
        ResponseEntity<String> respuesta = get(uri, tokenA);
        JsonNode normal = objectMapper.readTree(get("/explotaciones?q=finca&size=5&page=1", tokenA).getBody());

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        JsonNode vacia = objectMapper.readTree(respuesta.getBody());
        assertThat(vacia.get("content")).isEmpty();
        assertThat(vacia.get("totalElements").asLong()).isZero();
        assertThat(vacia.get("size").asInt()).isEqualTo(5);
        assertThat(vacia.get("number").asInt()).isEqualTo(1);
        assertThat(campos(vacia)).containsExactlyInAnyOrderElementsOf(campos(normal));
        assertThat(campos(vacia.get("pageable"))).containsExactlyInAnyOrderElementsOf(campos(normal.get("pageable")));
    }

    @Test
    void nuevePalabrasDevuelven400ConMotivo() throws IOException {
        ResponseEntity<String> respuesta = get(
                uriBusqueda("uno dos tres cuatro cinco seis siete ocho nueve", Map.of()), tokenA);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
        assertThat(objectMapper.readTree(respuesta.getBody()).get("motivo").asText())
                .isEqualTo("La búsqueda admite como máximo 8 palabras.");
    }

    /** q de varias palabras con la pagina llena: exactamente pagina + count y ningun Ganadero
     * cargado por separado (ajuste A3: sin fetch en la Specification, el EntityGraph solo en la
     * consulta de datos). */
    @Test
    void qDeVariasPalabrasHacePaginaMasCountSinCargasSueltas() throws IOException {
        importarDatosDeBusqueda();
        Statistics estadisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        estadisticas.clear();
        ResponseEntity<String> respuesta = get(uriBusqueda("finca es1", Map.of("size", "2")), tokenA);
        long consultas = estadisticas.getPrepareStatementCount();
        long cargasSueltas = estadisticas.getEntityFetchCount();

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        JsonNode pagina = objectMapper.readTree(respuesta.getBody());
        assertThat(pagina.get("totalElements").asLong()).isEqualTo(5);
        assertThat(nombresGanaderos(pagina)).containsExactly("Pedro Pérez", "Ana García");
        assertThat(cargasSueltas).as("Ganaderos cargados uno a uno").isZero();
        assertThat(consultas).as("pagina + count").isEqualTo(2);
    }

    @Test
    void qPaginaRespetaSizeYPageYCuentaBien() throws IOException {
        importarDatosDeBusqueda();

        JsonNode segunda = buscar(tokenA, "finca es1", Map.of("size", "2", "page", "1"));
        JsonNode tercera = buscar(tokenA, "finca es1", Map.of("size", "2", "page", "2"));

        assertThat(segunda.get("totalElements").asLong()).isEqualTo(5);
        assertThat(segunda.get("totalPages").asInt()).isEqualTo(3);
        assertThat(segunda.get("size").asInt()).isEqualTo(2);
        assertThat(segunda.get("number").asInt()).isEqualTo(1);
        assertThat(codigos(segunda)).containsExactly("ES130000000001", "ES130000000003");
        assertThat(tercera.get("totalElements").asLong()).isEqualTo(5);
        assertThat(codigos(tercera)).containsExactly("ES130000000005");
    }

    @Test
    void laUriDeLaBusquedaCodificaLosNoAsciiUnaSolaVez() {
        URI uri = uriBusqueda("MARTÍNEZ peña", Map.of());

        assertThat(uri.getRawQuery()).isEqualTo("q=MART%C3%8DNEZ%20pe%C3%B1a");
    }

    // --- utilidades ---

    /** Datos extra de la busqueda por palabras (REGAs ES12/ES13/ES14, que no chocan con los ES95
     * de preparar(), asi los tests de arriba no cambian). A: Pedro Pérez (ES120000000001 Los Olivos,
     * ES130000000001 La Vega), Ana García (ES120000000002 El Cerro, ES130000000004 Corral Pena),
     * José Martínez (ES130000000003 La Peña), Luis Ruiz (ES130000000005 Martín-Pérez).
     * B: Rosa Gil (ES140000000101 Finca Zarzalejo) y Marta García (ES140000000102 Finca Olivos). */
    private void importarDatosDeBusqueda() throws IOException {
        importar(tokenA, List.<String[]>of(
                new String[]{"ES120000000001", "Finca Los Olivos", "72000001A", "Pedro Pérez"},
                new String[]{"ES120000000002", "Finca El Cerro", "72000002B", "Ana García"},
                new String[]{"ES130000000001", "Finca La Vega", "72000001A", "Pedro Pérez"},
                new String[]{"ES130000000003", "Finca La Peña", "72000003C", "José Martínez"},
                new String[]{"ES130000000004", "Corral Pena", "72000002B", "Ana García"},
                new String[]{"ES130000000005", "Finca Martín-Pérez", "72000004D", "Luis Ruiz"}));
        importar(tokenB, List.<String[]>of(
                new String[]{"ES140000000101", "Finca Zarzalejo", "72000101E", "Rosa Gil"},
                new String[]{"ES140000000102", "Finca Olivos", "72000102F", "Marta García"}));
    }

    /** URI de GET /explotaciones con q y otros parametros, codificada UNA vez (UTF-8, % -> %25).
     * Se pasa como URI a TestRestTemplate para que no la vuelva a codificar. */
    private static URI uriBusqueda(String q, Map<String, String> otros) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/explotaciones").queryParam("q", q);
        new TreeMap<>(otros).forEach(builder::queryParam);
        return builder.build().encode().toUri();
    }

    private JsonNode buscar(String token, String q) throws IOException {
        return buscar(token, q, Map.of());
    }

    private JsonNode buscar(String token, String q, Map<String, String> otros) throws IOException {
        ResponseEntity<String> respuesta = get(uriBusqueda(q, otros), token);
        assertThat(respuesta.getStatusCode().value()).as(q).isEqualTo(200);
        return objectMapper.readTree(respuesta.getBody());
    }

    private static List<String> campos(JsonNode nodo) {
        List<String> campos = new ArrayList<>();
        nodo.fieldNames().forEachRemaining(campos::add);
        return campos;
    }

    private static List<String> nombresGanaderos(JsonNode pagina) {
        List<String> nombres = new ArrayList<>();
        pagina.get("content").forEach(e -> nombres.add(e.get("nombreGanadero").asText()));
        return nombres;
    }

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

    private ResponseEntity<String> get(URI uri, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), String.class);
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
