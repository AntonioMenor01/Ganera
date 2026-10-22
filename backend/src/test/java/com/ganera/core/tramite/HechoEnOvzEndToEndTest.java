package com.ganera.core.tramite;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.ganera.core.whatsapp.MensajeCampo;
import com.ganera.core.whatsapp.MensajeCampoRepository;
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
import com.ganera.core.shared.auditoria.LimpiezaAuditoria;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E real (servidor embebido + TestRestTemplate) de «hecho en OVZ» (plan 2026-10-07):
 * POST /tramites/{id}/marcar-hecho-ovz y /desmarcar-hecho-ovz, con DOS Gestorias. El estado "sin
 * cambios" se lee de la BD con JdbcTemplate, fuera de cualquier transaccion del servidor (rollback
 * real). SqlCapturadoInspector (misma propiedad que TramiteRevisionEndToEndTest, asi comparten
 * contexto) cuenta el SQL del listado para vigilar el N+1 del usuario que marco.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "com.ganera.core.tramite.SqlCapturadoInspector")
class HechoEnOvzEndToEndTest {

    private static final String MOTIVO_MARCAR = "Solo se puede marcar como hecho en OVZ un trámite aprobado.";
    private static final String MOTIVO_YA_MARCADO = "Este trámite ya está marcado como hecho en OVZ.";
    private static final String MOTIVO_DESMARCAR =
            "Solo se puede quitar la marca de un trámite marcado como hecho en OVZ.";
    private static final String MOTIVO_EDITAR =
            "Este trámite está marcado como hecho en OVZ. Quita la marca para editarlo.";
    private static final String MOTIVO_DESFASADA =
            "El trámite ha cambiado desde que lo abriste. Vuelve a cargarlo y revísalo antes de continuar.";

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private TramiteCrotalRepository tramiteCrotalRepository;
    @Autowired
    private TramiteRepository tramiteRepository;
    @Autowired
    private MensajeCampoRepository mensajeCampoRepository;
    @Autowired
    private SuscripcionRepository suscripcionRepository;
    @Autowired
    private ContactoRepository contactoRepository;
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
    private Usuario anaA;
    private Usuario brunoA;
    private Usuario carlaB;
    private String tokenAna;
    private String tokenBruno;
    private String tokenCarla;
    private Suscripcion suscripcionA;
    private Explotacion explotacionA;
    private Explotacion explotacionB;
    private Contacto contactoA;
    private Contacto contactoB;

    @BeforeEach
    void preparar() {
        gestoriaA = gestoriaRepository.save(new Gestoria("Gestoria hecho OVZ A"));
        gestoriaB = gestoriaRepository.save(new Gestoria("Gestoria hecho OVZ B"));
        anaA = crearUsuario(gestoriaA, "ana.hecho@test.com", "Ana Pérez");
        brunoA = crearUsuario(gestoriaA, "bruno.hecho@test.com", "Bruno Gil");
        carlaB = crearUsuario(gestoriaB, "carla.hecho@test.com", "Carla Ruiz");
        tokenAna = login("ana.hecho@test.com");
        tokenBruno = login("bruno.hecho@test.com");
        tokenCarla = login("carla.hecho@test.com");
        suscripcionA = suscripcion(gestoriaA, EstadoSuscripcion.ACTIVA);
        suscripcion(gestoriaB, EstadoSuscripcion.ACTIVA);
        explotacionA = nuevaExplotacion(gestoriaA, "ES990000000001");
        explotacionB = nuevaExplotacion(gestoriaB, "ES990000000101");
        contactoA = nuevoContacto(gestoriaA, "+34600990001");
        contactoB = nuevoContacto(gestoriaB, "+34600990002");
    }

    @AfterEach
    void limpiar() {
        LimpiezaAuditoria.borrar(jdbcTemplate);
        mensajeCampoRepository.deleteAll();
        tramiteCrotalRepository.deleteAll();
        tramiteRepository.deleteAll();
        suscripcionRepository.deleteAll();
        contactoRepository.deleteAll();
        explotacionRepository.deleteAll();
        ganaderoRepository.deleteAll();
        usuarioRepository.deleteAll();
        gestoriaRepository.deleteAll();
    }

