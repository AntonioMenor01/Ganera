package com.ganera.core.contacto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganera.core.auth.LoginRequest;
import com.ganera.core.auth.LoginResponse;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E real (servidor embebido + TestRestTemplate) de los endpoints de Contactos, con DOS
 * Gestorias creadas por /internal/onboarding/gestoria, cada una con su propio JWT y sus propias
 * Explotaciones importadas por Excel. Cada endpoint tiene su caso cruzado entre Gestorias: el
 * aislamiento no se da por supuesto porque "usa el mismo Repository" (ver CLAUDE.md).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ContactoEndToEndTest {

    private static final String SECRETO_ONBOARDING = "test-onboarding-secret";
    private static final String MOTIVO_TELEFONO = "No se puede usar ese teléfono para un contacto.";

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
    private Long explotacionA1;
    private Long explotacionA2;
    private Long explotacionB1;

    @BeforeEach
    void preparar() throws IOException {
        gestoriaA = onboarding("Gestoria Contactos A", "contactosA@test.com");
        onboarding("Gestoria Contactos B", "contactosB@test.com");
        tokenA = login("contactosA@test.com");
        tokenB = login("contactosB@test.com");

        importar(tokenA, Map.of(
                "ES920000000001", "Finca Contactos A1",
                "ES920000000002", "Finca Contactos A2"), "11111111H", "Ganadero A");
        importar(tokenB, Map.of("ES920000000101", "Finca Contactos B1"), "22222222J", "Ganadero B");

        Map<String, Long> idsA = idsExplotaciones(tokenA);
        Map<String, Long> idsB = idsExplotaciones(tokenB);
        explotacionA1 = idsA.get("ES920000000001");
        explotacionA2 = idsA.get("ES920000000002");
        explotacionB1 = idsB.get("ES920000000101");
        assertThat(explotacionA1).isNotNull();
        assertThat(explotacionA2).isNotNull();
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

    // --- POST /contactos ---

    @Test
    void crearContactoNormalizaElTelefonoYDevuelve201() throws IOException {
        ResponseEntity<String> respuesta = crear(tokenA, "612 345 678", "Juan Titular");

        assertThat(respuesta.getStatusCode().value()).isEqualTo(201);
        JsonNode json = objectMapper.readTree(respuesta.getBody());
        assertThat(json.get("id").asLong()).isPositive();
        assertThat(json.get("telefono").asText()).isEqualTo("+34612345678");
        assertThat(json.get("nombre").asText()).isEqualTo("Juan Titular");
        assertThat(json.get("activo").asBoolean()).isTrue();
        assertThat(json.get("explotaciones").isArray()).isTrue();
        assertThat(json.get("explotaciones")).isEmpty();
    }

    @Test
    void crearConTelefonoInvalidoDevuelve400ConMotivoYNoCreaNada() throws IOException {
        ResponseEntity<String> respuesta = crear(tokenA, "12345", "Juan");

        assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
        assertThat(objectMapper.readTree(respuesta.getBody()).get("motivo").asText()).isNotBlank();
        assertThat(contactoRepository.count()).isZero();
    }

    @Test
    void crearConNombreEnBlancoDevuelve400ConMotivo() throws IOException {
        ResponseEntity<String> respuesta = crear(tokenA, "612345678", "   ");

        assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
        assertThat(objectMapper.readTree(respuesta.getBody()).get("motivo").asText()).isNotBlank();
        assertThat(contactoRepository.count()).isZero();
    }

    @Test
    void crearConTelefonoYaUsadoEnLaMismaGestoriaDevuelve409Generico() throws IOException {
        crear(tokenA, "612345678", "Primero");

        ResponseEntity<String> respuesta = crear(tokenA, "+34 612 345 678", "Segundo");

        assertThat(respuesta.getStatusCode().value()).isEqualTo(409);
        assertThat(objectMapper.readTree(respuesta.getBody()).get("motivo").asText()).isEqualTo(MOTIVO_TELEFONO);
        assertThat(contactoRepository.count()).isEqualTo(1);
    }

    @Test
    void crearConTelefonoQueYaUsaOtraGestoriaDevuelve409GenericoSinMencionarla() throws IOException {
        crear(tokenA, "612345678", "Contacto de A");

        ResponseEntity<String> respuesta = crear(tokenB, "612345678", "Intento de B");

        assertThat(respuesta.getStatusCode().value()).isEqualTo(409);
        assertThat(objectMapper.readTree(respuesta.getBody()).get("motivo").asText()).isEqualTo(MOTIVO_TELEFONO);
        assertThat(respuesta.getBody()).doesNotContainIgnoringCase("gestor");
        assertThat(respuesta.getBody()).doesNotContain("Contacto de A");
        assertThat(contactoRepository.count()).isEqualTo(1);
        Contacto deA = contactoRepository.findAll().get(0);
        assertThat(deA.getGestoria().getId()).isEqualTo(gestoriaA);
        assertThat(deA.getNombre()).isEqualTo("Contacto de A");
        assertThat(deA.getTelefono()).isEqualTo("+34612345678");
        assertThat(listar(tokenB, false).get("totalElements").asInt()).isZero();
    }

    @Test
    void nombreDeMasDe255CaracteresDevuelve400ConMotivoNo409() throws IOException {
        long id = idDe(crear(tokenA, "612345678", "Contacto A"));
        String nombreLargo = "N".repeat(256);

        ResponseEntity<String> alCrear = crear(tokenA, "699000111", nombreLargo);
        ResponseEntity<String> alActualizar = put("/contactos/" + id, tokenA,
                "{\"telefono\":\"612345678\",\"nombre\":\"" + nombreLargo + "\"}");

        for (ResponseEntity<String> r : List.of(alCrear, alActualizar)) {
            assertThat(r.getStatusCode().value()).isEqualTo(400);
            assertThat(objectMapper.readTree(r.getBody()).get("motivo").asText())
                    .isNotBlank().isNotEqualTo(MOTIVO_TELEFONO);
        }
        assertThat(contactoRepository.count()).isEqualTo(1);
        assertThat(contactoRepository.findAll().get(0).getNombre()).isEqualTo("Contacto A");
        // 255 exactos si caben (limite de VARCHAR(255)).
        assertThat(crear(tokenA, "699000111", "N".repeat(255)).getStatusCode().value()).isEqualTo(201);
    }

    // --- GET /contactos ---

    @Test
    void listarSoloDevuelveContactosDeLaPropiaGestoriaConSusExplotaciones() throws IOException {
        long idA = idDe(crear(tokenA, "612345678", "Contacto A"));
        crear(tokenB, "622345678", "Contacto B");
        enlazar(tokenA, idA, explotacionA1, "TITULAR");
        enlazar(tokenA, idA, explotacionA2, "EMPLEADO");

        JsonNode paginaA = listar(tokenA, false);
        JsonNode paginaB = listar(tokenB, false);

        assertThat(paginaA.get("totalElements").asInt()).isEqualTo(1);
        JsonNode contactoA = paginaA.get("content").get(0);
        assertThat(contactoA.get("nombre").asText()).isEqualTo("Contacto A");
        assertThat(contactoA.get("explotaciones")).hasSize(2);
        Map<String, String> rolPorRega = new HashMap<>();
        contactoA.get("explotaciones").forEach(e -> {
            assertThat(e.get("nombre").asText()).startsWith("Finca Contactos A");
            assertThat(e.get("explotacionId").asLong()).isIn(explotacionA1, explotacionA2);
            rolPorRega.put(e.get("codigoRega").asText(), e.get("rol").asText());
        });
        assertThat(rolPorRega).containsEntry("ES920000000001", "TITULAR")
                .containsEntry("ES920000000002", "EMPLEADO");

        assertThat(paginaB.get("totalElements").asInt()).isEqualTo(1);
        assertThat(paginaB.toString()).doesNotContain("Contacto A").doesNotContain("+34612345678");
    }

    @Test
    void contactoInactivoNoSaleEnElListadoSalvoConIncluirInactivos() throws IOException {
        long id = idDe(crear(tokenA, "612345678", "Inactivo"));
        crear(tokenA, "632345678", "Activo");
        assertThat(borrar("/contactos/" + id, tokenA).getStatusCode().value()).isEqualTo(204);

        JsonNode porDefecto = listar(tokenA, false);
        JsonNode conInactivos = listar(tokenA, true);

        assertThat(porDefecto.get("totalElements").asInt()).isEqualTo(1);
        assertThat(porDefecto.toString()).doesNotContain("Inactivo");
        assertThat(conInactivos.get("totalElements").asInt()).isEqualTo(2);
        assertThat(conInactivos.toString()).contains("Inactivo");
        // incluirInactivos nunca abre la puerta a otra Gestoria.
        assertThat(listar(tokenB, true).get("totalElements").asInt()).isZero();
    }

    @Test
    void lasSieteRutasDeContactosSinJwtDevuelven401YNoTocanNada() {
        long id = idDe(crear(tokenA, "612345678", "Contacto A"));
        enlazar(tokenA, id, explotacionA1, "TITULAR");
        String contacto = "{\"telefono\":\"699000111\",\"nombre\":\"Sin JWT\"}";
        String enlace = "{\"explotacionId\":" + explotacionA2 + ",\"rol\":\"EMPLEADO\"}";

        Map<String, ResponseEntity<String>> respuestas = new LinkedHashMap<>();
        respuestas.put("GET /contactos", sinJwt("/contactos", HttpMethod.GET, null));
        respuestas.put("POST /contactos", sinJwt("/contactos", HttpMethod.POST, contacto));
        respuestas.put("PUT /contactos/{id}", sinJwt("/contactos/" + id, HttpMethod.PUT, contacto));
        respuestas.put("DELETE /contactos/{id}", sinJwt("/contactos/" + id, HttpMethod.DELETE, null));
        respuestas.put("POST /contactos/{id}/reactivar",
                sinJwt("/contactos/" + id + "/reactivar", HttpMethod.POST, null));
        respuestas.put("POST /contactos/{id}/explotaciones",
                sinJwt("/contactos/" + id + "/explotaciones", HttpMethod.POST, enlace));
        respuestas.put("DELETE /contactos/{id}/explotaciones/{explotacionId}",
                sinJwt("/contactos/" + id + "/explotaciones/" + explotacionA1, HttpMethod.DELETE, null));

        respuestas.forEach((ruta, r) -> assertThat(r.getStatusCode().value()).as(ruta).isEqualTo(401));
        assertThat(contactoRepository.count()).isEqualTo(1);
        Contacto guardado = contactoRepository.findAll().get(0);
        assertThat(guardado.getNombre()).isEqualTo("Contacto A");
        assertThat(guardado.isActivo()).isTrue();
        assertThat(contactoExplotacionRepository.count()).isEqualTo(1);
    }

    // --- PUT /contactos/{id} ---

    @Test
    void actualizarContactoPropioNormalizaTelefonoYDevuelve200() throws IOException {
        long id = idDe(crear(tokenA, "612345678", "Nombre viejo"));

        ResponseEntity<String> respuesta = put("/contactos/" + id, tokenA,
                "{\"telefono\":\"0034 699 000 111\",\"nombre\":\"Nombre nuevo\"}");

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        JsonNode json = objectMapper.readTree(respuesta.getBody());
        assertThat(json.get("telefono").asText()).isEqualTo("+34699000111");
        assertThat(json.get("nombre").asText()).isEqualTo("Nombre nuevo");
        Contacto guardado = contactoRepository.findAll().get(0);
        assertThat(guardado.getTelefono()).isEqualTo("+34699000111");
        assertThat(guardado.getNombre()).isEqualTo("Nombre nuevo");
    }

    @Test
    void actualizarContactoDeOtraGestoriaDevuelve404YNoLoCambia() {
        long idA = idDe(crear(tokenA, "612345678", "Contacto A"));

        ResponseEntity<String> respuesta = put("/contactos/" + idA, tokenB,
                "{\"telefono\":\"699000111\",\"nombre\":\"Secuestrado\"}");

        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
        assertThat(respuesta.getBody()).isNullOrEmpty();
        Contacto guardado = contactoRepository.findAll().get(0);
        assertThat(guardado.getNombre()).isEqualTo("Contacto A");
        assertThat(guardado.getTelefono()).isEqualTo("+34612345678");
    }

    @Test
    void actualizarConTelefonoInvalidoDevuelve400YConTelefonoAjenoDevuelve409() throws IOException {
        long idA = idDe(crear(tokenA, "612345678", "Contacto A"));
        crear(tokenB, "622345678", "Contacto B");

        ResponseEntity<String> invalido = put("/contactos/" + idA, tokenA,
                "{\"telefono\":\"+612345678\",\"nombre\":\"Contacto A\"}");
        ResponseEntity<String> ajeno = put("/contactos/" + idA, tokenA,
                "{\"telefono\":\"622345678\",\"nombre\":\"Contacto A\"}");

        assertThat(invalido.getStatusCode().value()).isEqualTo(400);
        assertThat(objectMapper.readTree(invalido.getBody()).get("motivo").asText()).isNotBlank();
        assertThat(ajeno.getStatusCode().value()).isEqualTo(409);
        assertThat(objectMapper.readTree(ajeno.getBody()).get("motivo").asText()).isEqualTo(MOTIVO_TELEFONO);
        assertThat(contactoRepository.findByIdAndGestoriaId(idA, gestoriaDe(idA)).orElseThrow().getTelefono())
                .isEqualTo("+34612345678");
    }

    @Test
    void actualizarContactoInexistenteDevuelve404() {
        ResponseEntity<String> respuesta = put("/contactos/999999", tokenA,
                "{\"telefono\":\"699000111\",\"nombre\":\"Nadie\"}");

        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
    }

    // --- DELETE /contactos/{id} y POST /contactos/{id}/reactivar ---

    @Test
    void borrarEsLogicoEIdempotente() {
        long id = idDe(crear(tokenA, "612345678", "Contacto A"));

        assertThat(borrar("/contactos/" + id, tokenA).getStatusCode().value()).isEqualTo(204);
        assertThat(borrar("/contactos/" + id, tokenA).getStatusCode().value()).isEqualTo(204);

        Contacto guardado = contactoRepository.findAll().get(0);
        assertThat(guardado.isActivo()).isFalse();
    }

    @Test
    void borrarContactoDeOtraGestoriaDevuelve404YSigueActivo() {
        long idA = idDe(crear(tokenA, "612345678", "Contacto A"));

        ResponseEntity<String> respuesta = borrar("/contactos/" + idA, tokenB);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
        assertThat(contactoRepository.findAll().get(0).isActivo()).isTrue();
    }

    @Test
    void reactivarContactoDeOtraGestoriaDevuelve404YSigueInactivo() {
        long idA = idDe(crear(tokenA, "612345678", "Contacto A"));
        borrar("/contactos/" + idA, tokenA);

        ResponseEntity<String> respuesta = post("/contactos/" + idA + "/reactivar", tokenB, null);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
        assertThat(contactoRepository.findAll().get(0).isActivo()).isFalse();
    }

    @Test
    void contactoInactivoNoSePuedeEnlazarYTrasReactivarSi() throws IOException {
        long id = idDe(crear(tokenA, "612345678", "Contacto A"));
        borrar("/contactos/" + id, tokenA);

        ResponseEntity<String> enlaceInactivo = enlazar(tokenA, id, explotacionA1, "TITULAR");

        assertThat(enlaceInactivo.getStatusCode().value()).isEqualTo(409);
        assertThat(objectMapper.readTree(enlaceInactivo.getBody()).get("motivo").asText()).isNotBlank();
        assertThat(contactoExplotacionRepository.count()).isZero();

        ResponseEntity<String> reactivado = post("/contactos/" + id + "/reactivar", tokenA, null);
        assertThat(reactivado.getStatusCode().value()).isEqualTo(200);
        assertThat(objectMapper.readTree(reactivado.getBody()).get("activo").asBoolean()).isTrue();

        ResponseEntity<String> enlace = enlazar(tokenA, id, explotacionA1, "TITULAR");
        assertThat(enlace.getStatusCode().value()).isEqualTo(200);
        assertThat(contactoExplotacionRepository.count()).isEqualTo(1);
    }

    // --- POST /contactos/{id}/explotaciones ---

    @Test
    void enlazarDosVecesActualizaElRolSinDuplicarElEnlace() throws IOException {
        long id = idDe(crear(tokenA, "612345678", "Contacto A"));

        enlazar(tokenA, id, explotacionA1, "EMPLEADO");
        ResponseEntity<String> respuesta = enlazar(tokenA, id, explotacionA1, "titular");

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        JsonNode explotaciones = objectMapper.readTree(respuesta.getBody()).get("explotaciones");
        assertThat(explotaciones).hasSize(1);
        assertThat(explotaciones.get(0).get("explotacionId").asLong()).isEqualTo(explotacionA1);
        assertThat(explotaciones.get(0).get("codigoRega").asText()).isEqualTo("ES920000000001");
        assertThat(explotaciones.get(0).get("rol").asText()).isEqualTo("TITULAR");
        assertThat(contactoExplotacionRepository.count()).isEqualTo(1);
    }

    @Test
    void enlazarContactoPropioConExplotacionDeOtraGestoriaDevuelve404YNoCreaEnlace() {
        long idA = idDe(crear(tokenA, "612345678", "Contacto A"));

        ResponseEntity<String> respuesta = enlazar(tokenA, idA, explotacionB1, "TITULAR");

        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
        assertThat(respuesta.getBody()).isNullOrEmpty();
        assertThat(contactoExplotacionRepository.count()).isZero();
    }

    @Test
    void enlazarContactoDeOtraGestoriaDevuelve404YNoCreaEnlace() {
        long idA = idDe(crear(tokenA, "612345678", "Contacto A"));

        ResponseEntity<String> respuesta = enlazar(tokenB, idA, explotacionB1, "TITULAR");

        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
        assertThat(contactoExplotacionRepository.count()).isZero();
    }

    @Test
    void enlazarConRolInvalidoOAusenteOSinExplotacionDevuelve400ConMotivo() throws IOException {
        long id = idDe(crear(tokenA, "612345678", "Contacto A"));

        ResponseEntity<String> rolInvalido = enlazar(tokenA, id, explotacionA1, "JEFE");
        ResponseEntity<String> sinRol = post("/contactos/" + id + "/explotaciones", tokenA,
                "{\"explotacionId\":" + explotacionA1 + "}");
        ResponseEntity<String> sinExplotacion = post("/contactos/" + id + "/explotaciones", tokenA,
                "{\"rol\":\"TITULAR\"}");

        for (ResponseEntity<String> r : List.of(rolInvalido, sinRol, sinExplotacion)) {
            assertThat(r.getStatusCode().value()).isEqualTo(400);
            assertThat(objectMapper.readTree(r.getBody()).get("motivo").asText()).isNotBlank();
        }
        assertThat(contactoExplotacionRepository.count()).isZero();
    }

    @Test
    void enlazarConExplotacionInexistenteDevuelve404() {
        long id = idDe(crear(tokenA, "612345678", "Contacto A"));

        ResponseEntity<String> respuesta = enlazar(tokenA, id, 999999L, "TITULAR");

        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
        assertThat(contactoExplotacionRepository.count()).isZero();
    }

    // --- DELETE /contactos/{id}/explotaciones/{explotacionId} ---

    @Test
    void desenlazarEnlacePropioDevuelve204YLoBorra() throws IOException {
        long id = idDe(crear(tokenA, "612345678", "Contacto A"));
        enlazar(tokenA, id, explotacionA1, "TITULAR");
        enlazar(tokenA, id, explotacionA2, "EMPLEADO");

        ResponseEntity<String> respuesta = borrar("/contactos/" + id + "/explotaciones/" + explotacionA1, tokenA);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(204);
        JsonNode explotaciones = listar(tokenA, false).get("content").get(0).get("explotaciones");
        assertThat(explotaciones).hasSize(1);
        assertThat(explotaciones.get(0).get("explotacionId").asLong()).isEqualTo(explotacionA2);
    }

    @Test
    void desenlazarEnlaceDeOtraGestoriaDevuelve404YElEnlaceSigue() {
        long idA = idDe(crear(tokenA, "612345678", "Contacto A"));
        enlazar(tokenA, idA, explotacionA1, "TITULAR");

        ResponseEntity<String> respuesta = borrar("/contactos/" + idA + "/explotaciones/" + explotacionA1, tokenB);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
        assertThat(contactoExplotacionRepository.count()).isEqualTo(1);
    }

    @Test
    void desenlazarEnlaceInexistenteDevuelve404() {
        long id = idDe(crear(tokenA, "612345678", "Contacto A"));

        ResponseEntity<String> respuesta = borrar("/contactos/" + id + "/explotaciones/" + explotacionA1, tokenA);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
    }

    // --- helpers ---

    private Long gestoriaDe(long contactoId) {
        return contactoRepository.findAll().stream()
                .filter(c -> c.getId().equals(contactoId))
                .findFirst().orElseThrow()
                .getGestoria().getId();
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

    // --- GET /contactos: ordenacion (revision 7a M1, igual que decision 21) ---

    /** Fuera de la lista blanca {nombre, telefono, id} -> 400 con el motivo comun. Incluye un
     * campo inexistente, campos anidados (otra entidad) y uno interno que el DTO no expone. */
    @Test
    void ordenarContactosPorUnCampoNoPermitidoDevuelve400ConMotivo() throws IOException {
        crear(tokenA, "612 000 001", "Ana");
        for (String sort : List.of("noExiste", "explotaciones.x", "gestoria.id", "gestoria.nombre", "createdAt",
                "nombre,desc&sort=gestoria.id")) {
            ResponseEntity<String> respuesta = exchange("/contactos?sort=" + sort, HttpMethod.GET, tokenA, null);
            assertThat(respuesta.getStatusCode().value()).as(sort).isEqualTo(400);
            assertThat(objectMapper.readTree(respuesta.getBody()).get("motivo").asText()).as(sort)
                    .isEqualTo("Campo de ordenación no permitido.");
        }
    }

    @Test
    void ordenarContactosPorCamposPermitidosFunciona() throws IOException {
        long carlos = idDe(crear(tokenA, "612 000 003", "Carlos"));
        long ana = idDe(crear(tokenA, "612 000 001", "Ana"));
        long beatriz = idDe(crear(tokenA, "612 000 002", "Beatriz"));

        assertThat(idsContactos("/contactos?sort=telefono,desc")).containsExactly(carlos, beatriz, ana);
        assertThat(idsContactos("/contactos?sort=id")).containsExactly(carlos, ana, beatriz);
        assertThat(idsContactos("/contactos?sort=nombre,desc")).containsExactly(carlos, beatriz, ana);
    }

    /** Sin ?sort=: {nombre, id} -- alfabetico y estable aunque dos Contactos se llamen igual. */
    @Test
    void ordenPorDefectoDeContactosEsNombreYLuegoId() throws IOException {
        long carlos = idDe(crear(tokenA, "612 000 013", "Carlos"));
        long ana1 = idDe(crear(tokenA, "612 000 011", "Ana"));
        long beatriz = idDe(crear(tokenA, "612 000 012", "Beatriz"));
        long ana2 = idDe(crear(tokenA, "612 000 014", "Ana"));

        assertThat(idsContactos("/contactos")).containsExactly(ana1, ana2, beatriz, carlos);
        assertThat(idsContactos("/contactos?page=0&size=2")).containsExactly(ana1, ana2);
        assertThat(idsContactos("/contactos?page=1&size=2")).containsExactly(beatriz, carlos);
    }

    private List<Long> idsContactos(String ruta) throws IOException {
        ResponseEntity<String> respuesta = exchange(ruta, HttpMethod.GET, tokenA, null);
        assertThat(respuesta.getStatusCode().value()).as(ruta).isEqualTo(200);
        List<Long> ids = new java.util.ArrayList<>();
        objectMapper.readTree(respuesta.getBody()).get("content").forEach(c -> ids.add(c.get("id").asLong()));
        return ids;
    }

    private ResponseEntity<String> crear(String token, String telefono, String nombre) {
        try {
            String json = objectMapper.writeValueAsString(Map.of("telefono", telefono, "nombre", nombre));
            return post("/contactos", token, json);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private ResponseEntity<String> enlazar(String token, long contactoId, long explotacionId, String rol) {
        return post("/contactos/" + contactoId + "/explotaciones", token,
                "{\"explotacionId\":" + explotacionId + ",\"rol\":\"" + rol + "\"}");
    }

    private long idDe(ResponseEntity<String> respuesta) {
        assertThat(respuesta.getStatusCode().value()).isEqualTo(201);
        try {
            return objectMapper.readTree(respuesta.getBody()).get("id").asLong();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonNode listar(String token, boolean incluirInactivos) throws IOException {
        ResponseEntity<String> respuesta = exchange(
                "/contactos?incluirInactivos=" + incluirInactivos, HttpMethod.GET, token, null);
        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        return objectMapper.readTree(respuesta.getBody());
    }

    private Map<String, Long> idsExplotaciones(String token) throws IOException {
        ResponseEntity<String> respuesta = exchange("/explotaciones", HttpMethod.GET, token, null);
        Map<String, Long> ids = new HashMap<>();
        objectMapper.readTree(respuesta.getBody()).get("content")
                .forEach(e -> ids.put(e.get("codigoRega").asText(), e.get("id").asLong()));
        return ids;
    }

    private ResponseEntity<String> post(String path, String token, String json) {
        return exchange(path, HttpMethod.POST, token, json);
    }

    private ResponseEntity<String> put(String path, String token, String json) {
        return exchange(path, HttpMethod.PUT, token, json);
    }

    private ResponseEntity<String> borrar(String path, String token) {
        return exchange(path, HttpMethod.DELETE, token, null);
    }

    private ResponseEntity<String> sinJwt(String path, HttpMethod metodo, String json) {
        HttpHeaders headers = new HttpHeaders();
        if (json != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        return restTemplate.exchange(path, metodo, new HttpEntity<>(json, headers), String.class);
    }

    private ResponseEntity<String> exchange(String path, HttpMethod metodo, String token, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        if (json != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        return restTemplate.exchange(path, metodo, new HttpEntity<>(json, headers), String.class);
    }

    private void importar(String token, Map<String, String> explotaciones, String nif, String nombreGanadero)
            throws IOException {
        byte[] contenido;
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet hojaExplotaciones = workbook.createSheet("Explotaciones");
            escribirFila(hojaExplotaciones, 0, "codigo_rega", "nombre", "nif_ganadero", "nombre_ganadero");
            int fila = 1;
            for (Map.Entry<String, String> e : explotaciones.entrySet()) {
                escribirFila(hojaExplotaciones, fila++, e.getKey(), e.getValue(), nif, nombreGanadero);
            }
            escribirFila(workbook.createSheet("Animales"), 0, "crotal", "especie", "codigo_rega_explotacion");
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
                return "contactos-e2e.xlsx";
            }
        });
        ResponseEntity<String> respuesta = restTemplate.postForEntity(
                "/explotaciones/importar", new HttpEntity<>(body, headers), String.class);
        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
    }

    private static void escribirFila(Sheet hoja, int indice, String... valores) {
        Row fila = hoja.createRow(indice);
        for (int c = 0; c < valores.length; c++) {
            fila.createCell(c).setCellValue(valores[c]);
        }
    }
}
