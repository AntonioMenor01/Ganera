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
import com.ganera.core.facturacion.EstadoSuscripcion;
import com.ganera.core.facturacion.Suscripcion;
import com.ganera.core.facturacion.SuscripcionRepository;
import com.ganera.core.ganadero.Ganadero;
import com.ganera.core.ganadero.GanaderoRepository;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.gestoria.Usuario;
import com.ganera.core.gestoria.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E real (servidor embebido + TestRestTemplate) de PATCH /tramites/{id} y de las reglas de
 * aprobar/rechazar (Task 6), con DOS Gestorias con datos parecidos (mismos sufijos de crotal).
 * El estado "sin cambios" se comprueba leyendo la BD con JdbcTemplate, fuera de cualquier
 * transaccion del servidor: asi se ve el rollback real, no una cache de Hibernate.
 * SqlCapturadoInspector registra el SQL para comprobar el bloqueo de fila ("for update").
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "com.ganera.core.tramite.SqlCapturadoInspector")
class TramiteRevisionEndToEndTest {

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private TramiteCrotalService tramiteCrotalService;
    @Autowired
    private TramiteCrotalRepository tramiteCrotalRepository;
    @Autowired
    private TramiteRepository tramiteRepository;
    @Autowired
    private SuscripcionRepository suscripcionRepository;
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

    private Gestoria gestoriaA;
    private Gestoria gestoriaB;
    private String tokenA;
    private String tokenB;
    private Suscripcion suscripcionA;
    private Explotacion explotacionA1;
    private Explotacion explotacionA2;
    private Explotacion explotacionB;
    private Animal animalA1Unico1234;
    private Animal animalA1Doble5678;
    private Animal animalA2Mismo1234;
    private Contacto contactoA;
    private Contacto contactoB;

    @BeforeEach
    void preparar() {
        gestoriaA = gestoriaRepository.save(new Gestoria("Gestoria revision E2E A"));
        gestoriaB = gestoriaRepository.save(new Gestoria("Gestoria revision E2E B"));
        crearUsuario(gestoriaA, "revisionA@test.com");
        crearUsuario(gestoriaB, "revisionB@test.com");
        tokenA = login("revisionA@test.com");
        tokenB = login("revisionB@test.com");
        suscripcionA = suscripcion(gestoriaA, EstadoSuscripcion.ACTIVA);
        suscripcion(gestoriaB, EstadoSuscripcion.ACTIVA);

        explotacionA1 = nuevaExplotacion(gestoriaA, "ES980000000001");
        explotacionA2 = nuevaExplotacion(gestoriaA, "ES980000000002");
        explotacionB = nuevaExplotacion(gestoriaB, "ES980000000101");
        // A1: un unico ...1234 y dos ...5678; A2: otro ...1234.
        animalA1Unico1234 = nuevoAnimal(gestoriaA, explotacionA1, "ES980000011234");
        animalA1Doble5678 = nuevoAnimal(gestoriaA, explotacionA1, "ES980000015678");
        nuevoAnimal(gestoriaA, explotacionA1, "ES980000025678");
        animalA2Mismo1234 = nuevoAnimal(gestoriaA, explotacionA2, "ES980000031234");
        // B: ...9999 solo en B y otro ...1234.
        nuevoAnimal(gestoriaB, explotacionB, "ES980000049999");
        nuevoAnimal(gestoriaB, explotacionB, "ES980000051234");
        contactoA = nuevoContacto(gestoriaA, "+34600980001");
        contactoB = nuevoContacto(gestoriaB, "+34600980002");
    }

    @AfterEach
    void limpiar() {
        // tramite_crotal antes que tramite (FK), igual que en el resto de E2E.
        tramiteCrotalRepository.deleteAll();
        tramiteRepository.deleteAll();
        suscripcionRepository.deleteAll();
        animalRepository.deleteAll();
        contactoRepository.deleteAll();
        explotacionRepository.deleteAll();
        ganaderoRepository.deleteAll();
        usuarioRepository.deleteAll();
        gestoriaRepository.deleteAll();
    }

    // ------------------------------------------------- titular (ficha OVZ, T2)

    private static final String OVZ_USUARIO_SEMBRADO = "ovz-usuario-sembrado-T2";
    private static final String OVZ_PASSWORD_SEMBRADA = "ovz-password-sembrada-T2";

    /** Da al ganadero de la explotacion un NIF y credenciales de OVZ reconocibles. */
    private Ganadero sembrarTitular(Explotacion explotacion, String nombre, String nif) {
        Ganadero ganadero = explotacion.getGanadero();
        ganadero.setNombre(nombre);
        ganadero.setNif(nif);
        ganadero.setOvzUsuario(OVZ_USUARIO_SEMBRADO);
        ganadero.setOvzPasswordCifrada(OVZ_PASSWORD_SEMBRADA);
        return ganaderoRepository.save(ganadero);
    }

    private static void sinCredencialesOvz(String cuerpo) {
        assertThat(cuerpo).doesNotContain("ovzUsuario");
        assertThat(cuerpo).doesNotContain("ovzPassword");
        assertThat(cuerpo).doesNotContain(OVZ_USUARIO_SEMBRADO);
        assertThat(cuerpo).doesNotContain(OVZ_PASSWORD_SEMBRADA);
    }

    @Test
    void detalleDeADevuelveElTitularDeAYNuncaSusCredencialesDeOvz() throws IOException {
        sembrarTitular(explotacionA1, "Titular de A", "11111111H");
        Tramite tramite = nuevoTramite(gestoriaA, contactoA, explotacionA1, null, EstadoTramite.PENDIENTE_REVISION);

        ResponseEntity<String> respuesta = get("/tramites/" + tramite.getId(), tokenA);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        JsonNode cuerpo = objectMapper.readTree(respuesta.getBody());
        assertThat(cuerpo.get("ganaderoNombre").asText()).isEqualTo("Titular de A");
        assertThat(cuerpo.get("ganaderoNif").asText()).isEqualTo("11111111H");
        sinCredencialesOvz(respuesta.getBody());
    }

    @Test
    void detalleSinExplotacionDevuelveElTitularANull() throws IOException {
        Tramite tramite = nuevoTramite(gestoriaA, contactoA, null, null, EstadoTramite.PENDIENTE_REVISION);

        ResponseEntity<String> respuesta = get("/tramites/" + tramite.getId(), tokenA);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        JsonNode cuerpo = objectMapper.readTree(respuesta.getBody());
        assertThat(cuerpo.has("ganaderoNombre")).isTrue();
        assertThat(cuerpo.get("ganaderoNombre").isNull()).isTrue();
        assertThat(cuerpo.get("ganaderoNif").isNull()).isTrue();
    }

    @Test
    void patchQueAsignaLaExplotacionDevuelveElTitularYaRelleno() throws IOException {
        sembrarTitular(explotacionA1, "Titular de A", "11111111H");
        Tramite tramite = nuevoTramite(gestoriaA, contactoA, null, null, EstadoTramite.PENDIENTE_REVISION);

        ResponseEntity<String> respuesta = patch(tramite, tokenA, Map.of("explotacionId", explotacionA1.getId()));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        JsonNode cuerpo = objectMapper.readTree(respuesta.getBody());
        assertThat(cuerpo.get("ganaderoNombre").asText()).isEqualTo("Titular de A");
        assertThat(cuerpo.get("ganaderoNif").asText()).isEqualTo("11111111H");
        sinCredencialesOvz(respuesta.getBody());
    }

    @Test
    void detalleDelTramiteDeBConElTokenDeADevuelve404SinCuerpo() {
        sembrarTitular(explotacionB, "Titular de B", "22222222J");
        Tramite tramiteB = nuevoTramite(gestoriaB, contactoB, explotacionB, null, EstadoTramite.PENDIENTE_REVISION);

        ResponseEntity<String> respuesta = get("/tramites/" + tramiteB.getId(), tokenA);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
        assertThat(respuesta.getBody()).isNullOrEmpty();
    }

    // ------------------------------------------------------------------ PATCH

    /** Caso obligatorio del plan: asignar a un Tramite de A una Explotacion de B. */
    @Test
    void patchAsignandoUnaExplotacionDeOtraGestoriaDevuelve404YNoCambiaNada() {
        Tramite tramite = nuevoTramite(gestoriaA, contactoA, null, null, EstadoTramite.PENDIENTE_REVISION);
        tramiteCrotalService.reemplazarCrotales(tramite, List.of("1234"), gestoriaA.getId());
        Map<String, Object> antes = foto(tramite);

        ResponseEntity<String> respuesta = patch(tramite, tokenA, Map.of(
                "explotacionId", explotacionB.getId(), "tipoTramite", "ALTA_NACIMIENTO", "crotales", List.of("9999")));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
        assertThat(respuesta.getBody()).isNullOrEmpty();
        assertThat(foto(tramite)).isEqualTo(antes);
        assertThat(antes.get("explotacion_id")).isNull();
        assertThat(antes.get("tipo_tramite")).isNull();
    }

    @Test
    void patchDeUnTramiteDeAConElTokenDeBDevuelve404YNoCambiaNada() {
        Tramite tramite = nuevoTramite(gestoriaA, contactoA, explotacionA1, null, EstadoTramite.PENDIENTE_REVISION);
        tramiteCrotalService.reemplazarCrotales(tramite, List.of("1234"), gestoriaA.getId());
        Map<String, Object> antes = foto(tramite);

        ResponseEntity<String> respuesta = patch(tramite, tokenB, Map.of(
                "explotacionId", explotacionB.getId(), "tipoTramite", "BAJA_MUERTE", "crotales", List.of("9999")));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
        assertThat(respuesta.getBody()).isNullOrEmpty();
        assertThat(foto(tramite)).isEqualTo(antes);
    }