    // ------------------------------------------------------------- marcar y desmarcar

    @Test
    void marcarYDesmarcarDevuelven200ConVersionMasUnoQuienYCuando() throws IOException {
        Tramite tramite = aprobado(gestoriaA, contactoA, explotacionA);
        long v0 = versionEnBd(tramite);
        Instant antes = Instant.now();

        ResponseEntity<String> marcar = marcar(tramite, tokenAna, v0);
        Instant despues = Instant.now();

        assertThat(marcar.getStatusCode().value()).isEqualTo(200);
        JsonNode marcado = objectMapper.readTree(marcar.getBody());
        assertThat(marcado.get("estado").asText()).isEqualTo("HECHO_EN_OVZ");
        assertThat(marcado.get("version").asLong()).isEqualTo(v0 + 1);
        assertThat(marcado.get("hechoEnOvzPorNombre").asText()).isEqualTo("Ana Pérez");
        String cuando = marcado.get("hechoEnOvzEn").asText();
        // ISO-8601 en UTC con "Z" (nunca un numero ni un desfase +02:00).
        assertThat(cuando).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z");
        assertThat(Instant.parse(cuando)).isBetween(antes.minusSeconds(1), despues.plusSeconds(1));
        // Nunca el email ni el id del usuario.
        assertThat(marcar.getBody()).doesNotContain("ana.hecho@test.com").doesNotContain("hechoEnOvzPorId");
        assertThat(fila(tramite)).containsEntry("estado", "HECHO_EN_OVZ")
                .containsEntry("hecho_en_ovz_por", anaA.getId())
                .containsEntry("version", v0 + 1);
        assertThat(fila(tramite).get("hecho_en_ovz_en")).isNotNull();

        ResponseEntity<String> desmarcar = desmarcar(tramite, tokenBruno, v0 + 1);

        assertThat(desmarcar.getStatusCode().value()).isEqualTo(200);
        JsonNode desmarcado = objectMapper.readTree(desmarcar.getBody());
        assertThat(desmarcado.get("estado").asText()).isEqualTo("APROBADO");
        assertThat(desmarcado.get("version").asLong()).isEqualTo(v0 + 2);
        assertThat(desmarcado.get("hechoEnOvzEn").isNull()).isTrue();
        assertThat(desmarcado.get("hechoEnOvzPorNombre").isNull()).isTrue();
        Map<String, Object> fila = fila(tramite);
        assertThat(fila).containsEntry("estado", "APROBADO").containsEntry("version", v0 + 2);
        assertThat(fila.get("hecho_en_ovz_por")).isNull();
        assertThat(fila.get("hecho_en_ovz_en")).isNull();
    }

    /** Un campo de mas en el cuerpo nunca cambia quien consta: siempre el usuario del JWT. Con dos
     * usuarios de la misma gestoria, el que marca es el que consta. */
    @Test
    void quienConstaEsSiempreElUsuarioAutenticadoNuncaElDelCuerpo() throws IOException {
        Tramite tramite = aprobado(gestoriaA, contactoA, explotacionA);
        long v0 = versionEnBd(tramite);
        String cuerpo = "{\"version\":" + v0 + ",\"hechoEnOvzPor\":" + carlaB.getId()
                + ",\"usuarioId\":" + brunoA.getId() + ",\"hechoEnOvzPorNombre\":\"Otra Persona\""
                + ",\"hechoEnOvzEn\":\"2020-01-01T00:00:00Z\"}";

        ResponseEntity<String> marcar = postCrudo("/tramites/" + tramite.getId() + "/marcar-hecho-ovz", tokenAna, cuerpo);

        assertThat(marcar.getStatusCode().value()).isEqualTo(200);
        JsonNode marcado = objectMapper.readTree(marcar.getBody());
        assertThat(marcado.get("hechoEnOvzPorNombre").asText()).isEqualTo("Ana Pérez");
        assertThat(marcado.get("hechoEnOvzEn").asText()).doesNotStartWith("2020");
        assertThat(fila(tramite)).containsEntry("hecho_en_ovz_por", anaA.getId());

        Tramite otro = aprobado(gestoriaA, contactoA, explotacionA);
        ResponseEntity<String> porBruno = marcar(otro, tokenBruno, versionEnBd(otro));
        assertThat(objectMapper.readTree(porBruno.getBody()).get("hechoEnOvzPorNombre").asText()).isEqualTo("Bruno Gil");
        assertThat(fila(otro)).containsEntry("hecho_en_ovz_por", brunoA.getId());
    }

