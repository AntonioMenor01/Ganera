package com.ganera.core.tramite;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganera.core.auth.LoginRequest;
import com.ganera.core.auth.LoginResponse;
import com.ganera.core.contacto.Contacto;
import com.ganera.core.contacto.ContactoRepository;
import com.ganera.core.explotacion.Animal;
import com.ganera.core.explotacion.AnimalRepository;
import com.ganera.core.explotacion.Explotacion;
import com.ganera.core.explotacion.ExplotacionRepository;
import com.ganera.core.ganadero.Ganadero;
import com.ganera.core.ganadero.GanaderoRepository;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.gestoria.Usuario;
import com.ganera.core.gestoria.UsuarioRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E real (servidor embebido + TestRestTemplate) de los crotales en GET /tramites y
 * GET /tramites/{id} con DOS Gestorias con datos parecidos (mismo sufijo de crotal en ambas).
 * Estadisticas de Hibernate activas para comprobar que el listado carga los crotales de toda la
 * pagina en UNA consulta (sin N+1).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class TramiteCrotalEndToEndTest {

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private TramiteCrotalService tramiteCrotalService;
    @Autowired
    private TramiteCrotalRepository tramiteCrotalRepository;
    @Autowired
    private TramiteRepository tramiteRepository;
    @Autowired
    private ContactoRepository contactoRepository;
    @Autowired
    private AnimalRepository animalRepository;
    @Autowired
    private ExplotacionRepository explotacionRepository;
    @Autowired
    private GanaderoRepository ganaderoRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private GestoriaRepository gestoriaRepository;

    private String tokenA;
    private String tokenB;
    private Gestoria gestoriaA;
    private Gestoria gestoriaB;
    private Explotacion explotacionA;
    private Explotacion explotacionB;
    private Animal animalA;
    private Animal animalB;
    private Contacto contactoA;
    private Contacto contactoB;

    @BeforeEach
    void preparar() {
        gestoriaA = gestoriaRepository.save(new Gestoria("Gestoria crotales E2E A"));
        gestoriaB = gestoriaRepository.save(new Gestoria("Gestoria crotales E2E B"));
        crearUsuario(gestoriaA, "crotalesA@test.com");
        crearUsuario(gestoriaB, "crotalesB@test.com");
        tokenA = login("crotalesA@test.com");
        tokenB = login("crotalesB@test.com");

        explotacionA = nuevaExplotacion(gestoriaA, "ES960000000001");
        explotacionB = nuevaExplotacion(gestoriaB, "ES960000000101");
        animalA = nuevoAnimal(gestoriaA, explotacionA, "ES960000011234");
        animalB = nuevoAnimal(gestoriaB, explotacionB, "ES960000021234");
        contactoA = nuevoContacto(gestoriaA, "+34600960001");
        contactoB = nuevoContacto(gestoriaB, "+34600960002");
    }

    @AfterEach
    void limpiar() {
        tramiteCrotalRepository.deleteAll();
        tramiteRepository.deleteAll();
        animalRepository.deleteAll();
        contactoRepository.deleteAll();
        explotacionRepository.deleteAll();
        ganaderoRepository.deleteAll();
        usuarioRepository.deleteAll();
        gestoriaRepository.deleteAll();
    }

    @Test
    void listadoMuestraLosCrotalesDeCadaGestoriaYNuncaLosDeLaOtra() throws IOException {
        Tramite tramiteA = nuevoTramite(gestoriaA, contactoA, explotacionA);
        Tramite tramiteB = nuevoTramite(gestoriaB, contactoB, explotacionB);
        tramiteCrotalService.reemplazarCrotales(tramiteA, List.of("1234", "ES969999999999"), gestoriaA.getId());
        tramiteCrotalService.reemplazarCrotales(tramiteB, List.of("1234"), gestoriaB.getId());

        ResponseEntity<String> respuestaA = get("/tramites", tokenA);
        assertThat(respuestaA.getStatusCode().value()).isEqualTo(200);
        JsonNode contenidoA = objectMapper.readTree(respuestaA.getBody()).get("content");
        assertThat(contenidoA).hasSize(1);
        JsonNode tA = contenidoA.get(0);
        assertThat(tA.get("id").asLong()).isEqualTo(tramiteA.getId());
        // Los campos que ya usa el frontend siguen ahi.
        assertThat(tA.get("explotacionId").asLong()).isEqualTo(explotacionA.getId());
        assertThat(tA.get("estado").asText()).isEqualTo("PENDIENTE_REVISION");
        JsonNode crotalesA = tA.get("crotales");
        assertThat(crotalesA).hasSize(2);
        assertThat(crotalesA.get(0).get("crotalIndicado").asText()).isEqualTo("1234");
        assertThat(crotalesA.get(0).get("crotal").asText()).isEqualTo("ES960000011234");
        assertThat(crotalesA.get(0).get("animalId").asLong()).isEqualTo(animalA.getId());
        assertThat(crotalesA.get(0).get("enInventario").asBoolean()).isTrue();
        assertThat(crotalesA.get(0).get("resolucion").asText()).isEqualTo("EN_INVENTARIO");
        assertThat(crotalesA.get(1).get("crotal").asText()).isEqualTo("ES969999999999");
        assertThat(crotalesA.get(1).get("animalId").isNull()).isTrue();
        assertThat(crotalesA.get(1).get("enInventario").asBoolean()).isFalse();
        assertThat(crotalesA.get(1).get("resolucion").asText()).isEqualTo("NO_ENCONTRADO");
        assertThat(respuestaA.getBody()).doesNotContain("ES960000021234");

        ResponseEntity<String> respuestaB = get("/tramites", tokenB);
        JsonNode contenidoB = objectMapper.readTree(respuestaB.getBody()).get("content");
        assertThat(contenidoB).hasSize(1);
        assertThat(contenidoB.get(0).get("id").asLong()).isEqualTo(tramiteB.getId());
        JsonNode crotalesB = contenidoB.get(0).get("crotales");
        assertThat(crotalesB).hasSize(1);
        assertThat(crotalesB.get(0).get("crotal").asText()).isEqualTo("ES960000021234");
        assertThat(crotalesB.get(0).get("animalId").asLong()).isEqualTo(animalB.getId());
        assertThat(respuestaB.getBody())
                .doesNotContain("ES960000011234")
                .doesNotContain("ES969999999999");
    }

    @Test
    void detalleMuestraLosCrotalesYElDeOtraGestoriaEs404SinCuerpo() throws IOException {
        Tramite tramiteA = nuevoTramite(gestoriaA, contactoA, explotacionA);
        tramiteCrotalService.reemplazarCrotales(tramiteA, List.of("1234"), gestoriaA.getId());

        ResponseEntity<String> respuestaA = get("/tramites/" + tramiteA.getId(), tokenA);
        assertThat(respuestaA.getStatusCode().value()).isEqualTo(200);
        JsonNode crotales = objectMapper.readTree(respuestaA.getBody()).get("crotales");
        assertThat(crotales).hasSize(1);
        assertThat(crotales.get(0).get("crotal").asText()).isEqualTo("ES960000011234");
        assertThat(crotales.get(0).get("animalId").asLong()).isEqualTo(animalA.getId());

        ResponseEntity<String> respuestaB = get("/tramites/" + tramiteA.getId(), tokenB);
        assertThat(respuestaB.getStatusCode().value()).isEqualTo(404);
        assertThat(respuestaB.getBody()).isNullOrEmpty();
    }

    @Test
    void detalleDeTramiteSinCrotalesDevuelveListaVacia() throws IOException {
        Tramite tramiteA = nuevoTramite(gestoriaA, contactoA, null);

        JsonNode json = objectMapper.readTree(get("/tramites/" + tramiteA.getId(), tokenA).getBody());

        assertThat(json.get("crotales").isArray()).isTrue();
        assertThat(json.get("crotales")).isEmpty();
    }

    /**
     * 5 Tramites con crotales en la pagina: una consulta por Tramite (N+1) daria 1 + 5 = 6; la
     * carga en lote da exactamente 2 (pagina + crotales; la pagina no esta llena, no hay count).
     */
    @Test
    void listadoCargaLosCrotalesDeTodaLaPaginaEnUnaSolaConsulta() throws IOException {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Tramite tramite = nuevoTramite(gestoriaA, contactoA, i % 2 == 0 ? explotacionA : null);
            tramiteCrotalService.reemplazarCrotales(tramite, List.of("1234", "700" + i), gestoriaA.getId());
            ids.add(tramite.getId());
        }
        Statistics estadisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        estadisticas.clear();
        ResponseEntity<String> respuesta = get("/tramites", tokenA);
        long consultas = estadisticas.getPrepareStatementCount();

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        JsonNode contenido = objectMapper.readTree(respuesta.getBody()).get("content");
        assertThat(contenido).hasSize(5);
        Map<Long, Integer> numeroPorTramite = new HashMap<>();
        contenido.forEach(t -> numeroPorTramite.put(t.get("id").asLong(), t.get("crotales").size()));
        assertThat(numeroPorTramite.keySet()).containsExactlyInAnyOrderElementsOf(ids);
        assertThat(numeroPorTramite.values()).containsOnly(2);
        assertThat(consultas).as("consultas del listado con 5 tramites").isEqualTo(2);
    }

    // --- utilidades ---

    private Explotacion nuevaExplotacion(Gestoria gestoria, String codigoRega) {
        Ganadero ganadero = new Ganadero();
        ganadero.setGestoria(gestoria);
        ganadero.setNombre("Ganadero " + codigoRega);
        ganaderoRepository.save(ganadero);

        Explotacion explotacion = new Explotacion();
        explotacion.setGestoria(gestoria);
        explotacion.setGanadero(ganadero);
        explotacion.setCodigoRega(codigoRega);
        explotacion.setNombre("Finca " + codigoRega);
        return explotacionRepository.save(explotacion);
    }

    private Animal nuevoAnimal(Gestoria gestoria, Explotacion explotacion, String crotal) {
        Animal animal = new Animal();
        animal.setGestoria(gestoria);
        animal.setExplotacion(explotacion);
        animal.setCrotal(crotal);
        animal.setCrotalUltimosDigitos(crotal.substring(crotal.length() - 6));
        return animalRepository.save(animal);
    }

    private Contacto nuevoContacto(Gestoria gestoria, String telefono) {
        Contacto contacto = new Contacto();
        contacto.setTelefono(telefono);
        contacto.setNombre("Contacto crotales E2E");
        contacto.setGestoria(gestoria);
        return contactoRepository.save(contacto);
    }

    private Tramite nuevoTramite(Gestoria gestoria, Contacto contacto, Explotacion explotacion) {
        Tramite tramite = new Tramite();
        tramite.setGestoria(gestoria);
        tramite.setContacto(contacto);
        tramite.setExplotacion(explotacion);
        tramite.setEstado(EstadoTramite.PENDIENTE_REVISION);
        return tramiteRepository.save(tramite);
    }

    private void crearUsuario(Gestoria gestoria, String email) {
        Usuario usuario = new Usuario();
        usuario.setGestoria(gestoria);
        usuario.setEmail(email);
        usuario.setPasswordHash(passwordEncoder.encode("password123"));
        usuario.setNombre("Usuario crotales E2E");
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
}