    @Test
    void patchFueraDePendienteRevisionDevuelve409YNoCambiaNada() throws IOException {
        Tramite tramite = nuevoTramite(gestoriaA, contactoA, explotacionA1, TipoTramite.ALTA_NACIMIENTO, EstadoTramite.APROBADO);
        Map<String, Object> antes = foto(tramite);

        ResponseEntity<String> respuesta = patch(tramite, tokenA, Map.of("tipoTramite", "BAJA_MUERTE"));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(409);
        assertThat(motivo(respuesta)).isEqualTo("Solo se puede editar un trámite pendiente de revisión.");
        assertThat(foto(tramite)).isEqualTo(antes);
    }

    @Test
    void patchResuelveCadaCrotalContraLaExplotacionDelTramite() throws IOException {
        Tramite tramite = nuevoTramite(gestoriaA, contactoA, null, null, EstadoTramite.PENDIENTE_REVISION);

        ResponseEntity<String> respuesta = patch(tramite, tokenA, Map.of(
                "explotacionId", explotacionA1.getId(),
                "tipoTramite", "baja_muerte",
                "crotales", List.of("ES-9800-0001-5678", "1234", "5678", "9999")));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        JsonNode json = objectMapper.readTree(respuesta.getBody());
        assertThat(json.get("explotacionId").asLong()).isEqualTo(explotacionA1.getId());
        assertThat(json.get("explotacionCodigoRega").asText()).isEqualTo("ES980000000001");
        assertThat(json.get("tipoTramite").asText()).isEqualTo("BAJA_MUERTE");
        assertThat(json.get("estado").asText()).isEqualTo("PENDIENTE_REVISION");
        JsonNode crotales = json.get("crotales");
        assertThat(crotales).hasSize(4);
        // Completo presente en la explotacion.
        assertThat(crotales.get(0).get("crotalIndicado").asText()).isEqualTo("ES980000015678");
        assertThat(crotales.get(0).get("enInventario").asBoolean()).isTrue();
        assertThat(crotales.get(0).get("animalId").asLong()).isEqualTo(animalA1Doble5678.getId());
        // Incompleto con un unico animal: se completa.
        assertThat(crotales.get(1).get("crotalIndicado").asText()).isEqualTo("1234");
        assertThat(crotales.get(1).get("crotal").asText()).isEqualTo("ES980000011234");
        assertThat(crotales.get(1).get("animalId").asLong()).isEqualTo(animalA1Unico1234.getId());
        assertThat(crotales.get(1).get("enInventario").asBoolean()).isTrue();
        // Incompleto con dos animales: ambiguo.
        assertThat(crotales.get(2).get("resolucion").asText()).isEqualTo("AMBIGUO");
        assertThat(crotales.get(2).get("animalId").isNull()).isTrue();
        // ...9999 solo existe en B: nunca se enlaza aunque el sufijo coincida.
        assertThat(crotales.get(3).get("resolucion").asText()).isEqualTo("NO_ENCONTRADO");
        assertThat(crotales.get(3).get("crotal").asText()).isEqualTo("9999");
        assertThat(crotales.get(3).get("animalId").isNull()).isTrue();
        assertThat(respuesta.getBody()).doesNotContain("ES980000049999").doesNotContain("ES980000051234");

        assertThat(crotalesEnBd(tramite)).containsExactly(
                "ES980000015678|ES980000015678|EN_INVENTARIO",
                "1234|ES980000011234|EN_INVENTARIO",
                "5678|5678|AMBIGUO",
                "9999|9999|NO_ENCONTRADO");
        assertThat(foto(tramite).get("tipo_tramite")).isEqualTo("BAJA_MUERTE");
    }

    @Test
    void cambiarSoloLaExplotacionVuelveAResolverLosCrotales() throws IOException {
        Tramite tramite = nuevoTramite(gestoriaA, contactoA, explotacionA1, null, EstadoTramite.PENDIENTE_REVISION);
        tramiteCrotalService.reemplazarCrotales(tramite, List.of("1234"), gestoriaA.getId());
        assertThat(crotalesEnBd(tramite)).containsExactly("1234|ES980000011234|EN_INVENTARIO");

        ResponseEntity<String> respuesta = patch(tramite, tokenA, Map.of("explotacionId", explotacionA2.getId()));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        JsonNode crotal = objectMapper.readTree(respuesta.getBody()).get("crotales").get(0);
        assertThat(crotal.get("crotal").asText()).isEqualTo("ES980000031234");
        assertThat(crotal.get("animalId").asLong()).isEqualTo(animalA2Mismo1234.getId());
        assertThat(crotalesEnBd(tramite)).containsExactly("1234|ES980000031234|EN_INVENTARIO");
    }

    /** Decision 23: dos crotales al mismo Animal -> 409 y rollback del PATCH completo. */
    @Test
    void patchConDosCrotalesDelMismoAnimalDevuelve409YNoGuardaNada() throws IOException {
        Tramite tramite = nuevoTramite(gestoriaA, contactoA, explotacionA1, null, EstadoTramite.PENDIENTE_REVISION);
        tramiteCrotalService.reemplazarCrotales(tramite, List.of("5678"), gestoriaA.getId());
        Map<String, Object> antes = foto(tramite);

        ResponseEntity<String> respuesta = patch(tramite, tokenA, Map.of(
                "explotacionId", explotacionA2.getId(),
                "tipoTramite", "ALTA_NACIMIENTO",
                "crotales", List.of("1234", "ES980000031234")));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(409);
        assertThat(motivo(respuesta)).isEqualTo(
                "Los crotales 1234 y ES980000031234 corresponden al mismo animal (ES980000031234).");
        assertThat(foto(tramite)).isEqualTo(antes);
        assertThat(crotalesEnBd(tramite)).containsExactly("5678|5678|AMBIGUO");
    }

    @Test
    void patchConUnCrotalInvalidoDevuelve400YNoGuardaNada() throws IOException {
        Tramite tramite = nuevoTramite(gestoriaA, contactoA, explotacionA1, null, EstadoTramite.PENDIENTE_REVISION);
        tramiteCrotalService.reemplazarCrotales(tramite, List.of("1234"), gestoriaA.getId());
        Map<String, Object> antes = foto(tramite);

        ResponseEntity<String> respuesta = patch(tramite, tokenA, Map.of(
                "explotacionId", explotacionA2.getId(),
                "tipoTramite", "ALTA_NACIMIENTO",
                "crotales", List.of("9999", "12_34")));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
        assertThat(motivo(respuesta)).isNotBlank();
        assertThat(foto(tramite)).isEqualTo(antes);
    }

    @Test
    void patchConTipoInvalidoDevuelve400ConMotivo() throws IOException {
        Tramite tramite = nuevoTramite(gestoriaA, contactoA, null, null, EstadoTramite.PENDIENTE_REVISION);
        Map<String, Object> antes = foto(tramite);

        ResponseEntity<String> respuesta = patch(tramite, tokenA, Map.of("tipoTramite", "TRASLADO"));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
        assertThat(motivo(respuesta)).isEqualTo("Tipo de trámite no válido.");
        assertThat(foto(tramite)).isEqualTo(antes);
    }

    /** Los tipos de antes de la V20 ya no existen: un cliente desfasado recibe el mismo 400. */
    @Test
    void patchConUnTipoViejoDeAntesDeLaV20Devuelve400ConMotivo() throws IOException {
        Tramite tramite = nuevoTramite(gestoriaA, contactoA, null, null, EstadoTramite.PENDIENTE_REVISION);
        Map<String, Object> antes = foto(tramite);

        ResponseEntity<String> respuesta = patch(tramite, tokenA, Map.of("tipoTramite", "BAJA"));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
        assertThat(motivo(respuesta)).isEqualTo("Tipo de trámite no válido.");
        assertThat(foto(tramite)).isEqualTo(antes);
    }

    @Test
    void patchSinJwtDevuelve401() {
        Tramite tramite = nuevoTramite(gestoriaA, contactoA, null, null, EstadoTramite.PENDIENTE_REVISION);

        ResponseEntity<String> respuesta = patch(tramite, null, Map.of("tipoTramite", "ALTA_NACIMIENTO"));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(401);
        assertThat(foto(tramite).get("tipo_tramite")).isNull();
    }

    /** El Tramite se carga con bloqueo de fila (PESSIMISTIC_WRITE -> "for update" en H2). */
    @Test
    void patchAprobarYRechazarCarganElTramiteConBloqueoDeFila() {
        Tramite editable = nuevoTramite(gestoriaA, contactoA, explotacionA1, TipoTramite.ALTA_NACIMIENTO, EstadoTramite.PENDIENTE_REVISION);
        Tramite rechazable = nuevoTramite(gestoriaA, contactoA, null, null, EstadoTramite.PENDIENTE_REVISION);

        SqlCapturadoInspector.limpiar();
        assertThat(patch(editable, tokenA, Map.of("tipoTramite", "BAJA_MUERTE")).getStatusCode().value()).isEqualTo(200);
        assertThat(seleccionConBloqueoDeTramite()).as("PATCH").isEqualTo(1);

        SqlCapturadoInspector.limpiar();
        assertThat(patch(editable, tokenA, Map.of("crotales", List.of("1234"))).getStatusCode().value()).isEqualTo(200);
        assertThat(seleccionConBloqueoDeTramite()).as("PATCH solo crotales").isEqualTo(1);

        SqlCapturadoInspector.limpiar();
        assertThat(aprobar(editable, tokenA).getStatusCode().value()).isEqualTo(200);
        assertThat(seleccionConBloqueoDeTramite()).as("aprobar").isEqualTo(1);

        SqlCapturadoInspector.limpiar();
        assertThat(rechazar(rechazable, tokenA).getStatusCode().value()).isEqualTo(200);
        assertThat(seleccionConBloqueoDeTramite()).as("rechazar").isEqualTo(1);
    }