    // ------------------------------------------------------------- 404, 400, 409

    @Test
    void unTramiteDeOtraGestoriaOInexistenteEs404SinCuerpoYNoCambiaNada() {
        Tramite aprobadoB = aprobado(gestoriaB, contactoB, explotacionB);
        Tramite hechoB = hechoEnOvz(gestoriaB, contactoB, explotacionB, carlaB);
        Map<String, Object> fotoAprobado = fila(aprobadoB);
        Map<String, Object> fotoHecho = fila(hechoB);

        List<ResponseEntity<String>> respuestas = List.of(
                marcar(aprobadoB, tokenAna, versionEnBd(aprobadoB)),
                desmarcar(hechoB, tokenAna, versionEnBd(hechoB)),
                // El endpoint "contrario" sobre el tramite ajeno tambien es 404 (no un 409 que revele su estado).
                desmarcar(aprobadoB, tokenAna, versionEnBd(aprobadoB)),
                marcar(hechoB, tokenAna, versionEnBd(hechoB)),
                postCrudo("/tramites/999999999/marcar-hecho-ovz", tokenAna, "{\"version\":0}"),
                postCrudo("/tramites/999999999/desmarcar-hecho-ovz", tokenAna, "{\"version\":0}"));

        for (ResponseEntity<String> respuesta : respuestas) {
            assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
            assertThat(respuesta.getBody()).isNullOrEmpty();
        }
        assertThat(fila(aprobadoB)).isEqualTo(fotoAprobado);
        assertThat(fila(hechoB)).isEqualTo(fotoHecho);
    }

    /** 400 sin version ANTES de buscar el Tramite: el mismo cuerpo para uno propio, uno ajeno y uno
     * inexistente, en los dos endpoints, y nada cambia. */
    @Test
    void sinVersionEs400ConElMismoMotivoParaPropioAjenoEInexistente() throws IOException {
        Tramite propio = aprobado(gestoriaA, contactoA, explotacionA);
        Tramite ajeno = aprobado(gestoriaB, contactoB, explotacionB);
        Map<String, Object> fotoPropio = fila(propio);
        Map<String, Object> fotoAjeno = fila(ajeno);

        List<String> cuerpos = new ArrayList<>();
        for (Long id : List.of(propio.getId(), ajeno.getId(), 999999999L)) {
            for (String accion : List.of("marcar-hecho-ovz", "desmarcar-hecho-ovz")) {
                for (String cuerpo : java.util.Arrays.asList(null, "{}", "{\"version\":null}")) {
                    ResponseEntity<String> respuesta = postCrudo("/tramites/" + id + "/" + accion, tokenAna, cuerpo);
                    assertThat(respuesta.getStatusCode().value()).as(accion + " " + id + " " + cuerpo).isEqualTo(400);
                    cuerpos.add(respuesta.getBody());
                }
            }
        }
        assertThat(cuerpos).containsOnly(cuerpos.get(0));
        assertThat(objectMapper.readTree(cuerpos.get(0)).get("motivo").asText())
                .isEqualTo(TramiteController.MOTIVO_FALTA_VERSION);
        assertThat(fila(propio)).isEqualTo(fotoPropio);
        assertThat(fila(ajeno)).isEqualTo(fotoAjeno);
    }

