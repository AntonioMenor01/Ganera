package com.ganera.core.explotacion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganera.core.auth.LoginRequest;
import com.ganera.core.auth.LoginResponse;
import com.ganera.core.facturacion.SuscripcionRepository;
import com.ganera.core.ganadero.GanaderoRepository;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.gestoria.UsuarioRepository;
import com.ganera.core.onboarding.OnboardingRequest;
import com.ganera.core.onboarding.OnboardingResponse;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E real (servidor embebido + TestRestTemplate) de los 400 de POST /explotaciones/importar
 * (mini-prompt de backend tras A2, punto 6): el cuerpo es JSON {@code {"motivo": "..."}} (antes
 * era texto plano), con el texto en espanol y sin el mensaje tecnico de POI, y el fichero se
 * clasifica por su contenido aunque el nombre diga .xlsx.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ExplotacionImportFicheroInvalidoEndToEndTest {

    private static final String SECRETO_ONBOARDING = "test-onboarding-secret";

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private ObjectMapper objectMapper;
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

    private String token;

    @BeforeEach
    void preparar() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Secret", SECRETO_ONBOARDING);
        ResponseEntity<OnboardingResponse> onboarding = restTemplate.postForEntity(
                "/internal/onboarding/gestoria",
                new HttpEntity<>(new OnboardingRequest("Gestoria Import Invalido", "importInvalido@test.com",
                        "password123", "Usuario"), headers),
                OnboardingResponse.class);
        assertThat(onboarding.getStatusCode().value()).isEqualTo(200);
        token = restTemplate.postForEntity("/auth/login",
                new LoginRequest("importInvalido@test.com", "password123"), LoginResponse.class).getBody().token();
    }

    @AfterEach
    void limpiar() {
        animalRepository.deleteAll();
        explotacionRepository.deleteAll();
        ganaderoRepository.deleteAll();
        suscripcionRepository.deleteAll();
        usuarioRepository.deleteAll();
        gestoriaRepository.deleteAll();
    }

    @Test
    void unXlsAntiguoDevuelve400JsonConMotivoEnEspanol() throws IOException {
        byte[] xls = ExplotacionImportControllerTest.xlsAntiguo().getBytes();

        ResponseEntity<String> respuesta = importar(xls, "inventario.xls");

        assertMotivo(respuesta, ExplotacionImportControllerTest.MOTIVO_XLS);
    }

    @Test
    void unTextoPlanoLlamadoXlsxDevuelve400JsonConMotivoGenerico() throws IOException {
        ResponseEntity<String> respuesta = importar(
                "esto no es un Excel".getBytes(StandardCharsets.UTF_8), "inventario.xlsx");

        assertMotivo(respuesta, ExplotacionImportControllerTest.MOTIVO_NO_XLSX);
        assertThat(respuesta.getBody()).doesNotContain("OOXML").doesNotContain("POI");
    }

    @Test
    void unXlsxSinHojaAnimalesDevuelve400JsonConLaHojaQueFalta() throws IOException {
        byte[] sinAnimales;
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("Explotaciones").createRow(0).createCell(0).setCellValue("codigo_rega");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            sinAnimales = out.toByteArray();
        }

        ResponseEntity<String> respuesta = importar(sinAnimales, "sin-animales.xlsx");

        assertMotivo(respuesta, "Falta la hoja 'Animales' en el Excel");
    }

    private void assertMotivo(ResponseEntity<String> respuesta, String motivoEsperado) throws IOException {
        assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
        assertThat(respuesta.getHeaders().getContentType()).isNotNull();
        assertThat(respuesta.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
        JsonNode cuerpo = objectMapper.readTree(respuesta.getBody());
        List<String> campos = new ArrayList<>();
        cuerpo.fieldNames().forEachRemaining(campos::add);
        assertThat(campos).containsExactly("motivo");
        assertThat(cuerpo.get("motivo").asText()).isEqualTo(motivoEsperado);
    }

    private ResponseEntity<String> importar(byte[] contenido, String nombreFichero) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("archivo", new ByteArrayResource(contenido) {
            @Override
            public String getFilename() {
                return nombreFichero;
            }
        });
        return restTemplate.postForEntity("/explotaciones/importar", new HttpEntity<>(body, headers), String.class);
    }
}