    /**
     * Otra transaccion (p. ej. una aprobacion simultanea) tiene bloqueada la fila del Tramite y la
     * pasa a APROBADO. El PATCH ESPERA al bloqueo (no lee la fila vieja), y al conseguirlo ve el
     * estado ya aprobado -> 409 y no toca nada. Determinista: el bloqueo solo se libera cuando
     * H2 muestra la sesion del PATCH esperando por el (INFORMATION_SCHEMA.SESSIONS.BLOCKER_ID).
     *
     * Nota: aqui no se provoca el lock timeout a proposito. En H2 llega como SQLTimeoutException,
     * Hikari cierra la conexion y el rollback falla ("Connection is closed") -- un artefacto de
     * H2+Hikari; en Postgres el FOR UPDATE espera sin limite (Hibernate no aplica un timeout
     * distinto de NOWAIT en PostgreSQL). La traduccion a 409 de ConcurrencyFailureException /
     * DataIntegrityViolationException se prueba en TramiteControllerErroresTest.
     */
    @Test
    void patchConcurrenteEsperaAlBloqueoYVeElEstadoConfirmadoPorLaOtraTransaccion() throws Exception {
        Tramite tramite = nuevoTramite(gestoriaA, contactoA, explotacionA1, TipoTramite.ALTA_NACIMIENTO, EstadoTramite.PENDIENTE_REVISION);
        CountDownLatch bloqueado = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            Future<?> otraTransaccion = hilos.submit(() -> {
                try (Connection conexion = dataSource.getConnection()) {
                    conexion.setAutoCommit(false);
                    try (PreparedStatement ps = conexion.prepareStatement("select id from tramite where id = ? for update")) {
                        ps.setLong(1, tramite.getId());
                        try (ResultSet rs = ps.executeQuery()) {
                            assertThat(rs.next()).isTrue();
                        }
                    }
                    bloqueado.countDown();
                    assertThat(liberar.await(30, TimeUnit.SECONDS)).isTrue();
                    try (PreparedStatement ps = conexion.prepareStatement("update tramite set estado = 'APROBADO' where id = ?")) {
                        ps.setLong(1, tramite.getId());
                        ps.executeUpdate();
                    }
                    conexion.commit();
                }
                return null;
            });
            assertThat(bloqueado.await(10, TimeUnit.SECONDS)).isTrue();

            Future<ResponseEntity<String>> peticion =
                    hilos.submit(() -> patch(tramite, tokenA, Map.of("tipoTramite", "BAJA_MUERTE")));
            long limite = System.currentTimeMillis() + 10_000;
            while (sesionesEsperandoUnBloqueo() == 0) {
                assertThat(System.currentTimeMillis()).as("el PATCH deberia estar esperando al bloqueo").isLessThan(limite);
                assertThat(peticion.isDone()).as("el PATCH no deberia terminar sin el bloqueo").isFalse();
                Thread.sleep(10);
            }
            liberar.countDown();
            otraTransaccion.get(10, TimeUnit.SECONDS);
            ResponseEntity<String> respuesta = peticion.get(10, TimeUnit.SECONDS);

            assertThat(respuesta.getStatusCode().value()).isEqualTo(409);
            assertThat(motivo(respuesta)).isEqualTo("Solo se puede editar un trámite pendiente de revisión.");
            assertThat(foto(tramite).get("tipo_tramite")).isEqualTo("ALTA_NACIMIENTO");
            assertThat(estadoEnBd(tramite)).isEqualTo("APROBADO");
        } finally {
            liberar.countDown();
            hilos.shutdownNow();
        }
    }

    private int sesionesEsperandoUnBloqueo() {
        return jdbcTemplate.queryForObject(
                "select count(*) from information_schema.sessions where blocker_id is not null", Integer.class);
    }

    // ---------------------------------------------------------------- aprobar

    @Test
    void aprobarSinExplotacionDevuelve409NombrandoLoQueFaltaYElCrotal() throws IOException {
        Tramite tramite = nuevoTramite(gestoriaA, contactoA, null, TipoTramite.ALTA_NACIMIENTO, EstadoTramite.PENDIENTE_REVISION);
        tramiteCrotalService.reemplazarCrotales(tramite, List.of("1234"), gestoriaA.getId());

        ResponseEntity<String> respuesta = aprobar(tramite, tokenA);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(409);
        assertThat(motivo(respuesta)).isEqualTo("Falta asignar la explotación. "
                + "El crotal 1234 no se puede comprobar porque el trámite no tiene explotación.");
        assertThat(estadoEnBd(tramite)).isEqualTo("PENDIENTE_REVISION");
    }

    @Test
    void aprobarSinTipoDevuelve409() throws IOException {
        Tramite tramite = nuevoTramite(gestoriaA, contactoA, explotacionA1, null, EstadoTramite.PENDIENTE_REVISION);

        ResponseEntity<String> respuesta = aprobar(tramite, tokenA);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(409);
        assertThat(motivo(respuesta)).isEqualTo("Falta el tipo de trámite.");
        assertThat(estadoEnBd(tramite)).isEqualTo("PENDIENTE_REVISION");
    }

    @Test
    void aprobarConCrotalAmbiguoOIncompletoNoEncontradoDevuelve409NombrandoCadaCrotal() throws IOException {
        Tramite tramite = nuevoTramite(gestoriaA, contactoA, explotacionA1, TipoTramite.ALTA_NACIMIENTO, EstadoTramite.PENDIENTE_REVISION);
        tramiteCrotalService.reemplazarCrotales(tramite, List.of("1234", "5678", "9999"), gestoriaA.getId());

        ResponseEntity<String> respuesta = aprobar(tramite, tokenA);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(409);
        assertThat(motivo(respuesta)).isEqualTo("El crotal 5678 es ambiguo (varios animales coinciden). "
                + "El crotal 9999 no está en el inventario y está incompleto.");
        assertThat(estadoEnBd(tramite)).isEqualTo("PENDIENTE_REVISION");
    }

    /** Decision 25: un crotal completo que no esta en inventario (entrada de animales) si vale. */
    @Test
    void aprobarConCrotalCompletoNoEncontradoDevuelve200() throws IOException {
        Tramite tramite = nuevoTramite(gestoriaA, contactoA, explotacionA1, TipoTramite.ALTA_NACIMIENTO, EstadoTramite.PENDIENTE_REVISION);
        tramiteCrotalService.reemplazarCrotales(tramite, List.of("ES980000077777", "1234"), gestoriaA.getId());

        ResponseEntity<String> respuesta = aprobar(tramite, tokenA);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        JsonNode json = objectMapper.readTree(respuesta.getBody());
        assertThat(json.get("estado").asText()).isEqualTo("APROBADO");
        assertThat(json.get("crotales").get(0).get("resolucion").asText()).isEqualTo("NO_ENCONTRADO");
        assertThat(json.get("crotales").get(1).get("resolucion").asText()).isEqualTo("EN_INVENTARIO");
        assertThat(estadoEnBd(tramite)).isEqualTo("APROBADO");
    }

    @Test
    void aprobarUnTramiteResueltoDevuelve200() throws IOException {
        Tramite tramite = nuevoTramite(gestoriaA, contactoA, explotacionA1, TipoTramite.BAJA_MUERTE, EstadoTramite.PENDIENTE_REVISION);
        tramiteCrotalService.reemplazarCrotales(tramite, List.of("1234"), gestoriaA.getId());

        ResponseEntity<String> respuesta = aprobar(tramite, tokenA);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        JsonNode json = objectMapper.readTree(respuesta.getBody());
        assertThat(json.get("estado").asText()).isEqualTo("APROBADO");
        assertThat(json.get("explotacionId").asLong()).isEqualTo(explotacionA1.getId());
        assertThat(json.get("crotales").get(0).get("crotal").asText()).isEqualTo("ES980000011234");
        assertThat(estadoEnBd(tramite)).isEqualTo("APROBADO");
    }

    /**
     * La resolucion guardada no se da por buena: al aprobar se re-resuelve contra el inventario
     * actual. Si la re-resolucion CAMBIA algo -> 409 especifico, la nueva resolucion SI se guarda
     * (para que el detalle la muestre) y el estado no cambia. El segundo intento sigue las reglas
     * normales: aqui el crotal es ahora ambiguo -> 409 normal, con rollback completo.
     */
    @Test
    void aprobarConUnCrotalQueAhoraEsAmbiguoGuardaLaNuevaResolucionYDevuelve409() throws IOException {
        Tramite tramite = nuevoTramite(gestoriaA, contactoA, explotacionA1, TipoTramite.ALTA_NACIMIENTO, EstadoTramite.PENDIENTE_REVISION);
        tramiteCrotalService.reemplazarCrotales(tramite, List.of("1234"), gestoriaA.getId());
        nuevoAnimal(gestoriaA, explotacionA1, "ES980000091234");

        ResponseEntity<String> respuesta = aprobar(tramite, tokenA);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(409);
        assertThat(motivo(respuesta)).isEqualTo(
                "La resolución de los crotales ha cambiado desde la revisión (inventario actualizado): "
                        + "1234 antes ES980000011234, ahora ambiguo. Revisa el trámite antes de aprobarlo.");
        assertThat(estadoEnBd(tramite)).isEqualTo("PENDIENTE_REVISION");
        assertThat(crotalesEnBd(tramite)).containsExactly("1234|1234|AMBIGUO");
        JsonNode detalle = objectMapper.readTree(get("/tramites/" + tramite.getId(), tokenA).getBody());
        assertThat(detalle.get("crotales").get(0).get("resolucion").asText()).isEqualTo("AMBIGUO");

        ResponseEntity<String> segundo = aprobar(tramite, tokenA);

        assertThat(segundo.getStatusCode().value()).isEqualTo(409);
        assertThat(motivo(segundo)).isEqualTo("El crotal 1234 es ambiguo (varios animales coinciden).");
        assertThat(estadoEnBd(tramite)).isEqualTo("PENDIENTE_REVISION");
        assertThat(crotalesEnBd(tramite)).containsExactly("1234|1234|AMBIGUO");
    }

    /**
     * Escenario A de la revision: "1234" era X (A1). Un reimport mueve X a A2 y mete Y ...1234 en
     * A1. Aprobar NO puede dar 200 por Y (animal que el revisor no vio): 409 especifico, el detalle
     * ya muestra Y, y solo el segundo aprobar (reglas normales) lo aprueba.
     */
    @Test
    void aprobarNoApruebaEnSilencioUnAnimalDistintoDelRevisado() throws IOException {
        Tramite tramite = nuevoTramite(gestoriaA, contactoA, explotacionA1, TipoTramite.ALTA_NACIMIENTO, EstadoTramite.PENDIENTE_REVISION);
        assertThat(patch(tramite, tokenA, Map.of("crotales", List.of("1234"))).getStatusCode().value()).isEqualTo(200);
        assertThat(crotalesEnBd(tramite)).containsExactly("1234|ES980000011234|EN_INVENTARIO");
        jdbcTemplate.update("update animal set explotacion_id = ? where id = ?",
                explotacionA2.getId(), animalA1Unico1234.getId());
        Animal otro = nuevoAnimal(gestoriaA, explotacionA1, "ES980000021234");

        ResponseEntity<String> respuesta = aprobar(tramite, tokenA);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(409);
        assertThat(motivo(respuesta)).isEqualTo(
                "La resolución de los crotales ha cambiado desde la revisión (inventario actualizado): "
                        + "1234 antes ES980000011234, ahora ES980000021234. Revisa el trámite antes de aprobarlo.");
        assertThat(estadoEnBd(tramite)).isEqualTo("PENDIENTE_REVISION");
        JsonNode crotal = objectMapper.readTree(get("/tramites/" + tramite.getId(), tokenA).getBody())
                .get("crotales").get(0);
        assertThat(crotal.get("crotal").asText()).isEqualTo("ES980000021234");
        assertThat(crotal.get("animalId").asLong()).isEqualTo(otro.getId());

        ResponseEntity<String> segundo = aprobar(tramite, tokenA);

        assertThat(segundo.getStatusCode().value()).isEqualTo(200);
        assertThat(objectMapper.readTree(segundo.getBody()).get("estado").asText()).isEqualTo("APROBADO");
        assertThat(estadoEnBd(tramite)).isEqualTo("APROBADO");
    }

    /**
     * Escenario B: el revisor vio un crotal completo EN_INVENTARIO; el animal sale de la
     * explotacion -> NO_ENCONTRADO completo. Aprobar da 409 especifico (no aprueba en silencio);
     * el segundo intento lo permite (decision 25: completo no encontrado es aprobable).
     */
    @Test
    void aprobarNoApruebaEnSilencioUnCrotalQueHaSalidoDelInventario() throws IOException {
        Tramite tramite = nuevoTramite(gestoriaA, contactoA, explotacionA1, TipoTramite.BAJA_MUERTE, EstadoTramite.PENDIENTE_REVISION);
        assertThat(patch(tramite, tokenA, Map.of("crotales", List.of("ES980000011234"))).getStatusCode().value())
                .isEqualTo(200);
        jdbcTemplate.update("update animal set explotacion_id = ? where id = ?",
                explotacionA2.getId(), animalA1Unico1234.getId());

        ResponseEntity<String> respuesta = aprobar(tramite, tokenA);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(409);
        assertThat(motivo(respuesta)).isEqualTo(
                "La resolución de los crotales ha cambiado desde la revisión (inventario actualizado): "
                        + "ES980000011234 antes ES980000011234, ahora no está en el inventario. "
                        + "Revisa el trámite antes de aprobarlo.");
        assertThat(estadoEnBd(tramite)).isEqualTo("PENDIENTE_REVISION");
        JsonNode crotal = objectMapper.readTree(get("/tramites/" + tramite.getId(), tokenA).getBody())
                .get("crotales").get(0);
        assertThat(crotal.get("resolucion").asText()).isEqualTo("NO_ENCONTRADO");
        assertThat(crotal.get("enInventario").asBoolean()).isFalse();

        ResponseEntity<String> segundo = aprobar(tramite, tokenA);

        assertThat(segundo.getStatusCode().value()).isEqualTo(200);
        assertThat(estadoEnBd(tramite)).isEqualTo("APROBADO");
    }

    /**
     * Revision M6: dos aprobaciones simultaneas del mismo Tramite. Una transaccion externa tiene
     * la fila bloqueada hasta que H2 muestra LAS DOS peticiones esperando; al liberarla se
     * serializan: exactamente un 200 APROBADO y un 409 (ya no esta pendiente). Nunca dos 200
     * (en 3c, dos envios a OVZ.net).
     */
    @Test
    void dosAprobacionesSimultaneasDanExactamenteUn200YUn409() throws Exception {
        Tramite tramite = nuevoTramite(gestoriaA, contactoA, explotacionA1, TipoTramite.ALTA_NACIMIENTO, EstadoTramite.PENDIENTE_REVISION);
        CountDownLatch bloqueado = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(3);
        try {
            Future<?> otraTransaccion = hilos.submit(() -> {
                try (Connection conexion = dataSource.getConnection()) {
                    conexion.setAutoCommit(false);
                    try (PreparedStatement ps = conexion.prepareStatement("select id from tramite where id = ? for update")) {
                        ps.setLong(1, tramite.getId());
                        try (ResultSet rs = ps.executeQuery()) {
                            assertThat(rs.next()).isTrue();
                        }
                    }
                    bloqueado.countDown();
                    assertThat(liberar.await(30, TimeUnit.SECONDS)).isTrue();
                    conexion.rollback();
                }
                return null;
            });
            assertThat(bloqueado.await(10, TimeUnit.SECONDS)).isTrue();

            // Las dos pantallas muestran la misma version (la actual): la que gane el bloqueo
            // aprueba; la otra ve el Tramite ya APROBADO (el estado se comprueba antes que la version).
            long version = versionEnBd(tramite);
            Future<ResponseEntity<String>> primera = hilos.submit(() -> aprobarConVersion(tramite, tokenA, version));
            Future<ResponseEntity<String>> segunda = hilos.submit(() -> aprobarConVersion(tramite, tokenA, version));
            long limite = System.currentTimeMillis() + 10_000;
            while (sesionesEsperandoUnBloqueo() < 2) {
                assertThat(System.currentTimeMillis()).as("las dos aprobaciones deberian esperar al bloqueo").isLessThan(limite);
                assertThat(primera.isDone() || segunda.isDone()).as("ninguna deberia terminar sin el bloqueo").isFalse();
                Thread.sleep(10);
            }
            liberar.countDown();
            otraTransaccion.get(10, TimeUnit.SECONDS);
            List<ResponseEntity<String>> respuestas =
                    List.of(primera.get(10, TimeUnit.SECONDS), segunda.get(10, TimeUnit.SECONDS));

            assertThat(respuestas).extracting(r -> r.getStatusCode().value()).containsExactlyInAnyOrder(200, 409);
            ResponseEntity<String> conflicto = respuestas.stream()
                    .filter(r -> r.getStatusCode().value() == 409).findFirst().orElseThrow();
            assertThat(motivo(conflicto)).isEqualTo("Solo se puede aprobar un trámite pendiente de revisión.");
            assertThat(estadoEnBd(tramite)).isEqualTo("APROBADO");
        } finally {
            liberar.countDown();
            hilos.shutdownNow();
        }
    }

    @Test
    void aprobarConDosCrotalesDelMismoAnimalDevuelve409() throws IOException {
        Tramite tramite = nuevoTramite(gestoriaA, contactoA, explotacionA1, TipoTramite.ALTA_NACIMIENTO, EstadoTramite.PENDIENTE_REVISION);
        tramiteCrotalService.reemplazarCrotales(tramite, List.of("1234", "ES980000011234"), gestoriaA.getId());

        ResponseEntity<String> respuesta = aprobar(tramite, tokenA);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(409);
        assertThat(motivo(respuesta)).isEqualTo(
                "Los crotales 1234 y ES980000011234 corresponden al mismo animal (ES980000011234).");
        assertThat(estadoEnBd(tramite)).isEqualTo("PENDIENTE_REVISION");
    }

    /** Decision 12: aprobar y rechazar solo desde PENDIENTE_REVISION. */
    @Test
    void aprobarYRechazarDesdeAprobadoORechazadoDevuelven409YNoCambianElEstado() throws IOException {
        for (EstadoTramite estado : List.of(EstadoTramite.APROBADO, EstadoTramite.RECHAZADO)) {
            Tramite tramite = nuevoTramite(gestoriaA, contactoA, explotacionA1, TipoTramite.ALTA_NACIMIENTO, estado);

            ResponseEntity<String> aprobar = aprobar(tramite, tokenA);
            ResponseEntity<String> rechazar = rechazar(tramite, tokenA);

            assertThat(aprobar.getStatusCode().value()).as("aprobar desde " + estado).isEqualTo(409);
            assertThat(motivo(aprobar)).isEqualTo("Solo se puede aprobar un trámite pendiente de revisión.");
            assertThat(rechazar.getStatusCode().value()).as("rechazar desde " + estado).isEqualTo(409);
            assertThat(motivo(rechazar)).isEqualTo("Solo se puede rechazar un trámite pendiente de revisión.");
            assertThat(estadoEnBd(tramite)).isEqualTo(estado.name());
        }
    }

    /** El 403 de suscripcion va antes que cualquier otra comprobacion. */
    @Test
    void aprobarConSuscripcionSuspendidaOSinSuscripcionDevuelve403AunqueElTramiteSeaAprobable() throws IOException {
        Tramite aprobable = nuevoTramite(gestoriaA, contactoA, explotacionA1, TipoTramite.ALTA_NACIMIENTO, EstadoTramite.PENDIENTE_REVISION);
        suscripcionA.setEstado(EstadoSuscripcion.SUSPENDIDA);
        suscripcionRepository.save(suscripcionA);

        ResponseEntity<String> suspendida = aprobar(aprobable, tokenA);
        assertThat(suspendida.getStatusCode().value()).isEqualTo(403);
        assertThat(motivo(suspendida)).isEqualTo(TramiteController.MOTIVO_SUSCRIPCION_NO_PERMITE_APROBAR)
                .isEqualTo("Tu suscripción no permite aprobar trámites ahora mismo (prueba terminada o suscripción "
                        + "suspendida). Ponte en contacto con Ganera para regularizarla.");
        assertThat(estadoEnBd(aprobable)).isEqualTo("PENDIENTE_REVISION");

        Gestoria gestoriaC = gestoriaRepository.save(new Gestoria("Gestoria revision E2E C sin suscripcion"));
        crearUsuario(gestoriaC, "revisionC@test.com");
        String tokenC = login("revisionC@test.com");
        Tramite yaAprobado = nuevoTramite(gestoriaC, nuevoContacto(gestoriaC, "+34600980003"), null, null, EstadoTramite.APROBADO);

        // Sin Suscripcion: 403 aunque el Tramite daria 409 por estado.
        ResponseEntity<String> sinSuscripcion = aprobar(yaAprobado, tokenC);
        assertThat(sinSuscripcion.getStatusCode().value()).isEqualTo(403);
        assertThat(motivo(sinSuscripcion)).isEqualTo(TramiteController.MOTIVO_SUSCRIPCION_NO_PERMITE_APROBAR);
        assertThat(estadoEnBd(yaAprobado)).isEqualTo("APROBADO");
    }

    @Test
    void aprobarYRechazarUnTramiteDeAConElTokenDeBDevuelven404YNoLoCambian() {
        Tramite tramite = nuevoTramite(gestoriaA, contactoA, explotacionA1, TipoTramite.ALTA_NACIMIENTO, EstadoTramite.PENDIENTE_REVISION);

        ResponseEntity<String> aprobar = aprobar(tramite, tokenB);
        ResponseEntity<String> rechazar = rechazar(tramite, tokenB);

        assertThat(aprobar.getStatusCode().value()).isEqualTo(404);
        assertThat(aprobar.getBody()).isNullOrEmpty();
        assertThat(rechazar.getStatusCode().value()).isEqualTo(404);
        assertThat(rechazar.getBody()).isNullOrEmpty();
        assertThat(estadoEnBd(tramite)).isEqualTo("PENDIENTE_REVISION");
    }

    @Test
    void rechazarNoExigeExplotacionNiTipo() throws IOException {
        Tramite tramite = nuevoTramite(gestoriaB, contactoB, null, null, EstadoTramite.PENDIENTE_REVISION);

        ResponseEntity<String> respuesta = rechazar(tramite, tokenB);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(objectMapper.readTree(respuesta.getBody()).get("estado").asText()).isEqualTo("RECHAZADO");
        assertThat(estadoEnBd(tramite)).isEqualTo("RECHAZADO");
    }

    // ------------------------------------------------ listado: ordenacion (decision 21)

    @Test
    void ordenarTramitesPorUnCampoNoPermitidoDevuelve400ConMotivo() throws IOException {
        for (String sort : List.of("contacto.telefono", "gestoria.id", "explotacion.ganadero.ovzUsuario",
                "motivoError", "noExiste", "id,desc&sort=contacto.nombre")) {
            ResponseEntity<String> respuesta = get("/tramites?sort=" + sort, tokenA);
            assertThat(respuesta.getStatusCode().value()).as(sort).isEqualTo(400);
            assertThat(motivo(respuesta)).as(sort).isEqualTo("Campo de ordenación no permitido.");
        }
    }

    @Test
    void ordenarTramitesPorCamposPermitidosFunciona() throws IOException {
        Tramite primero = nuevoTramite(gestoriaA, contactoA, null, null, EstadoTramite.PENDIENTE_REVISION);
        Tramite segundo = nuevoTramite(gestoriaA, contactoA, null, null, EstadoTramite.APROBADO);
        for (String sort : List.of("id", "estado,desc", "createdAt,asc")) {
            assertThat(get("/tramites?sort=" + sort, tokenA).getStatusCode().value()).as(sort).isEqualTo(200);
        }
        assertThat(idsDelListado("/tramites?sort=id,asc", tokenA)).containsExactly(primero.getId(), segundo.getId());
    }

    /** Orden por defecto {createdAt desc, id desc}: lo mas reciente primero, estable si empatan. */
    @Test
    void ordenPorDefectoDeTramitesEsElMasRecientePrimero() throws IOException {
        Tramite primero = nuevoTramite(gestoriaA, contactoA, null, null, EstadoTramite.PENDIENTE_REVISION);
        Tramite segundo = nuevoTramite(gestoriaA, contactoA, null, null, EstadoTramite.PENDIENTE_REVISION);
        Tramite tercero = nuevoTramite(gestoriaA, contactoA, null, null, EstadoTramite.APROBADO);
        // Mismo instante en dos de ellos: decide el id.
        jdbcTemplate.update("update tramite set created_at = (select created_at from tramite where id = ?) where id = ?",
                primero.getId(), segundo.getId());
        nuevoTramite(gestoriaB, contactoB, null, null, EstadoTramite.PENDIENTE_REVISION);

        assertThat(idsDelListado("/tramites?page=0&size=20", tokenA))
                .containsExactly(tercero.getId(), segundo.getId(), primero.getId());
        assertThat(idsDelListado("/tramites?page=0&size=20&estado=PENDIENTE_REVISION", tokenA))
                .containsExactly(segundo.getId(), primero.getId());
    }

    /** Decision 31: los tests esperan bloqueos con un LOCK_TIMEOUT generoso (H2 trae 1000 ms). */
    @Test
    void laBdDeTestTieneUnLockTimeoutGeneroso() {
        assertThat(jdbcTemplate.queryForObject("select lock_timeout()", Integer.class)).isGreaterThanOrEqualTo(10_000);
    }

    // ---------------------------------------------- version optimista (decision 27)

    /**
     * Revision R1 de Task 6: el "no se aprueba lo que el revisor no vio" vale por pantalla, no por
     * peticion. Tras un cambio de inventario, el primer aprobar (con la version que se vio) da el
     * 409 especifico, GUARDA la nueva resolucion e INCREMENTA la version aunque solo haya tocado
     * tramite_crotal. Un segundo intento con la version antigua (la misma pantalla, o la de otro
     * usuario que abrio el tramite antes) vuelve a dar 409 y no aprueba nada. Solo con la version
     * nueva -- la que devuelve el detalle recargado -- se aplican las reglas normales.
     */
    @Test
    void aprobarTrasUnCambioDeInventarioSoloApruebaConLaVersionNueva() throws IOException {
        Tramite tramite = nuevoTramite(gestoriaA, contactoA, explotacionA1, TipoTramite.ALTA_NACIMIENTO, EstadoTramite.PENDIENTE_REVISION);
        ResponseEntity<String> edicion = patch(tramite, tokenA, Map.of("crotales", List.of("1234")));
        assertThat(edicion.getStatusCode().value()).isEqualTo(200);
        long vista = objectMapper.readTree(edicion.getBody()).get("version").asLong();
        assertThat(versionEnBd(tramite)).isEqualTo(vista);
        assertThat(crotalesEnBd(tramite)).containsExactly("1234|ES980000011234|EN_INVENTARIO");
        // Un reimport mueve X a A2 y mete Y ...1234 en A1.
        jdbcTemplate.update("update animal set explotacion_id = ? where id = ?",
                explotacionA2.getId(), animalA1Unico1234.getId());
        nuevoAnimal(gestoriaA, explotacionA1, "ES980000021234");

        ResponseEntity<String> primero = aprobarConVersion(tramite, tokenA, vista);

        assertThat(primero.getStatusCode().value()).isEqualTo(409);
        assertThat(motivo(primero)).startsWith("La resolución de los crotales ha cambiado desde la revisión");
        assertThat(estadoEnBd(tramite)).isEqualTo("PENDIENTE_REVISION");
        assertThat(crotalesEnBd(tramite)).containsExactly("1234|ES980000021234|EN_INVENTARIO");
        assertThat(versionEnBd(tramite)).as("la re-resolucion confirmada incrementa la version").isEqualTo(vista + 1);
        Map<String, Object> trasElPrimero = foto(tramite);

        ResponseEntity<String> segundo = aprobarConVersion(tramite, tokenA, vista);

        assertThat(segundo.getStatusCode().value()).isEqualTo(409);
        assertThat(motivo(segundo)).isEqualTo(TramiteRevisionService.MOTIVO_VERSION_DESFASADA);
        assertThat(foto(tramite)).isEqualTo(trasElPrimero);

        JsonNode detalle = objectMapper.readTree(get("/tramites/" + tramite.getId(), tokenA).getBody());
        long nueva = detalle.get("version").asLong();
        assertThat(nueva).isEqualTo(vista + 1);
        assertThat(detalle.get("crotales").get(0).get("crotal").asText()).isEqualTo("ES980000021234");

        ResponseEntity<String> tercero = aprobarConVersion(tramite, tokenA, nueva);

        assertThat(tercero.getStatusCode().value()).isEqualTo(200);
        JsonNode aprobado = objectMapper.readTree(tercero.getBody());
        assertThat(aprobado.get("estado").asText()).isEqualTo("APROBADO");
        assertThat(aprobado.get("version").asLong()).isEqualTo(nueva + 1);
        assertThat(estadoEnBd(tramite)).isEqualTo("APROBADO");
        assertThat(versionEnBd(tramite)).isEqualTo(nueva + 1);
    }

    /**
     * Sin version -> 400 { motivo } sin tocar nada. Se comprueba antes de buscar el Tramite, asi
     * que la respuesta es identica para uno propio, uno de otra Gestoria y uno inexistente.
     */
    @Test
    void patchAprobarYRechazarSinVersionDevuelven400ConMotivoYNoCambianNada() throws IOException {
        Tramite tramite = nuevoTramite(gestoriaA, contactoA, explotacionA1, TipoTramite.ALTA_NACIMIENTO, EstadoTramite.PENDIENTE_REVISION);
        Tramite ajeno = nuevoTramite(gestoriaB, contactoB, explotacionB, TipoTramite.ALTA_NACIMIENTO, EstadoTramite.PENDIENTE_REVISION);
        Map<String, Object> antes = foto(tramite);
        Map<String, Object> antesAjeno = foto(ajeno);
        Map<String, Object> versionNula = new HashMap<>();
        versionNula.put("version", null);
        versionNula.put("tipoTramite", "BAJA_MUERTE");

        for (Long id : List.of(tramite.getId(), ajeno.getId(), 999999L)) {
            List<ResponseEntity<String>> respuestas = List.of(
                    patchCrudo(id, tokenA, Map.of("tipoTramite", "BAJA_MUERTE")),
                    patchCrudo(id, tokenA, versionNula),
                    postCrudo("/tramites/" + id + "/aprobar", tokenA, null),
                    postCrudo("/tramites/" + id + "/aprobar", tokenA, "{}"),
                    postCrudo("/tramites/" + id + "/aprobar", tokenA, "{\"version\":null}"),
                    postCrudo("/tramites/" + id + "/rechazar", tokenA, null),
                    postCrudo("/tramites/" + id + "/rechazar", tokenA, "{}"),
                    postCrudo("/tramites/" + id + "/rechazar", tokenA, "{\"version\":null}"));
            for (ResponseEntity<String> respuesta : respuestas) {
                assertThat(respuesta.getStatusCode().value()).as("id " + id).isEqualTo(400);
                assertThat(motivo(respuesta)).isEqualTo(TramiteController.MOTIVO_FALTA_VERSION);
            }
        }
        assertThat(foto(tramite)).isEqualTo(antes);
        assertThat(foto(ajeno)).isEqualTo(antesAjeno);
    }

    /** El 403 de suscripcion sigue siendo lo primero, antes incluso que la version ausente. */
    @Test
    void aprobarSinSuscripcionYSinVersionDevuelve403ConMotivo() throws IOException {
        Tramite tramite = nuevoTramite(gestoriaA, contactoA, explotacionA1, TipoTramite.ALTA_NACIMIENTO, EstadoTramite.PENDIENTE_REVISION);
        suscripcionA.setEstado(EstadoSuscripcion.SUSPENDIDA);
        suscripcionRepository.save(suscripcionA);

        // El motivo es generico (no depende del Tramite): un id ajeno o inexistente da el mismo 403
        // y el mismo texto, asi que no revela nada.
        for (Long id : List.of(tramite.getId(), 999999L)) {
            ResponseEntity<String> respuesta = postCrudo("/tramites/" + id + "/aprobar", tokenA, null);
            assertThat(respuesta.getStatusCode().value()).isEqualTo(403);
            assertThat(motivo(respuesta)).isEqualTo(TramiteController.MOTIVO_SUSCRIPCION_NO_PERMITE_APROBAR);
        }
        assertThat(estadoEnBd(tramite)).isEqualTo("PENDIENTE_REVISION");
    }

    @Test
    void patchYAprobarConVersionDesfasadaDevuelven409SinCambiarNada() throws IOException {
        Tramite tramite = nuevoTramite(gestoriaA, contactoA, explotacionA1, TipoTramite.ALTA_NACIMIENTO, EstadoTramite.PENDIENTE_REVISION);
        tramiteCrotalService.reemplazarCrotales(tramite, List.of("1234"), gestoriaA.getId());
        long vieja = versionEnBd(tramite);
        assertThat(patch(tramite, tokenA, Map.of("version", vieja, "tipoTramite", "BAJA_MUERTE")).getStatusCode().value())
                .isEqualTo(200);
        assertThat(versionEnBd(tramite)).isEqualTo(vieja + 1);
        Map<String, Object> antes = foto(tramite);

        for (long otra : List.of(vieja, vieja + 5)) {
            ResponseEntity<String> edicion = patch(tramite, tokenA, Map.of("version", otra, "tipoTramite", "ALTA_NACIMIENTO",
                    "explotacionId", explotacionA2.getId(), "crotales", List.of("5678")));
            ResponseEntity<String> aprobacion = aprobarConVersion(tramite, tokenA, otra);

            for (ResponseEntity<String> respuesta : List.of(edicion, aprobacion)) {
                assertThat(respuesta.getStatusCode().value()).as("version " + otra).isEqualTo(409);
                assertThat(motivo(respuesta)).isEqualTo(TramiteRevisionService.MOTIVO_VERSION_DESFASADA);
            }
            assertThat(foto(tramite)).isEqualTo(antes);
        }
    }

    /** Mini-prompt tras A2 (punto 4): rechazar con una version que no es la actual -> 409 con el
     * mismo motivo que PATCH/aprobar, y nada cambia (estado ni version). */
    @Test
    void rechazarConVersionDesfasadaDevuelve409SinCambiarNada() throws IOException {
        Tramite tramite = nuevoTramite(gestoriaA, contactoA, null, null, EstadoTramite.PENDIENTE_REVISION);
        long vieja = versionEnBd(tramite);
        assertThat(patch(tramite, tokenA, Map.of("version", vieja, "tipoTramite", "BAJA_MUERTE")).getStatusCode().value())
                .isEqualTo(200);
        Map<String, Object> antes = foto(tramite);

        for (long otra : List.of(vieja, vieja + 5)) {
            ResponseEntity<String> respuesta = rechazarConVersion(tramite, tokenA, otra);

            assertThat(respuesta.getStatusCode().value()).as("version " + otra).isEqualTo(409);
            assertThat(motivo(respuesta)).isEqualTo(TramiteRevisionService.MOTIVO_VERSION_DESFASADA);
            assertThat(foto(tramite)).isEqualTo(antes);
        }
        assertThat(estadoEnBd(tramite)).isEqualTo("PENDIENTE_REVISION");
    }

    /** Toda escritura aceptada incrementa la version exactamente en 1, tambien un PATCH que solo
     * cambia los crotales (solo toca tramite_crotal: el incremento se fuerza). */
    @Test
    void unPatchCorrectoIncrementaLaVersionAunqueSoloCambienLosCrotales() throws IOException {
        Tramite tramite = nuevoTramite(gestoriaA, contactoA, explotacionA1, null, EstadoTramite.PENDIENTE_REVISION);
        long v0 = versionEnBd(tramite);

        ResponseEntity<String> soloCrotales = patch(tramite, tokenA, Map.of("version", v0, "crotales", List.of("1234")));
        assertThat(soloCrotales.getStatusCode().value()).isEqualTo(200);
        assertThat(objectMapper.readTree(soloCrotales.getBody()).get("version").asLong()).isEqualTo(v0 + 1);
        assertThat(versionEnBd(tramite)).isEqualTo(v0 + 1);

        ResponseEntity<String> soloTipo = patch(tramite, tokenA, Map.of("version", v0 + 1, "tipoTramite", "BAJA_MUERTE"));
        assertThat(soloTipo.getStatusCode().value()).isEqualTo(200);
        assertThat(objectMapper.readTree(soloTipo.getBody()).get("version").asLong()).isEqualTo(v0 + 2);
        assertThat(versionEnBd(tramite)).isEqualTo(v0 + 2);
        assertThat(foto(tramite).get("tipo_tramite")).isEqualTo("BAJA_MUERTE");
    }

    @Test
    void rechazarConLaVersionActualDevuelve200YLaIncrementaEnUno() throws IOException {
        Tramite tramite = nuevoTramite(gestoriaA, contactoA, null, null, EstadoTramite.PENDIENTE_REVISION);
        long v0 = versionEnBd(tramite);

        ResponseEntity<String> respuesta = rechazar(tramite, tokenA);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(objectMapper.readTree(respuesta.getBody()).get("version").asLong()).isEqualTo(v0 + 1);
        assertThat(versionEnBd(tramite)).isEqualTo(v0 + 1);
        assertThat(estadoEnBd(tramite)).isEqualTo("RECHAZADO");
    }

    /** Un Tramite de otra Gestoria es 404 sin cuerpo con CUALQUIER version, incluida la correcta. */
    @Test
    void unTramiteDeOtraGestoriaEs404SeaCualSeaLaVersion() {
        Tramite tramite = nuevoTramite(gestoriaA, contactoA, explotacionA1, TipoTramite.ALTA_NACIMIENTO, EstadoTramite.PENDIENTE_REVISION);
        long actual = versionEnBd(tramite);
        Map<String, Object> antes = foto(tramite);

        for (long version : List.of(actual, actual + 1, actual + 100)) {
            ResponseEntity<String> edicion = patch(tramite, tokenB, Map.of("version", version, "tipoTramite", "BAJA_MUERTE"));
            ResponseEntity<String> aprobacion = aprobarConVersion(tramite, tokenB, version);
            ResponseEntity<String> rechazo = rechazarConVersion(tramite, tokenB, version);

            for (ResponseEntity<String> respuesta : List.of(edicion, aprobacion, rechazo)) {
                assertThat(respuesta.getStatusCode().value()).as("version " + version).isEqualTo(404);
                assertThat(respuesta.getBody()).isNullOrEmpty();
            }
        }
        assertThat(foto(tramite)).isEqualTo(antes);
    }

    // ------------------------- listado con REGA/nombre y crotales con completo (mini-prompt, 5 y 8)

    /**
     * Punto 5: cada fila del listado lleva el codigo REGA y el nombre de su Explotacion (null si no
     * tiene), sin cargar la Explotacion fila a fila (EntityGraph en la consulta de la pagina), y
     * Gestoria A nunca ve nada de B.
     */
    @Test
    void listadoIncluyeRegaYNombreDeLaExplotacionSinCargarlaPorFilaNiMezclarGestorias() throws IOException {
        Explotacion explotacionA3 = nuevaExplotacion(gestoriaA, "ES980000000003");
        Tramite enA1 = nuevoTramite(gestoriaA, contactoA, explotacionA1, TipoTramite.ALTA_NACIMIENTO, EstadoTramite.PENDIENTE_REVISION);
        Tramite enA2 = nuevoTramite(gestoriaA, contactoA, explotacionA2, null, EstadoTramite.PENDIENTE_REVISION);
        Tramite enA3 = nuevoTramite(gestoriaA, contactoA, explotacionA3, null, EstadoTramite.APROBADO);
        Tramite sinExplotacion = nuevoTramite(gestoriaA, contactoA, null, null, EstadoTramite.PENDIENTE_REVISION);
        Tramite deB = nuevoTramite(gestoriaB, contactoB, explotacionB, null, EstadoTramite.PENDIENTE_REVISION);

        for (String ruta : List.of("/tramites?size=20", "/tramites?size=2&page=1",
                "/tramites?estado=PENDIENTE_REVISION&size=20", "/tramites?estado=PENDIENTE_REVISION&size=1&page=1")) {
            SqlCapturadoInspector.limpiar();
            ResponseEntity<String> respuesta = get(ruta, tokenA);
            assertThat(respuesta.getStatusCode().value()).as(ruta).isEqualTo(200);
            assertThat(consultasDeTramiteConJoinAExplotacion())
                    .as(ruta + ": la consulta de la pagina trae la Explotacion con un join (si Hibernate cambiara "
                            + "el formato del SQL, el contador de cargas sueltas podria quedarse en 0 sin medir nada)")
                    .isPositive();
            assertThat(cargasSueltasDeExplotacion()).as(ruta + ": ninguna carga de Explotacion por fila").isZero();
            assertThat(respuesta.getBody()).as(ruta).doesNotContain("ES980000000101");
        }

        JsonNode todo = objectMapper.readTree(get("/tramites?size=20", tokenA).getBody());
        assertThat(todo.get("totalElements").asLong()).isEqualTo(4);
        Map<Long, JsonNode> porId = new HashMap<>();
        todo.get("content").forEach(t -> porId.put(t.get("id").asLong(), t));
        assertThat(porId).doesNotContainKey(deB.getId());
        assertThat(porId.get(enA1.getId()).get("explotacionCodigoRega").asText()).isEqualTo("ES980000000001");
        assertThat(porId.get(enA1.getId()).get("explotacionNombre").asText()).isEqualTo("Finca ES980000000001");
        assertThat(porId.get(enA2.getId()).get("explotacionCodigoRega").asText()).isEqualTo("ES980000000002");
        assertThat(porId.get(enA3.getId()).get("explotacionNombre").asText()).isEqualTo("Finca ES980000000003");
        assertThat(porId.get(sinExplotacion.getId()).get("explotacionCodigoRega").isNull()).isTrue();
        assertThat(porId.get(sinExplotacion.getId()).get("explotacionNombre").isNull()).isTrue();

        // Paginacion con conteo: la consulta de conteo sigue funcionando con el EntityGraph.
        JsonNode pagina = objectMapper.readTree(get("/tramites?size=2&page=1", tokenA).getBody());
        assertThat(pagina.get("totalElements").asLong()).isEqualTo(4);
        assertThat(pagina.get("content")).hasSize(2);
        JsonNode pendientes = objectMapper.readTree(
                get("/tramites?estado=PENDIENTE_REVISION&size=1&page=1", tokenA).getBody());
        assertThat(pendientes.get("totalElements").asLong()).isEqualTo(3);

        JsonNode vistoPorB = objectMapper.readTree(get("/tramites?size=20", tokenB).getBody());
        assertThat(vistoPorB.get("totalElements").asLong()).isEqualTo(1);
        assertThat(vistoPorB.get("content").get(0).get("explotacionCodigoRega").asText()).isEqualTo("ES980000000101");
        assertThat(vistoPorB.toString()).doesNotContain("ES980000000001").doesNotContain("ES980000000002")
                .doesNotContain("ES980000000003");
    }

    /** Punto 5, efecto colateral: aprobar y rechazar devuelven TramiteResponse, con REGA y nombre. */
    @Test
    void aprobarYRechazarDevuelvenRegaYNombreDeLaExplotacion() throws IOException {
        Tramite aprobable = nuevoTramite(gestoriaA, contactoA, explotacionA1, TipoTramite.ALTA_NACIMIENTO, EstadoTramite.PENDIENTE_REVISION);
        Tramite rechazable = nuevoTramite(gestoriaA, contactoA, explotacionA2, null, EstadoTramite.PENDIENTE_REVISION);
        Tramite sinExplotacion = nuevoTramite(gestoriaA, contactoA, null, null, EstadoTramite.PENDIENTE_REVISION);

        JsonNode aprobado = objectMapper.readTree(aprobar(aprobable, tokenA).getBody());
        assertThat(aprobado.get("estado").asText()).isEqualTo("APROBADO");
        assertThat(aprobado.get("explotacionCodigoRega").asText()).isEqualTo("ES980000000001");
        assertThat(aprobado.get("explotacionNombre").asText()).isEqualTo("Finca ES980000000001");

        JsonNode rechazado = objectMapper.readTree(rechazar(rechazable, tokenA).getBody());
        assertThat(rechazado.get("estado").asText()).isEqualTo("RECHAZADO");
        assertThat(rechazado.get("explotacionCodigoRega").asText()).isEqualTo("ES980000000002");
        assertThat(rechazado.get("explotacionNombre").asText()).isEqualTo("Finca ES980000000002");

        JsonNode rechazadoSin = objectMapper.readTree(rechazar(sinExplotacion, tokenA).getBody());
        assertThat(rechazadoSin.has("explotacionCodigoRega")).isTrue();
        assertThat(rechazadoSin.get("explotacionCodigoRega").isNull()).isTrue();
        assertThat(rechazadoSin.get("explotacionNombre").isNull()).isTrue();
    }

    /**
     * Punto 8: completo esta en el PATCH, el detalle y el listado, y describe lo ESCRITO: un sufijo
     * resuelto EN_INVENTARIO sigue siendo completo=false.
     */
    @Test
    void completoApareceEnPatchDetalleYListadoYDescribeLoEscrito() throws IOException {
        Tramite tramite = nuevoTramite(gestoriaA, contactoA, explotacionA1, null, EstadoTramite.PENDIENTE_REVISION);

        ResponseEntity<String> edicion = patch(tramite, tokenA,
                Map.of("crotales", List.of("ES-9800-0001-5678", "1234", "9999")));
        assertThat(edicion.getStatusCode().value()).isEqualTo(200);

        JsonNode enPatch = objectMapper.readTree(edicion.getBody()).get("crotales");
        JsonNode enDetalle = objectMapper.readTree(get("/tramites/" + tramite.getId(), tokenA).getBody()).get("crotales");
        JsonNode enListado = objectMapper.readTree(get("/tramites", tokenA).getBody())
                .get("content").get(0).get("crotales");
        for (JsonNode crotales : List.of(enPatch, enDetalle, enListado)) {
            assertThat(crotales).hasSize(3);
            assertThat(crotales.get(0).get("completo").asBoolean()).isTrue();
            assertThat(crotales.get(1).get("crotal").asText()).isEqualTo("ES980000011234");
            assertThat(crotales.get(1).get("resolucion").asText()).isEqualTo("EN_INVENTARIO");
            assertThat(crotales.get(1).get("completo").isBoolean()).isTrue();
            assertThat(crotales.get(1).get("completo").asBoolean()).isFalse();
            assertThat(crotales.get(2).get("resolucion").asText()).isEqualTo("NO_ENCONTRADO");
            assertThat(crotales.get(2).get("completo").isBoolean()).isTrue();
            assertThat(crotales.get(2).get("completo").asBoolean()).isFalse();
        }
    }

    /** Selects sueltos sobre explotacion (la carga perezosa de un proxy, una por fila). La
     * consulta de la pagina hace "left join explotacion", que no cuenta. */
    private long cargasSueltasDeExplotacion() {
        return SqlCapturadoInspector.capturado().stream()
                .map(sql -> sql.toLowerCase(java.util.Locale.ROOT))
                .filter(sql -> sql.contains("from explotacion "))
                .count();
    }

    /** Consultas sobre tramite que traen la Explotacion en la misma sentencia ("join explotacion "):
     * contrapartida positiva de {@link #cargasSueltasDeExplotacion()}. */
    private long consultasDeTramiteConJoinAExplotacion() {
        return SqlCapturadoInspector.capturado().stream()
                .map(sql -> sql.toLowerCase(java.util.Locale.ROOT))
                .filter(sql -> sql.contains("from tramite ") && sql.contains("join explotacion "))
                .count();
    }

    private List<Long> idsDelListado(String ruta, String token) throws IOException {
        List<Long> ids = new java.util.ArrayList<>();
        objectMapper.readTree(get(ruta, token).getBody()).get("content").forEach(t -> ids.add(t.get("id").asLong()));
        return ids;
    }

    // ------------------------------------------------------------- utilidades

    private long seleccionConBloqueoDeTramite() {
        return SqlCapturadoInspector.capturado().stream()
                .map(sql -> sql.toLowerCase(java.util.Locale.ROOT))
                .filter(sql -> sql.contains("from tramite ") && sql.contains("for update"))
                .count();
    }

    /**
     * Revision 7a I1 (a), por HTTP real: un Animal del inventario con crotal sin "ES"
     * (010000001234, sembrado como lo dejaria un Excel sin prefijo) resuelve "1234" EN_INVENTARIO,
     * pero aprobar da 409 nombrandolo y NADA cambia en BD (estado, version, crotales).
     */
    @Test
    void aprobarUnCrotalDeInventarioConFormatoIncompletoDevuelve409YNoCambiaNada() throws IOException {
        Explotacion sinPrefijo = nuevaExplotacion(gestoriaA, "ES980000000003");
        nuevoAnimal(gestoriaA, sinPrefijo, "010000001234");
        Tramite tramite = nuevoTramite(gestoriaA, contactoA, sinPrefijo, TipoTramite.BAJA_MUERTE, EstadoTramite.PENDIENTE_REVISION);
        ResponseEntity<String> edicion = patch(tramite, tokenA, Map.of("crotales", List.of("1234")));
        assertThat(edicion.getStatusCode().value()).isEqualTo(200);
        JsonNode crotal = objectMapper.readTree(edicion.getBody()).get("crotales").get(0);
        assertThat(crotal.get("resolucion").asText()).isEqualTo("EN_INVENTARIO");
        assertThat(crotal.get("crotal").asText()).isEqualTo("010000001234");
        Map<String, Object> antes = foto(tramite);

        ResponseEntity<String> respuesta = aprobar(tramite, tokenA);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(409);
        assertThat(motivo(respuesta)).isEqualTo("El crotal 1234 está en el inventario como 010000001234, que no es "
                + "un crotal completo válido. Vuelve a importar el inventario con el crotal completo.");
        assertThat(foto(tramite)).isEqualTo(antes);
        assertThat(foto(tramite).get("estado")).isEqualTo("PENDIENTE_REVISION");
        assertThat(crotalesEnBd(tramite)).containsExactly("1234|010000001234|EN_INVENTARIO");
    }

    /** Fila del Tramite (incluida su version) + sus crotales, leida directamente de la BD. */
    private Map<String, Object> foto(Tramite tramite) {
        // Claves en minusculas: H2 devuelve los nombres de columna en mayusculas.
        Map<String, Object> foto = new HashMap<>();
        jdbcTemplate.queryForMap("select estado, explotacion_id, tipo_tramite, version from tramite where id = ?",
                        tramite.getId())
                .forEach((columna, valor) -> foto.put(columna.toLowerCase(java.util.Locale.ROOT), valor));
        assertThat(foto).containsOnlyKeys("estado", "explotacion_id", "tipo_tramite", "version");
        foto.put("crotales", crotalesEnBd(tramite));
        return foto;
    }

    private String estadoEnBd(Tramite tramite) {
        return jdbcTemplate.queryForObject("select estado from tramite where id = ?", String.class, tramite.getId());
    }

    private List<String> crotalesEnBd(Tramite tramite) {
        return jdbcTemplate.queryForList(
                "select crotal_indicado || '|' || crotal || '|' || resolucion from tramite_crotal "
                        + "where tramite_id = ? order by id", String.class, tramite.getId());
    }

    private String motivo(ResponseEntity<String> respuesta) throws IOException {
        return objectMapper.readTree(respuesta.getBody()).get("motivo").asText();
    }

    /**
     * PATCH como lo haria una pantalla recien cargada: si el cuerpo no trae "version", se anade la
     * version ACTUAL de la BD (decision 27). Para probar una version concreta, se pone en el cuerpo.
     */
    private ResponseEntity<String> patch(Tramite tramite, String token, Map<String, Object> cuerpo) {
        Map<String, Object> conVersion = new HashMap<>(cuerpo);
        conVersion.putIfAbsent("version", versionEnBd(tramite));
        return patchCrudo(tramite.getId(), token, conVersion);
    }

    /** PATCH con el cuerpo tal cual (sin anadir version). */
    private ResponseEntity<String> patchCrudo(Long id, String token, Map<String, Object> cuerpo) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange("/tramites/" + id, HttpMethod.PATCH, new HttpEntity<>(cuerpo, headers), String.class);
    }

    /** Aprobar con la version ACTUAL de la BD (pantalla recien cargada). */
    private ResponseEntity<String> aprobar(Tramite tramite, String token) {
        return aprobarConVersion(tramite, token, versionEnBd(tramite));
    }

    private ResponseEntity<String> aprobarConVersion(Tramite tramite, String token, long version) {
        return postCrudo("/tramites/" + tramite.getId() + "/aprobar", token, "{\"version\":" + version + "}");
    }

    /** Rechazar con la version ACTUAL de la BD (pantalla recien cargada). */
    private ResponseEntity<String> rechazar(Tramite tramite, String token) {
        return rechazarConVersion(tramite, token, versionEnBd(tramite));
    }

    private ResponseEntity<String> rechazarConVersion(Tramite tramite, String token, long version) {
        return postCrudo("/tramites/" + tramite.getId() + "/rechazar", token, "{\"version\":" + version + "}");
    }

    /** POST con un cuerpo JSON literal (o sin cuerpo si es null). */
    private ResponseEntity<String> postCrudo(String path, String token, String cuerpoJson) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        if (cuerpoJson != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        return restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(cuerpoJson, headers), String.class);
    }

    private long versionEnBd(Tramite tramite) {
        return jdbcTemplate.queryForObject("select version from tramite where id = ?", Long.class, tramite.getId());
    }

    private ResponseEntity<String> get(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private Suscripcion suscripcion(Gestoria gestoria, EstadoSuscripcion estado) {
        Suscripcion suscripcion = new Suscripcion();
        suscripcion.setGestoria(gestoria);
        suscripcion.setEstado(estado);
        return suscripcionRepository.save(suscripcion);
    }

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
        contacto.setNombre("Contacto revision E2E");
        contacto.setGestoria(gestoria);
        return contactoRepository.save(contacto);
    }

    private Tramite nuevoTramite(Gestoria gestoria, Contacto contacto, Explotacion explotacion,
                                 TipoTramite tipo, EstadoTramite estado) {
        Tramite tramite = new Tramite();
        tramite.setGestoria(gestoria);
        tramite.setContacto(contacto);
        tramite.setExplotacion(explotacion);
        tramite.setTipoTramite(tipo);
        tramite.setEstado(estado);
        return tramiteRepository.save(tramite);
    }

    private void crearUsuario(Gestoria gestoria, String email) {
        Usuario usuario = new Usuario();
        usuario.setGestoria(gestoria);
        usuario.setEmail(email);
        usuario.setPasswordHash(passwordEncoder.encode("password123"));
        usuario.setNombre("Usuario revision E2E");
        usuario.setActivo(true);
        usuarioRepository.save(usuario);
    }

    private String login(String email) {
        ResponseEntity<LoginResponse> respuesta = restTemplate.postForEntity(
                "/auth/login", new LoginRequest(email, "password123"), LoginResponse.class);
        return respuesta.getBody().token();
    }
}