    @Test
    void marcarDesdeCadaEstadoIncorrectoEs409ConSuMotivoYNoCambiaNada() throws IOException {
        for (EstadoTramite estado : List.of(EstadoTramite.PENDIENTE_EXTRACCION, EstadoTramite.PENDIENTE_REVISION,
                EstadoTramite.RECHAZADO, EstadoTramite.EN_PROCESO, EstadoTramite.EJECUTADO_OVZ,
                EstadoTramite.ERROR_OVZ, EstadoTramite.HECHO_EN_OVZ)) {
            Tramite tramite = estado == EstadoTramite.HECHO_EN_OVZ
                    ? hechoEnOvz(gestoriaA, contactoA, explotacionA, brunoA)
                    : nuevoTramite(gestoriaA, contactoA, explotacionA, estado);
            Map<String, Object> foto = fila(tramite);

            ResponseEntity<String> respuesta = marcar(tramite, tokenAna, versionEnBd(tramite));

            assertThat(respuesta.getStatusCode().value()).as("marcar desde " + estado).isEqualTo(409);
            assertThat(motivo(respuesta))
                    .isEqualTo(estado == EstadoTramite.HECHO_EN_OVZ ? MOTIVO_YA_MARCADO : MOTIVO_MARCAR);
            assertThat(fila(tramite)).isEqualTo(foto);
        }
    }

    @Test
    void desmarcarDesdeCadaEstadoIncorrectoEs409ConSuMotivoYNoCambiaNada() throws IOException {
        for (EstadoTramite estado : List.of(EstadoTramite.PENDIENTE_EXTRACCION, EstadoTramite.PENDIENTE_REVISION,
                EstadoTramite.APROBADO, EstadoTramite.RECHAZADO, EstadoTramite.EN_PROCESO,
                EstadoTramite.EJECUTADO_OVZ, EstadoTramite.ERROR_OVZ)) {
            Tramite tramite = nuevoTramite(gestoriaA, contactoA, explotacionA, estado);
            Map<String, Object> foto = fila(tramite);

            ResponseEntity<String> respuesta = desmarcar(tramite, tokenAna, versionEnBd(tramite));

            assertThat(respuesta.getStatusCode().value()).as("desmarcar desde " + estado).isEqualTo(409);
            assertThat(motivo(respuesta)).isEqualTo(MOTIVO_DESMARCAR);
            assertThat(fila(tramite)).isEqualTo(foto);
        }
    }

    @Test
    void conVersionDesfasadaEs409YNoCambiaNada() throws IOException {
        Tramite aprobado = aprobado(gestoriaA, contactoA, explotacionA);
        Tramite hecho = hechoEnOvz(gestoriaA, contactoA, explotacionA, brunoA);
        Map<String, Object> fotoAprobado = fila(aprobado);
        Map<String, Object> fotoHecho = fila(hecho);

        for (long desfase : List.of(-1L, 1L)) {
            ResponseEntity<String> marcar = marcar(aprobado, tokenAna, versionEnBd(aprobado) + desfase);
            ResponseEntity<String> desmarcar = desmarcar(hecho, tokenAna, versionEnBd(hecho) + desfase);

            assertThat(marcar.getStatusCode().value()).isEqualTo(409);
            assertThat(motivo(marcar)).isEqualTo(MOTIVO_DESFASADA);
            assertThat(desmarcar.getStatusCode().value()).isEqualTo(409);
            assertThat(motivo(desmarcar)).isEqualTo(MOTIVO_DESFASADA);
        }
        assertThat(fila(aprobado)).isEqualTo(fotoAprobado);
        assertThat(fila(hecho)).isEqualTo(fotoHecho);
    }

