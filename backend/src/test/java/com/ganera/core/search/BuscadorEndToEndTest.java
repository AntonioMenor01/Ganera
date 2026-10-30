package com.ganera.core.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganera.core.auth.LoginRequest;
import com.ganera.core.auth.LoginResponse;
import com.ganera.core.contacto.ContactoExplotacionRepository;
import com.ganera.core.contacto.ContactoRepository;
import com.ganera.core.explotacion.AnimalRepository;
import com.ganera.core.explotacion.ExplotacionRepository;
import com.ganera.core.facturacion.SuscripcionRepository;
import com.ganera.core.ganadero.GanaderoRepository;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.gestoria.UsuarioRepository;
import com.ganera.core.onboarding.OnboardingRequest;
import com.ganera.core.onboarding.OnboardingResponse;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E real del buscador global (GET /buscar). Dos gestorías con sus datos (importados por Excel);
 * comprueba que encuentra ganaderos por nombre y por NIF, explotaciones por REGA/nombre y animales
 * por crotal, que respeta el aislamiento multi-tenant (A nunca ve lo de B) y que q corta/vacía no
 * busca.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BuscadorEndToEndTest {

    private static final String SECRETO_ONBOARDING = "test-onboarding-secret";

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ContactoExplotacionRepository contactoExplotacionRepository;
    @Autowired private ContactoRepository contactoRepository;
    @Autowired private AnimalRepository animalRepository;
    @Autowired private ExplotacionRepository explotacionRepository;
    @Autowired private GanaderoRepository ganaderoRepository;
    @Autowired private SuscripcionRepository suscripcionRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private GestoriaRepository gestoriaRepository;

    private String tokenA;
    private String tokenB;

    @BeforeEach
    void preparar() throws IOException {
        onboarding("Gestoria Buscar A", "buscarA@test.com");
        onboarding("Gestoria Buscar B", "buscarB@test.com");
        tokenA = login("buscarA@test.com");
        tokenB = login("buscarB@test.com");

        importar(tokenA,
                List.<String[]>of(
                        new String[]{"ES940000000001", "Finca El Roble", "12345678Z", "Juan Pérez Gómez"},
                        new String[]{"ES940000000002", "Dehesa La Encina", "12345678Z", "Juan Pérez Gómez"}),
                List.<String[]>of(
                        new String[]{"ES040012345678", "", "ES940000000001"},
                        new String[]{"ES040087654321", "", "ES940000000001"}));
        importar(tokenB,
                List.<String[]>of(new String[]{"ES950000000001", "Granja Beta", "87654321X", "Otro Ganadero"}),
                List.<String[]>of(new String[]{"ES040099999999", "", "ES950000000001"}));
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

    @Test
    void encuentraExplotacionPorNombreConSuGanadero() throws Exception {
        JsonNode r = buscar(tokenA, "Roble");
        assertThat(campos(r, "explotaciones", "codigoRega")).containsExactly("ES940000000001");
        assertThat(r.get("explotaciones").get(0).get("ganaderoNombre").asText()).isEqualTo("Juan Pérez Gómez");
    }

    @Test
    void encuentraGanaderoPorNombreSinTildesNiMayusculas() throws Exception {
        JsonNode r = buscar(tokenA, "perez");
        assertThat(campos(r, "ganaderos", "nombre")).contains("Juan Pérez Gómez");
    }

    @Test
    void encuentraGanaderoPorNif() throws Exception {
        JsonNode r = buscar(tokenA, "12345678Z");
        assertThat(campos(r, "ganaderos", "nif")).contains("12345678Z");
    }

    @Test
    void encuentraAnimalPorParteDelCrotal() throws Exception {
        JsonNode r = buscar(tokenA, "012345678");
        assertThat(campos(r, "animales", "crotal")).contains("ES040012345678");
        assertThat(r.get("animales").get(0).get("ganaderoNombre").asText()).isEqualTo("Juan Pérez Gómez");
    }

    @Test
    void noDevuelveNadaDeOtraGestoria() throws Exception {
        // A no ve lo de B.
        assertThat(campos(buscar(tokenA, "Beta"), "explotaciones", "codigoRega")).isEmpty();
        assertThat(campos(buscar(tokenA, "99999999"), "animales", "crotal")).isEmpty();
        // B no ve lo de A.
        assertThat(campos(buscar(tokenB, "Roble"), "explotaciones", "codigoRega")).isEmpty();
        assertThat(campos(buscar(tokenB, "12345678Z"), "ganaderos", "nif")).isEmpty();
    }

    @Test
    void qVaciaOCortaNoBusca() throws Exception {
        for (String grupo : List.of("ganaderos", "explotaciones", "animales")) {
            assertThat(campos(buscar(tokenA, null), grupo, "id")).isEmpty();
            assertThat(campos(buscar(tokenA, "a"), grupo, "id")).isEmpty();
        }
    }

    // --- helpers -----------------------------------------------------------------------------

    private JsonNode buscar(String token, String q) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<String> respuesta = q == null
                ? restTemplate.exchange("/buscar", HttpMethod.GET, new HttpEntity<>(headers), String.class)
                : restTemplate.exchange("/buscar?q={q}", HttpMethod.GET, new HttpEntity<>(headers), String.class, q);
        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        return objectMapper.readTree(respuesta.getBody());
    }

    private List<String> campos(JsonNode root, String grupo, String campo) {
        List<String> valores = new ArrayList<>();
        root.get(grupo).forEach(nodo -> valores.add(nodo.get(campo).asText()));
        return valores;
    }

    private void importar(String token, List<String[]> explotaciones, List<String[]> animales) throws IOException {
        byte[] contenido;
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            escribirHoja(workbook.createSheet("Explotaciones"),
                    new String[]{"codigo_rega", "nombre", "nif_ganadero", "nombre_ganadero"}, explotaciones);
            escribirHoja(workbook.createSheet("Animales"),
                    new String[]{"crotal", "especie", "codigo_rega_explotacion"}, animales);
            escribirHoja(workbook.createSheet("Contactos"),
                    new String[]{"telefono", "nombre", "codigo_explotacion", "rol"}, List.of());
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
                return "buscador-e2e.xlsx";
            }
        });
        ResponseEntity<String> respuesta = restTemplate.postForEntity(
                "/explotaciones/importar", new HttpEntity<>(body, headers), String.class);
        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
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
