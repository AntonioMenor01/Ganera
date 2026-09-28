package com.ganera.core.explotacion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganera.core.auth.LoginRequest;
import com.ganera.core.auth.LoginResponse;
import com.ganera.core.contacto.Contacto;
import com.ganera.core.contacto.ContactoExplotacion;
import com.ganera.core.contacto.ContactoExplotacionRepository;
import com.ganera.core.contacto.ContactoRepository;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E real (servidor embebido + TestRestTemplate, dos Gestorias con su propio JWT) de la hoja
 * "Contactos" de POST /explotaciones/importar. Decision 11: el importador busca el telefono solo
 * dentro de la Gestoria del JWT (findByGestoriaIdAndTelefono, nunca findByTelefono), asi que un
 * telefono que ya es de otra Gestoria acaba como error de fila generico -- sin enlazar el Contacto
 * ajeno a nada y sin revelar que existe en otra Gestoria.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ExplotacionImportContactosEndToEndTest {

    private static final String SECRETO_ONBOARDING = "test-onboarding-secret";
    private static final String MOTIVO_TELEFONO = "No se puede usar ese teléfono para un contacto.";
    private static final String TELEFONO_DE_A = "+34655000001";

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private ObjectMapper objectMapper;
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
    private Long gestoriaA;
    private Long gestoriaB;

    @BeforeEach
    void preparar() {
        gestoriaA = onboarding("Gestoria Import Contactos A", "importContactosA@test.com");
        gestoriaB = onboarding("Gestoria Import Contactos B", "importContactosB@test.com");
        tokenA = login("importContactosA@test.com");
        tokenB = login("importContactosB@test.com");
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

    /**
     * Decision 11 (obligatorio): B importa un telefono que ya es de un Contacto de A. Error de fila
     * generico para B, la fila valida siguiente de B si se importa, el Contacto de A conserva
     * exactamente su enlace original y B nunca ve el Contacto de A.
     */
    @Test
    void importarUnTelefonoDeOtraGestoriaEsErrorGenericoYNoTocaElContactoAjeno() throws IOException {
        importar(tokenA, List.<String[]>of(explotacion("ES930000000001", "Finca A1", "33333333P", "Ganadero A")), List.of());
        Long explotacionA1 = idsExplotaciones(tokenA).get("ES930000000001");

        ResponseEntity<String> creado = exchange("/contactos", HttpMethod.POST, tokenA,
                "{\"telefono\":\"655 000 001\",\"nombre\":\"Contacto de A\"}");
        assertThat(creado.getStatusCode().value()).isEqualTo(201);
        long contactoA = objectMapper.readTree(creado.getBody()).get("id").asLong();
        ResponseEntity<String> enlazado = exchange("/contactos/" + contactoA + "/explotaciones", HttpMethod.POST, tokenA,
                "{\"explotacionId\":" + explotacionA1 + ",\"rol\":\"TITULAR\"}");
        assertThat(enlazado.getStatusCode().value()).isEqualTo(200);

        JsonNode resumenB = importar(tokenB,
                List.<String[]>of(explotacion("ES930000000101", "Finca B1", "44444444Q", "Ganadero B"),
                        explotacion("ES930000000102", "Finca B2", "44444444Q", "Ganadero B")),
                List.<String[]>of(new String[]{"655 000 001", "Intruso de B", "ES930000000101", "TITULAR"},
                        new String[]{"655 000 002", "Valido de B", "ES930000000102", "EMPLEADO"}));

        // Error de fila generico para B, sin mencionar otra Gestoria.
        JsonNode errores = resumenB.get("errores");
        assertThat(errores).hasSize(1);
        assertThat(errores.get(0).get("hoja").asText()).isEqualTo("Contactos");
        assertThat(errores.get(0).get("fila").asInt()).isEqualTo(2);
        String motivo = errores.get(0).get("motivo").asText();
        assertThat(motivo).isEqualTo(MOTIVO_TELEFONO);
        assertThat(motivo.toLowerCase(Locale.ROOT)).doesNotContain("gestori").doesNotContain("contacto de a");
        assertThat(resumenB.get("contactos").get("filasProcesadas").asInt()).isEqualTo(2);
        assertThat(resumenB.get("contactos").get("creadas").asInt()).isEqualTo(1);
        assertThat(resumenB.get("contactos").get("actualizadas").asInt()).isZero();

        // El Contacto de A sigue intacto, con exactamente su enlace original.
        JsonNode contactosDeA = listarContactos(tokenA);
        assertThat(contactosDeA).hasSize(1);
        assertThat(contactosDeA.get(0).get("telefono").asText()).isEqualTo(TELEFONO_DE_A);
        assertThat(contactosDeA.get(0).get("nombre").asText()).isEqualTo("Contacto de A");
        assertThat(contactosDeA.get(0).get("explotaciones")).hasSize(1);
        assertThat(contactosDeA.get(0).get("explotaciones").get(0).get("explotacionId").asLong()).isEqualTo(explotacionA1);

        Contacto enBd = contactoRepository.findByGestoriaIdAndTelefono(gestoriaA, TELEFONO_DE_A).orElseThrow();
        assertThat(enBd.getNombre()).isEqualTo("Contacto de A");
        List<ContactoExplotacion> enlacesDeA = contactoExplotacionRepository.findAll().stream()
                .filter(e -> e.getContacto().getId().equals(enBd.getId()))
                .toList();
        assertThat(enlacesDeA).singleElement()
                .satisfies(e -> assertThat(e.getExplotacion().getId()).isEqualTo(explotacionA1));
        assertThat(contactoRepository.findByGestoriaIdAndTelefono(gestoriaB, TELEFONO_DE_A)).isEmpty();

        // B no ve el Contacto de A, pero si su fila valida posterior.
        JsonNode contactosDeB = listarContactos(tokenB);
        assertThat(contactosDeB).hasSize(1);
        assertThat(contactosDeB.get(0).get("telefono").asText()).isEqualTo("+34655000002");
        assertThat(contactosDeB.get(0).get("nombre").asText()).isEqualTo("Valido de B");
        assertThat(contactosDeB.get(0).get("explotaciones")).hasSize(1);
        assertThat(contactosDeB.toString()).doesNotContain(TELEFONO_DE_A);
    }

    /**
     * Lo que A importa en su hoja Contactos nunca aparece en GET /contactos de B; y una fila de A
     * que apunta a una Explotacion de B es error de fila sin crear ningun enlace hacia B.
     */
    @Test
    void loQueAImportaEnSuHojaContactosNuncaApareceParaB() throws IOException {
        importar(tokenB, List.<String[]>of(explotacion("ES930000000201", "Finca B", "66666666S", "Ganadero B")), List.of());

        JsonNode resumenA = importar(tokenA,
                List.<String[]>of(explotacion("ES930000000301", "Finca A", "55555555R", "Ganadero A")),
                List.<String[]>of(new String[]{"655 000 010", "Titular de A", "ES930000000301", "TITULAR"},
                        new String[]{"655 000 011", "Apunta a B", "ES930000000201", "EMPLEADO"}));

        assertThat(resumenA.get("errores")).hasSize(1);
        assertThat(resumenA.get("errores").get(0).get("fila").asInt()).isEqualTo(3);
        assertThat(resumenA.get("errores").get(0).get("motivo").asText().toLowerCase(Locale.ROOT)).doesNotContain("gestori");
        assertThat(resumenA.get("contactos").get("creadas").asInt()).isEqualTo(1);

        JsonNode contactosDeA = listarContactos(tokenA);
        assertThat(contactosDeA).hasSize(1);
        assertThat(contactosDeA.get(0).get("telefono").asText()).isEqualTo("+34655000010");

        assertThat(listarContactos(tokenB)).isEmpty();
        ResponseEntity<String> conInactivos = exchange("/contactos?incluirInactivos=true", HttpMethod.GET, tokenB, null);
        assertThat(objectMapper.readTree(conInactivos.getBody()).get("content")).isEmpty();

        Long explotacionB = idsExplotaciones(tokenB).get("ES930000000201");
        assertThat(contactoExplotacionRepository.findAll())
                .noneSatisfy(e -> assertThat(e.getExplotacion().getId()).isEqualTo(explotacionB));
        assertThat(contactoRepository.findByGestoriaIdAndTelefono(gestoriaA, "+34655000011")).isEmpty();
    }

    private static String[] explotacion(String codigoRega, String nombre, String nif, String nombreGanadero) {
        return new String[]{codigoRega, nombre, nif, nombreGanadero};
    }

    private Long onboarding(String nombreGestoria, String email) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Secret", SECRETO_ONBOARDING);
        ResponseEntity<OnboardingResponse> respuesta = restTemplate.postForEntity(
                "/internal/onboarding/gestoria",
                new HttpEntity<>(new OnboardingRequest(nombreGestoria, email, "password123", "Usuario"), headers),
                OnboardingResponse.class);
        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        return respuesta.getBody().gestoriaId();
    }

    private String login(String email) {
        ResponseEntity<LoginResponse> respuesta = restTemplate.postForEntity(
                "/auth/login", new LoginRequest(email, "password123"), LoginResponse.class);
        return respuesta.getBody().token();
    }

    private JsonNode listarContactos(String token) throws IOException {
        ResponseEntity<String> respuesta = exchange("/contactos", HttpMethod.GET, token, null);
        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        return objectMapper.readTree(respuesta.getBody()).get("content");
    }

    private Map<String, Long> idsExplotaciones(String token) throws IOException {
        ResponseEntity<String> respuesta = exchange("/explotaciones", HttpMethod.GET, token, null);
        Map<String, Long> ids = new HashMap<>();
        objectMapper.readTree(respuesta.getBody()).get("content")
                .forEach(e -> ids.put(e.get("codigoRega").asText(), e.get("id").asLong()));
        return ids;
    }

    private ResponseEntity<String> exchange(String path, HttpMethod metodo, String token, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        if (json != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        return restTemplate.exchange(path, metodo, new HttpEntity<>(json, headers), String.class);
    }

    /** Con filasContactos vacio no se crea la hoja Contactos (es opcional). */
    private JsonNode importar(String token, List<String[]> filasExplotaciones, List<String[]> filasContactos)
            throws IOException {
        byte[] contenido;
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            List<String[]> explotaciones = new ArrayList<>();
            explotaciones.add(new String[]{"codigo_rega", "nombre", "nif_ganadero", "nombre_ganadero"});
            explotaciones.addAll(filasExplotaciones);
            escribirHoja(workbook.createSheet("Explotaciones"), explotaciones);
            escribirHoja(workbook.createSheet("Animales"),
                    List.<String[]>of(new String[]{"crotal", "especie", "codigo_rega_explotacion"}));
            if (!filasContactos.isEmpty()) {
                List<String[]> contactos = new ArrayList<>();
                contactos.add(new String[]{"telefono", "nombre", "codigo_explotacion", "rol"});
                contactos.addAll(filasContactos);
                escribirHoja(workbook.createSheet("Contactos"), contactos);
            }
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
                return "import-contactos-e2e.xlsx";
            }
        });
        ResponseEntity<String> respuesta = restTemplate.postForEntity(
                "/explotaciones/importar", new HttpEntity<>(body, headers), String.class);
        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        return objectMapper.readTree(respuesta.getBody());
    }

    private static void escribirHoja(Sheet hoja, List<String[]> filas) {
        for (int i = 0; i < filas.size(); i++) {
            Row fila = hoja.createRow(i);
            String[] valores = filas.get(i);
            for (int c = 0; c < valores.length; c++) {
                fila.createCell(c).setCellValue(valores[c]);
            }
        }
    }
}