    /** Sin comprobacion de suscripcion (A1 D30): una gestoria SUSPENDIDA marca y desmarca, y sigue sin
     * poder aprobar. */
    @Test
    void unaGestoriaSuspendidaMarcaYDesmarcaPeroNoApruebaNada() throws IOException {
        suscripcionA.setEstado(EstadoSuscripcion.SUSPENDIDA);
        suscripcionRepository.save(suscripcionA);
        Tramite tramite = aprobado(gestoriaA, contactoA, explotacionA);
        Tramite pendiente = nuevoTramite(gestoriaA, contactoA, explotacionA, EstadoTramite.PENDIENTE_REVISION);

        ResponseEntity<String> marcar = marcar(tramite, tokenAna, versionEnBd(tramite));
        assertThat(marcar.getStatusCode().value()).isEqualTo(200);
        ResponseEntity<String> desmarcar = desmarcar(tramite, tokenAna, versionEnBd(tramite));
        assertThat(desmarcar.getStatusCode().value()).isEqualTo(200);

        ResponseEntity<String> aprobar = postCrudo("/tramites/" + pendiente.getId() + "/aprobar", tokenAna,
                "{\"version\":" + versionEnBd(pendiente) + "}");
        assertThat(aprobar.getStatusCode().value()).isEqualTo(403);
        assertThat(motivo(aprobar)).isEqualTo(TramiteController.MOTIVO_SUSCRIPCION_NO_PERMITE_APROBAR);
        assertThat(estadoEnBd(pendiente)).isEqualTo("PENDIENTE_REVISION");
    }

    // ------------------------------------------------------------- lectura en HECHO_EN_OVZ

    /** La ficha se abre en HECHO_EN_OVZ: el detalle devuelve quien y cuando. */
    @Test
    void elDetalleEnHechoEnOvzDevuelveQuienYCuando() throws IOException {
        Tramite tramite = aprobado(gestoriaA, contactoA, explotacionA);
        JsonNode marcado = objectMapper.readTree(marcar(tramite, tokenBruno, versionEnBd(tramite)).getBody());

        ResponseEntity<String> detalle = get("/tramites/" + tramite.getId(), tokenAna);

        assertThat(detalle.getStatusCode().value()).isEqualTo(200);
        JsonNode cuerpo = objectMapper.readTree(detalle.getBody());
        assertThat(cuerpo.get("estado").asText()).isEqualTo("HECHO_EN_OVZ");
        assertThat(cuerpo.get("hechoEnOvzPorNombre").asText()).isEqualTo("Bruno Gil");
        assertThat(cuerpo.get("hechoEnOvzEn").asText()).isEqualTo(marcado.get("hechoEnOvzEn").asText());
        assertThat(cuerpo.get("explotacionCodigoRega").asText()).isEqualTo("ES990000000001");
        assertThat(detalle.getBody()).doesNotContain("bruno.hecho@test.com");

        // Sin marca, los dos campos vienen a null (no ausentes).
        Tramite sinMarca = aprobado(gestoriaA, contactoA, explotacionA);
        JsonNode sin = objectMapper.readTree(get("/tramites/" + sinMarca.getId(), tokenAna).getBody());
        assertThat(sin.has("hechoEnOvzEn")).isTrue();
        assertThat(sin.get("hechoEnOvzEn").isNull()).isTrue();
        assertThat(sin.get("hechoEnOvzPorNombre").isNull()).isTrue();
    }

    @Test
    void filtrarPorHechoEnOvzDevuelveSoloLosMarcadosDeLaPropiaGestoria() throws IOException {
        Tramite hecho1 = hechoEnOvz(gestoriaA, contactoA, explotacionA, anaA);
        Tramite hecho2 = hechoEnOvz(gestoriaA, contactoA, explotacionA, brunoA);
        aprobado(gestoriaA, contactoA, explotacionA);
        nuevoTramite(gestoriaA, contactoA, explotacionA, EstadoTramite.PENDIENTE_REVISION);
        hechoEnOvz(gestoriaB, contactoB, explotacionB, carlaB);

        JsonNode pagina = objectMapper.readTree(get("/tramites?estado=HECHO_EN_OVZ&size=20", tokenAna).getBody());

        assertThat(pagina.get("totalElements").asLong()).isEqualTo(2);
        Map<Long, String> quien = new HashMap<>();
        pagina.get("content").forEach(t -> quien.put(t.get("id").asLong(), t.get("hechoEnOvzPorNombre").asText()));
        assertThat(quien).containsOnly(Map.entry(hecho1.getId(), "Ana Pérez"), Map.entry(hecho2.getId(), "Bruno Gil"));
        assertThat(pagina.toString()).doesNotContain("Carla Ruiz");

        JsonNode deB = objectMapper.readTree(get("/tramites?estado=HECHO_EN_OVZ&size=20", tokenCarla).getBody());
        assertThat(deB.get("totalElements").asLong()).isEqualTo(1);
        assertThat(deB.get("content").get(0).get("hechoEnOvzPorNombre").asText()).isEqualTo("Carla Ruiz");
    }

