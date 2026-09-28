package com.ganera.core.ganadero;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganera.core.auth.LoginRequest;
import com.ganera.core.auth.LoginResponse;
import com.ganera.core.contacto.ContactoExplotacionRepository;
import com.ganera.core.contacto.ContactoRepository;
import com.ganera.core.explotacion.AnimalRepository;
import com.ganera.core.explotacion.ExplotacionRepository;
import com.ganera.core.facturacion.SuscripcionRepository;
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
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E real (servidor embebido + TestRestTemplate) de GET /ganaderos, GET /ganaderos/{id} y
 * GET /explotaciones/{id}/animales con DOS Gestorias, cada una con su JWT y sus propios datos
 * importados por Excel (Explotaciones, Animales y Contactos). Cada endpoint tiene su caso cruzado:
 * un recurso de otra Gestoria es 404 sin cuerpo. Las estadisticas de Hibernate estan activas para
 * comprobar que listado y detalle no hacen N+1.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class GanaderoEndToEndTest {

    private static final String SECRETO_ONBOARDING = "test-onboarding-secret";

    private static final String NIF_A1 = "70000001A";
    private static final String NIF_A2 = "70000002B";
    private static final String NIF_B = "70000003C";

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
    private Long ganaderoA1;
    private Long ganaderoA2;
    private Long ganaderoB;
    private Long explotacionA1;
    private Long explotacionA2;
    private Long explotacionB1;

    @BeforeEach
    void preparar() throws IOException {
        onboarding("Gestoria Ganaderos A", "ganaderosA@test.com");
        onboarding("Gestoria Ganaderos B", "ganaderosB@test.com");
        tokenA = login("ganaderosA@test.com");
        tokenB = login("ganaderosB@test.com");

        importar(tokenA,
                List.<String[]>of(
                        new String[]{"ES940000000001", "Finca A1", NIF_A1, "Ganadero A Uno"},
                        new String[]{"ES940000000002", "Finca A2", NIF_A1, "Ganadero A Uno"},
                        new String[]{"ES940000000003", "Finca A3", NIF_A2, "Ganadero A Dos"}),
                List.<String[]>of(
                        new String[]{"ES940000000013", "", "ES940000000001"},
                        new String[]{"ES940000000011", "", "ES940000000001"},
                        new String[]{"ES940000000012", "", "ES940000000001"},
                        new String[]{"ES940000000021", "", "ES940000000002"}),
                List.<String[]>of(
                        new String[]{"655 100 001", "Titular A", "ES940000000001", "TITULAR"},
                        new String[]{"655 100 002", "Empleado A", "ES940000000001", "EMPLEADO"},
                        new String[]{"655 100 001", "Titular A", "ES940000000002", "TITULAR"}));
        importar(tokenB,
                List.<String[]>of(new String[]{"ES940000000101", "Finca B1", NIF_B, "Ganadero B"}),
                List.<String[]>of(
                        new String[]{"ES940000000111", "", "ES940000000101"},
                        new String[]{"ES940000000112", "", "ES940000000101"}),
                List.<String[]>of(new String[]{"655 100 101", "Titular B", "ES940000000101", "TITULAR"}));

        ganaderoA1 = idGanadero(NIF_A1);
        ganaderoA2 = idGanadero(NIF_A2);
        ganaderoB = idGanadero(NIF_B);
        Map<String, Long> idsA = idsExplotaciones(tokenA);
        explotacionA1 = idsA.get("ES940000000001");
        explotacionA2 = idsA.get("ES940000000002");
        explotacionB1 = idsExplotaciones(tokenB).get("ES940000000101");
        assertThat(explotacionA1).isNotNull();
        assertThat(explotacionA2).isNotNull();
        assertThat(explotacionB1).isNotNull();

        // Credenciales OVZ reales en BD para que el chequeo "nunca contiene ovz" signifique algo.
        for (Ganadero ganadero : ganaderoRepository.findAll()) {
            ganadero.setOvzUsuario("usuario-ovz-" + ganadero.getNif());
            ganadero.setOvzPasswordCifrada("password-ovz-secreta");
            ganaderoRepository.save(ganadero);
        }
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

    // --- GET /ganaderos ---

    @Test
    void listarGanaderosSoloDevuelveLosDeLaGestoriaConSuNumeroDeExplotaciones() throws IOException {
        ResponseEntity<String> respuestaA = get("/ganaderos", tokenA);
        assertThat(respuestaA.getStatusCode().value()).isEqualTo(200);
        JsonNode contenidoA = objectMapper.readTree(respuestaA.getBody()).get("content");
        assertThat(contenidoA).hasSize(2);
        Map<Long, JsonNode> porId = new HashMap<>();
        contenidoA.forEach(g -> porId.put(g.get("id").asLong(), g));
        assertThat(porId.keySet()).containsExactlyInAnyOrder(ganaderoA1, ganaderoA2);
        assertThat(porId.get(ganaderoA1).get("nombre").asText()).isEqualTo("Ganadero A Uno");
        assertThat(porId.get(ganaderoA1).get("nif").asText()).isEqualTo(NIF_A1);
        assertThat(porId.get(ganaderoA1).get("numeroExplotaciones").asLong()).isEqualTo(2);
        assertThat(porId.get(ganaderoA2).get("numeroExplotaciones").asLong()).isEqualTo(1);

        ResponseEntity<String> respuestaB = get("/ganaderos", tokenB);
        assertThat(respuestaB.getStatusCode().value()).isEqualTo(200);
        JsonNode contenidoB = objectMapper.readTree(respuestaB.getBody()).get("content");
        assertThat(contenidoB).hasSize(1);
        assertThat(contenidoB.get(0).get("id").asLong()).isEqualTo(ganaderoB);
        assertThat(contenidoB.get(0).get("numeroExplotaciones").asLong()).isEqualTo(1);
        assertThat(respuestaB.getBody()).doesNotContain(NIF_A1).doesNotContain(NIF_A2).doesNotContain("Ganadero A");
    }

    @Test
    void listarGanaderosEsPaginado() throws IOException {
        JsonNode pagina = objectMapper.readTree(get("/ganaderos?size=1&page=1", tokenA).getBody());
        assertThat(pagina.get("content")).hasSize(1);
        assertThat(pagina.get("totalElements").asLong()).isEqualTo(2);
    }

    // --- GET /ganaderos/{id} ---

    @Test
    void detalleDeGanaderoDeOtraGestoriaDevuelve404SinCuerpo() {
        ResponseEntity<String> respuesta = get("/ganaderos/" + ganaderoA1, tokenB);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
        assertThat(respuesta.getBody()).isNullOrEmpty();
    }

    @Test
    void detalleDeGanaderoInexistenteDevuelve404SinCuerpo() {
        ResponseEntity<String> respuesta = get("/ganaderos/999999", tokenA);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
        assertThat(respuesta.getBody()).isNullOrEmpty();
    }

    @Test
    void detalleDeGanaderoIncluyeExplotacionesYContactosConRol() throws IOException {
        ResponseEntity<String> respuesta = get("/ganaderos/" + ganaderoA1, tokenA);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        JsonNode json = objectMapper.readTree(respuesta.getBody());
        assertThat(json.get("id").asLong()).isEqualTo(ganaderoA1);
        assertThat(json.get("nombre").asText()).isEqualTo("Ganadero A Uno");
        assertThat(json.get("nif").asText()).isEqualTo(NIF_A1);

        JsonNode explotaciones = json.get("explotaciones");
        assertThat(explotaciones).hasSize(2);
        // Orden determinista por codigoRega.
        JsonNode e1 = explotaciones.get(0);
        JsonNode e2 = explotaciones.get(1);
        assertThat(e1.get("id").asLong()).isEqualTo(explotacionA1);
        assertThat(e1.get("codigoRega").asText()).isEqualTo("ES940000000001");
        assertThat(e1.get("nombre").asText()).isEqualTo("Finca A1");
        assertThat(e2.get("id").asLong()).isEqualTo(explotacionA2);

        Map<String, String> rolPorTelefono = new HashMap<>();
        e1.get("contactos").forEach(c -> {
            assertThat(c.get("contactoId").asLong()).isPositive();
            assertThat(c.get("nombre").asText()).isNotBlank();
            rolPorTelefono.put(c.get("telefono").asText(), c.get("rol").asText());
        });
        assertThat(rolPorTelefono).containsExactlyInAnyOrderEntriesOf(Map.of(
                "+34655100001", "TITULAR",
                "+34655100002", "EMPLEADO"));
        assertThat(e2.get("contactos")).hasSize(1);
        assertThat(e2.get("contactos").get(0).get("telefono").asText()).isEqualTo("+34655100001");
        assertThat(e2.get("contactos").get(0).get("nombre").asText()).isEqualTo("Titular A");
        assertThat(e2.get("contactos").get(0).get("rol").asText()).isEqualTo("TITULAR");
    }

    @Test
    void detalleDeGanaderoConExplotacionSinContactosDevuelveListaVacia() throws IOException {
        JsonNode json = objectMapper.readTree(get("/ganaderos/" + ganaderoA2, tokenA).getBody());

        assertThat(json.get("explotaciones")).hasSize(1);
        JsonNode contactos = json.get("explotaciones").get(0).get("contactos");
        assertThat(contactos.isArray()).isTrue();
        assertThat(contactos).isEmpty();
    }

    @Test
    void unContactoDadoDeBajaDesapareceDelDetalle() throws IOException {
        JsonNode antes = objectMapper.readTree(get("/ganaderos/" + ganaderoA1, tokenA).getBody());
        long empleadoId = -1;
        for (JsonNode c : antes.get("explotaciones").get(0).get("contactos")) {
            if (c.get("telefono").asText().equals("+34655100002")) {
                empleadoId = c.get("contactoId").asLong();
            }
        }
        assertThat(empleadoId).isPositive();

        ResponseEntity<String> baja = exchange("/contactos/" + empleadoId, HttpMethod.DELETE, tokenA);
        assertThat(baja.getStatusCode().value()).isEqualTo(204);

        JsonNode despues = objectMapper.readTree(get("/ganaderos/" + ganaderoA1, tokenA).getBody());
        JsonNode contactosE1 = despues.get("explotaciones").get(0).get("contactos");
        assertThat(contactosE1).hasSize(1);
        assertThat(contactosE1.get(0).get("telefono").asText()).isEqualTo("+34655100001");
        assertThat(despues.toString()).doesNotContain("+34655100002");
    }

    // --- GET /explotaciones/{id}/animales ---

    @Test
    void animalesDeExplotacionDeOtraGestoriaDevuelve404SinCuerpo() {
        ResponseEntity<String> respuesta = get("/explotaciones/" + explotacionA1 + "/animales", tokenB);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
        assertThat(respuesta.getBody()).isNullOrEmpty();
        // Y al reves: A tampoco ve los de B.
        ResponseEntity<String> alReves = get("/explotaciones/" + explotacionB1 + "/animales", tokenA);
        assertThat(alReves.getStatusCode().value()).isEqualTo(404);
        assertThat(alReves.getBody()).isNullOrEmpty();
    }

    @Test
    void animalesDeExplotacionInexistenteDevuelve404() {
        assertThat(get("/explotaciones/999999/animales", tokenA).getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void animalesSoloDevuelveLosDeEsaExplotacionPaginados() throws IOException {
        ResponseEntity<String> respuesta = get("/explotaciones/" + explotacionA1 + "/animales", tokenA);
        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        JsonNode pagina = objectMapper.readTree(respuesta.getBody());
        assertThat(pagina.get("totalElements").asLong()).isEqualTo(3);
        List<String> crotales = new ArrayList<>();
        pagina.get("content").forEach(a -> {
            assertThat(a.get("id").asLong()).isPositive();
            crotales.add(a.get("crotal").asText());
        });
        // Orden por defecto: crotal.
        assertThat(crotales).containsExactly("ES940000000011", "ES940000000012", "ES940000000013");
        JsonNode primero = pagina.get("content").get(0);
        assertThat(primero.get("crotalUltimosDigitos").asText()).isEqualTo("000011");

        JsonNode segundaPagina = objectMapper.readTree(
                get("/explotaciones/" + explotacionA1 + "/animales?size=2&page=1", tokenA).getBody());
        assertThat(segundaPagina.get("content")).hasSize(1);
        assertThat(segundaPagina.get("content").get(0).get("crotal").asText()).isEqualTo("ES940000000013");
        assertThat(segundaPagina.get("totalElements").asLong()).isEqualTo(3);
        assertThat(segundaPagina.get("totalPages").asInt()).isEqualTo(2);

        JsonNode deA2 = objectMapper.readTree(get("/explotaciones/" + explotacionA2 + "/animales", tokenA).getBody());
        assertThat(deA2.get("content")).hasSize(1);
        assertThat(deA2.get("content").get(0).get("crotal").asText()).isEqualTo("ES940000000021");
    }

    // --- Credenciales OVZ, 401 y N+1 ---

    @Test
    void ningunaRespuestaDeGanaderoContieneOvz() {
        String lista = get("/ganaderos", tokenA).getBody();
        String detalle = get("/ganaderos/" + ganaderoA1, tokenA).getBody();

        assertThat(lista).isNotBlank();
        assertThat(detalle).isNotBlank();
        for (String cuerpo : List.of(lista, detalle)) {
            assertThat(cuerpo.toLowerCase(Locale.ROOT)).doesNotContain("ovz");
            assertThat(cuerpo).doesNotContain("password-ovz-secreta");
        }
    }

    @Test
    void sinJwtLasTresRutasDevuelven401() {
        for (String ruta : List.of("/ganaderos", "/ganaderos/" + ganaderoA1, "/explotaciones/" + explotacionA1 + "/animales")) {
            ResponseEntity<String> respuesta = restTemplate.getForEntity(ruta, String.class);
            assertThat(respuesta.getStatusCode().value()).as(ruta).isEqualTo(401);
        }
    }

    /**
     * Listado: 1 consulta de la pagina + 1 consulta agrupada de conteos (+ como mucho 1 count de
     * paginacion). Detalle: Ganadero + Explotaciones + Contactos en lote. Con N+1 (un conteo por
     * Ganadero o una carga de Contacto por enlace) ambos superarian el limite.
     */
    @Test
    void listadoYDetalleNoHacenNMasUno() {
        Statistics estadisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        estadisticas.clear();
        assertThat(get("/ganaderos", tokenA).getStatusCode().value()).isEqualTo(200);
        long consultasListado = estadisticas.getPrepareStatementCount();

        estadisticas.clear();
        assertThat(get("/ganaderos/" + ganaderoA1, tokenA).getStatusCode().value()).isEqualTo(200);
        long consultasDetalle = estadisticas.getPrepareStatementCount();

        assertThat(consultasListado).as("consultas del listado").isLessThanOrEqualTo(3);
        assertThat(consultasDetalle).as("consultas del detalle").isLessThanOrEqualTo(3);
    }

    /**
     * Con 6 Ganaderos en la pagina, un conteo por Ganadero (N+1) daria 1 + 6 = 7 consultas; la
     * consulta agrupada da exactamente 2 (pagina + conteo; la pagina no esta llena, no hay count).
     */
    @Test
    void listadoConMuchosGanaderosSigueSiendoDosConsultas() throws IOException {
        importar(tokenA,
                List.<String[]>of(
                        new String[]{"ES940000000004", "Finca A4", "70000004D", "Ganadero A Cuatro"},
                        new String[]{"ES940000000005", "Finca A5", "70000005E", "Ganadero A Cinco"},
                        new String[]{"ES940000000006", "Finca A6", "70000005E", "Ganadero A Cinco"},
                        new String[]{"ES940000000007", "Finca A7", "70000006F", "Ganadero A Seis"},
                        new String[]{"ES940000000008", "Finca A8", "70000007G", "Ganadero A Siete"}),
                List.of(), List.of());
        Statistics estadisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        estadisticas.clear();
        ResponseEntity<String> respuesta = get("/ganaderos", tokenA);
        long consultas = estadisticas.getPrepareStatementCount();

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        JsonNode contenido = objectMapper.readTree(respuesta.getBody()).get("content");
        assertThat(contenido).hasSize(6);
        Map<String, Long> numeroPorNif = new HashMap<>();
        contenido.forEach(g -> numeroPorNif.put(g.get("nif").asText(), g.get("numeroExplotaciones").asLong()));
        assertThat(numeroPorNif).containsExactlyInAnyOrderEntriesOf(Map.of(
                NIF_A1, 2L, NIF_A2, 1L, "70000004D", 1L, "70000005E", 2L, "70000006F", 1L, "70000007G", 1L));
        assertThat(consultas).as("consultas del listado con 6 ganaderos").isEqualTo(2);
    }

    // --- Ordenacion: lista blanca y orden por defecto estable ---

    @Test
    void ordenarGanaderosPorUnCampoNoPermitidoDevuelve400ConMotivo() throws IOException {
        for (String sort : List.of("ovzUsuario", "ovzPasswordCifrada", "noExiste", "nombre,desc&sort=ovzUsuario")) {
            ResponseEntity<String> respuesta = get("/ganaderos?sort=" + sort, tokenA);
            assertThat(respuesta.getStatusCode().value()).as(sort).isEqualTo(400);
            assertThat(objectMapper.readTree(respuesta.getBody()).get("motivo").asText()).as(sort).isNotBlank();
        }
    }

    @Test
    void ordenarGanaderosPorCamposPermitidosFunciona() throws IOException {
        for (String sort : List.of("nombre", "nif,desc", "id")) {
            assertThat(get("/ganaderos?sort=" + sort, tokenA).getStatusCode().value()).as(sort).isEqualTo(200);
        }
        JsonNode porNifDesc = objectMapper.readTree(get("/ganaderos?sort=nif,desc", tokenA).getBody()).get("content");
        assertThat(porNifDesc.get(0).get("nif").asText()).isEqualTo(NIF_A2);
    }

    @Test
    void ordenarAnimalesPorUnCampoNoPermitidoDevuelve400ConMotivo() throws IOException {
        for (String sort : List.of("explotacion.ganadero.ovzUsuario", "noExiste", "crotalUltimosDigitos")) {
            ResponseEntity<String> respuesta = get("/explotaciones/" + explotacionA1 + "/animales?sort=" + sort, tokenA);
            assertThat(respuesta.getStatusCode().value()).as(sort).isEqualTo(400);
            assertThat(objectMapper.readTree(respuesta.getBody()).get("motivo").asText()).as(sort).isNotBlank();
        }
        for (String sort : List.of("crotal,desc", "id")) {
            assertThat(get("/explotaciones/" + explotacionA1 + "/animales?sort=" + sort, tokenA)
                    .getStatusCode().value()).as(sort).isEqualTo(200);
        }
    }

    /** Decision 21: GET /explotaciones solo ordena por codigoRega, nombre o id. */
    @Test
    void ordenarExplotacionesPorUnCampoNoPermitidoDevuelve400ConMotivo() throws IOException {
        for (String sort : List.of("ganadero.ovzPasswordCifrada", "ganadero.ovzUsuario", "gestoria.id", "noExiste",
                "nombre,desc&sort=ganadero.nif")) {
            ResponseEntity<String> respuesta = get("/explotaciones?sort=" + sort, tokenA);
            assertThat(respuesta.getStatusCode().value()).as(sort).isEqualTo(400);
            assertThat(objectMapper.readTree(respuesta.getBody()).get("motivo").asText()).as(sort)
                    .isEqualTo("Campo de ordenación no permitido.");
        }
    }

    @Test
    void ordenarExplotacionesPorCamposPermitidosFunciona() throws IOException {
        for (String sort : List.of("codigoRega", "nombre,desc", "id,desc")) {
            assertThat(get("/explotaciones?sort=" + sort, tokenA).getStatusCode().value()).as(sort).isEqualTo(200);
        }
        JsonNode porCodigoDesc = objectMapper.readTree(get("/explotaciones?sort=codigoRega,desc", tokenA).getBody())
                .get("content");
        assertThat(porCodigoDesc.get(0).get("codigoRega").asText()).isEqualTo("ES940000000003");
    }

    /** Orden por defecto {codigoRega, id}: estable y lo que el frontend (solo page/size) recibe. */
    @Test
    void ordenPorDefectoDeExplotacionesEsCodigoRega() throws IOException {
        List<String> codigos = new ArrayList<>();
        objectMapper.readTree(get("/explotaciones?page=0&size=20", tokenA).getBody()).get("content")
                .forEach(e -> codigos.add(e.get("codigoRega").asText()));
        assertThat(codigos).containsExactly("ES940000000001", "ES940000000002", "ES940000000003");
    }

    /** Orden por defecto {nombre, id}: dos Ganaderos con el mismo nombre salen por id ascendente. */
    @Test
    void ordenPorDefectoDeGanaderosEsNombreYLuegoId() throws IOException {
        importar(tokenA,
                List.<String[]>of(
                        new String[]{"ES940000000009", "Finca R1", "70000008H", "Ganadero Repetido"},
                        new String[]{"ES940000000010", "Finca R2", "70000009J", "Ganadero Repetido"}),
                List.of(), List.of());
        Long primero = idGanadero("70000008H");
        Long segundo = idGanadero("70000009J");

        JsonNode contenido = objectMapper.readTree(get("/ganaderos", tokenA).getBody()).get("content");
        List<Long> idsRepetidos = new ArrayList<>();
        List<String> nombres = new ArrayList<>();
        contenido.forEach(g -> {
            nombres.add(g.get("nombre").asText());
            if (g.get("nombre").asText().equals("Ganadero Repetido")) {
                idsRepetidos.add(g.get("id").asLong());
            }
        });
        assertThat(nombres).isSorted();
        assertThat(idsRepetidos).containsExactly(Math.min(primero, segundo), Math.max(primero, segundo));
    }

    // --- utilidades ---

    private Long idGanadero(String nif) {
        return ganaderoRepository.findAll().stream()
                .filter(g -> nif.equals(g.getNif()))
                .findFirst().orElseThrow().getId();
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
        return exchange(path, HttpMethod.GET, token);
    }

    private ResponseEntity<String> exchange(String path, HttpMethod metodo, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(path, metodo, new HttpEntity<>(headers), String.class);
    }

    private void importar(String token, List<String[]> filasExplotaciones, List<String[]> filasAnimales,
                          List<String[]> filasContactos) throws IOException {
        byte[] contenido;
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            escribirHoja(workbook.createSheet("Explotaciones"),
                    new String[]{"codigo_rega", "nombre", "nif_ganadero", "nombre_ganadero"}, filasExplotaciones);
            escribirHoja(workbook.createSheet("Animales"),
                    new String[]{"crotal", "especie", "codigo_rega_explotacion"}, filasAnimales);
            escribirHoja(workbook.createSheet("Contactos"),
                    new String[]{"telefono", "nombre", "codigo_explotacion", "rol"}, filasContactos);
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
                return "ganaderos-e2e.xlsx";
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