    @Test
    void editarAprobarYRechazarEnHechoEnOvzSon409YNoCambianNada() throws IOException {
        Tramite tramite = hechoEnOvz(gestoriaA, contactoA, explotacionA, anaA);
        Map<String, Object> foto = fila(tramite);
        long v = versionEnBd(tramite);

        ResponseEntity<String> patch = patchCrudo(tramite.getId(), tokenAna,
                Map.of("version", v, "tipoTramite", "BAJA_MUERTE"));
        assertThat(patch.getStatusCode().value()).isEqualTo(409);
        assertThat(motivo(patch)).isEqualTo(MOTIVO_EDITAR);

        ResponseEntity<String> aprobar = postCrudo("/tramites/" + tramite.getId() + "/aprobar", tokenAna,
                "{\"version\":" + v + "}");
        assertThat(aprobar.getStatusCode().value()).isEqualTo(409);
        assertThat(motivo(aprobar)).isEqualTo("Solo se puede aprobar un trámite pendiente de revisión.");
        ResponseEntity<String> rechazar = postCrudo("/tramites/" + tramite.getId() + "/rechazar", tokenAna,
                "{\"version\":" + v + "}");
        assertThat(rechazar.getStatusCode().value()).isEqualTo(409);
        assertThat(motivo(rechazar)).isEqualTo("Solo se puede rechazar un trámite pendiente de revisión.");
        assertThat(fila(tramite)).isEqualTo(foto);
    }

    // ------------------------------------------------------------- N+1 y JSON

    /** El usuario que marco llega en la consulta de la pagina (EntityGraph), nunca una carga por fila:
     * varios tramites marcados por usuarios distintos, con y sin filtro de estado. */
    @Test
    void elListadoTraeAlUsuarioQueMarcoSinUnaConsultaPorFila() throws IOException {
        Usuario dani = crearUsuario(gestoriaA, "dani.hecho@test.com", "Dani Sanz");
        hechoEnOvz(gestoriaA, contactoA, explotacionA, anaA);
        hechoEnOvz(gestoriaA, contactoA, explotacionA, brunoA);
        hechoEnOvz(gestoriaA, contactoA, null, dani);
        hechoEnOvz(gestoriaA, contactoA, explotacionA, anaA);
        aprobado(gestoriaA, contactoA, explotacionA);

        for (String ruta : List.of("/tramites?size=20", "/tramites?estado=HECHO_EN_OVZ&size=20",
                "/tramites?estado=HECHO_EN_OVZ&size=2&page=1")) {
            SqlCapturadoInspector.limpiar();
            ResponseEntity<String> respuesta = get(ruta, tokenAna);
            assertThat(respuesta.getStatusCode().value()).as(ruta).isEqualTo(200);
            assertThat(consultasDeTramiteConJoinAUsuario())
                    .as(ruta + ": la consulta de la pagina trae al usuario con un join (si el formato del SQL "
                            + "cambiara, el contador de cargas sueltas podria quedarse en 0 sin medir nada)")
                    .isPositive();
            assertThat(cargasSueltasDeUsuario()).as(ruta + ": ninguna carga de usuario por fila").isZero();
        }
        JsonNode todo = objectMapper.readTree(get("/tramites?estado=HECHO_EN_OVZ&size=20", tokenAna).getBody());
        List<String> nombres = new ArrayList<>();
        todo.get("content").forEach(t -> nombres.add(t.get("hechoEnOvzPorNombre").asText()));
        assertThat(nombres).containsExactlyInAnyOrder("Ana Pérez", "Bruno Gil", "Dani Sanz", "Ana Pérez");
    }

    /** hechoEnOvzEn se serializa en ISO-8601 UTC con "Z" con el ObjectMapper de la aplicacion. */
    @Test
    void hechoEnOvzEnSeSerializaEnIso8601UtcConZ() throws IOException {
        Tramite enMemoria = new Tramite();
        enMemoria.setEstado(EstadoTramite.HECHO_EN_OVZ);
        enMemoria.setHechoEnOvzEn(Instant.parse("2026-10-07T10:30:00Z"));
        enMemoria.setHechoEnOvzPor(anaA);

        String json = objectMapper.writeValueAsString(TramiteResponse.from(enMemoria, List.of()));

        assertThat(json).contains("\"hechoEnOvzEn\":\"2026-10-07T10:30:00Z\"")
                .contains("\"hechoEnOvzPorNombre\":\"Ana Pérez\"");
        // Y por HTTP, el instante guardado vuelve tal cual (TIMESTAMP sin zona, Instant de ida y vuelta).
        Tramite tramite = hechoEnOvz(gestoriaA, contactoA, explotacionA, anaA);
        JsonNode detalle = objectMapper.readTree(get("/tramites/" + tramite.getId(), tokenAna).getBody());
        assertThat(detalle.get("hechoEnOvzEn").asText()).isEqualTo("2026-10-06T09:15:00Z");
    }

    // ------------------------------------------------------------- mensaje con gestoriaId

    /** Un MensajeCampo de B enlazado (datos inconsistentes, sembrados a mano) al Tramite de A, mas
     * reciente que el de A, no aparece ni en el detalle de A ni en la respuesta de su PATCH. */
    @Test
    void elMensajeDeOtraGestoriaEnlazadoAlTramiteNoApareceEnDetalleNiEnPatch() throws IOException {
        Tramite tramite = nuevoTramite(gestoriaA, contactoA, explotacionA, EstadoTramite.PENDIENTE_REVISION);
        nuevoMensaje("SM-hecho-A", gestoriaA, contactoA, tramite, "Mensaje de A", Instant.parse("2026-10-01T08:00:00Z"));
        nuevoMensaje("SM-hecho-B", gestoriaB, contactoB, tramite, "Mensaje de B", Instant.parse("2026-10-02T08:00:00Z"));

        JsonNode detalle = objectMapper.readTree(get("/tramites/" + tramite.getId(), tokenAna).getBody());
        assertThat(detalle.get("mensajeOriginal").asText()).isEqualTo("Mensaje de A");

        ResponseEntity<String> patch = patchCrudo(tramite.getId(), tokenAna,
                Map.of("version", versionEnBd(tramite), "tipoTramite", "BAJA_MUERTE"));
        assertThat(patch.getStatusCode().value()).isEqualTo(200);
        assertThat(objectMapper.readTree(patch.getBody()).get("mensajeOriginal").asText()).isEqualTo("Mensaje de A");
        assertThat(patch.getBody()).doesNotContain("Mensaje de B");
    }

    // ------------------------------------------------------------- utilidades

    private long cargasSueltasDeUsuario() {
        return SqlCapturadoInspector.capturado().stream()
                .map(sql -> sql.toLowerCase(Locale.ROOT))
                .filter(sql -> sql.contains("from usuario "))
                .count();
    }

    private long consultasDeTramiteConJoinAUsuario() {
        return SqlCapturadoInspector.capturado().stream()
                .map(sql -> sql.toLowerCase(Locale.ROOT))
                .filter(sql -> sql.contains("from tramite ") && sql.contains("join usuario "))
                .count();
    }

    /** Estado, version y marca del Tramite leidos directamente de la BD (claves en minusculas). */
    private Map<String, Object> fila(Tramite tramite) {
        Map<String, Object> fila = new HashMap<>();
        jdbcTemplate.queryForMap("select estado, version, hecho_en_ovz_por, hecho_en_ovz_en, tipo_tramite "
                        + "from tramite where id = ?", tramite.getId())
                .forEach((columna, valor) -> fila.put(columna.toLowerCase(Locale.ROOT), valor));
        return fila;
    }

    private String estadoEnBd(Tramite tramite) {
        return jdbcTemplate.queryForObject("select estado from tramite where id = ?", String.class, tramite.getId());
    }

    private long versionEnBd(Tramite tramite) {
        return jdbcTemplate.queryForObject("select version from tramite where id = ?", Long.class, tramite.getId());
    }

    private String motivo(ResponseEntity<String> respuesta) throws IOException {
        return objectMapper.readTree(respuesta.getBody()).get("motivo").asText();
    }

    private ResponseEntity<String> marcar(Tramite tramite, String token, long version) {
        return postCrudo("/tramites/" + tramite.getId() + "/marcar-hecho-ovz", token, "{\"version\":" + version + "}");
    }

    private ResponseEntity<String> desmarcar(Tramite tramite, String token, long version) {
        return postCrudo("/tramites/" + tramite.getId() + "/desmarcar-hecho-ovz", token, "{\"version\":" + version + "}");
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

    private ResponseEntity<String> patchCrudo(Long id, String token, Map<String, Object> cuerpo) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange("/tramites/" + id, HttpMethod.PATCH, new HttpEntity<>(cuerpo, headers), String.class);
    }

    private ResponseEntity<String> get(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private Tramite aprobado(Gestoria gestoria, Contacto contacto, Explotacion explotacion) {
        return nuevoTramite(gestoria, contacto, explotacion, EstadoTramite.APROBADO);
    }

    private Tramite nuevoTramite(Gestoria gestoria, Contacto contacto, Explotacion explotacion, EstadoTramite estado) {
        Tramite tramite = new Tramite();
        tramite.setGestoria(gestoria);
        tramite.setContacto(contacto);
        tramite.setExplotacion(explotacion);
        tramite.setTipoTramite(TipoTramite.ALTA_NACIMIENTO);
        tramite.setEstado(estado);
        return tramiteRepository.save(tramite);
    }

    /** Un tramite ya marcado como hecho en OVZ por {@code quien} (con quien y cuando: CHECK de V21). */
    private Tramite hechoEnOvz(Gestoria gestoria, Contacto contacto, Explotacion explotacion, Usuario quien) {
        Tramite tramite = new Tramite();
        tramite.setGestoria(gestoria);
        tramite.setContacto(contacto);
        tramite.setExplotacion(explotacion);
        tramite.setTipoTramite(TipoTramite.ALTA_NACIMIENTO);
        tramite.setEstado(EstadoTramite.HECHO_EN_OVZ);
        tramite.setHechoEnOvzPor(quien);
        tramite.setHechoEnOvzEn(Instant.parse("2026-10-06T09:15:00Z"));
        return tramiteRepository.save(tramite);
    }

    private void nuevoMensaje(String sid, Gestoria gestoria, Contacto contacto, Tramite tramite, String cuerpo,
                              Instant cuando) {
        MensajeCampo mensaje = new MensajeCampo();
        mensaje.setMessageSid(sid);
        mensaje.setTelefonoOrigen(contacto.getTelefono());
        mensaje.setCuerpo(cuerpo);
        mensaje.setGestoria(gestoria);
        mensaje.setContacto(contacto);
        mensaje.setTramite(tramite);
        mensaje.setCreatedAt(cuando);
        mensajeCampoRepository.save(mensaje);
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

    private Contacto nuevoContacto(Gestoria gestoria, String telefono) {
        Contacto contacto = new Contacto();
        contacto.setTelefono(telefono);
        contacto.setNombre("Contacto hecho OVZ");
        contacto.setGestoria(gestoria);
        return contactoRepository.save(contacto);
    }

    private Usuario crearUsuario(Gestoria gestoria, String email, String nombre) {
        Usuario usuario = new Usuario();
        usuario.setGestoria(gestoria);
        usuario.setEmail(email);
        usuario.setPasswordHash(passwordEncoder.encode("password123"));
        usuario.setNombre(nombre);
        usuario.setActivo(true);
        return usuarioRepository.save(usuario);
    }

    private String login(String email) {
        ResponseEntity<LoginResponse> respuesta = restTemplate.postForEntity(
                "/auth/login", new LoginRequest(email, "password123"), LoginResponse.class);
        return respuesta.getBody().token();
    }
}
