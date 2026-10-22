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
import com.ganera.core.tramite.IaDePruebaConfig.RelojAjustable;
import com.ganera.core.whatsapp.EnvioWhatsappFalso;
import com.ganera.core.whatsapp.MensajeCampo;
import com.ganera.core.whatsapp.MensajeCampoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.ganera.core.shared.auditoria.LimpiezaAuditoria;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E real (servidor embebido + TestRestTemplate) de «Descartar» un mensaje sin tramite (plan 2026-10-12):
 * POST /tramites/{id}/descartar y /deshacer-descarte, con DOS Gestorias. El estado «sin cambios» se lee de la
 * BD con JdbcTemplate, fuera de cualquier transaccion del servidor (rollback real). Twilio nunca se llama
 * ({@link EnvioWhatsappFalso} registra cualquier llamada) y el reloj es el ajustable de IaDePruebaConfig
 * (2031-03-10T09:00Z). Mismo contexto que RespuestaWhatsappEndToEndTest (misma configuracion).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "com.ganera.core.tramite.SqlCapturadoInspector")
@Import({IaDePruebaConfig.class, EnvioWhatsappFalso.Config.class})
class TramiteDescarteEndToEndTest {

    private static final Instant AHORA = IaDePruebaConfig.AHORA;
    private static final String MOTIVO_DESCARTAR = "Solo se puede descartar un trámite pendiente de revisión.";
    private static final String MOTIVO_NO_DESCARTABLE =
            "Solo se descarta un mensaje sin trámite que nadie ha completado. Si no procede, recházalo.";
    private static final String MOTIVO_DESHACER = "Este trámite no está descartado; no hay nada que deshacer.";
    private static final String MOTIVO_DESFASADA =
            "El trámite ha cambiado desde que lo abriste. Vuelve a cargarlo y revísalo antes de continuar.";
    private static final String MOTIVO_WHATSAPP =
            "Este mensaje se descartó como sin trámite; no se responde desde Ganera.";

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private EnvioWhatsappFalso envio;
    @Autowired
    private RelojAjustable reloj;
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
    private String tokenAna;
    private String tokenBruno;
    private String tokenCarla;
    private Suscripcion suscripcionA;
    private Explotacion explotacionA;
    private Contacto contactoA;
    private Contacto contactoB;
    private int secuenciaSid;

    @BeforeEach
    void preparar() {
        envio.reiniciar();
        reloj.fijar(AHORA);
        gestoriaA = gestoriaRepository.save(new Gestoria("Gestoría Descarte A"));
        gestoriaB = gestoriaRepository.save(new Gestoria("Gestoría Descarte B"));
        anaA = crearUsuario(gestoriaA, "ana.descarte@test.com", "Ana Pérez");
        brunoA = crearUsuario(gestoriaA, "bruno.descarte@test.com", "Bruno Gil");
        crearUsuario(gestoriaB, "carla.descarte@test.com", "Carla Ruiz");
        tokenAna = login("ana.descarte@test.com");
        tokenBruno = login("bruno.descarte@test.com");
        tokenCarla = login("carla.descarte@test.com");
        suscripcionA = suscripcion(gestoriaA, EstadoSuscripcion.ACTIVA);
        suscripcion(gestoriaB, EstadoSuscripcion.ACTIVA);
        explotacionA = nuevaExplotacion(gestoriaA, "ES960000000001");
        contactoA = nuevoContacto(gestoriaA, "+34600960001");
        contactoB = nuevoContacto(gestoriaB, "+34600960002");
        SqlCapturadoInspector.limpiar();
    }

    @AfterEach
    void limpiar() {
        LimpiezaAuditoria.borrar(jdbcTemplate);
        jdbcTemplate.update("delete from tramite_evento");
        jdbcTemplate.update("delete from respuesta_whatsapp");
        mensajeCampoRepository.deleteAll();
        tramiteCrotalRepository.deleteAll();
        tramiteRepository.deleteAll();
        suscripcionRepository.deleteAll();
        contactoRepository.deleteAll();
        explotacionRepository.deleteAll();
        ganaderoRepository.deleteAll();
        usuarioRepository.deleteAll();
        gestoriaRepository.deleteAll();
        envio.reiniciar();
        reloj.fijar(AHORA);
    }

    // ------------------------------------------------------------- descartar y deshacer

    @Test
    void descartarYDeshacerUnoPropioDevuelven200ConElDetalleYLaVersionMasUno() throws IOException {
        Tramite tramite = sinTramite(gestoriaA, contactoA);
        long v0 = versionEnBd(tramite);

        ResponseEntity<String> descartar = descartar(tramite, tokenAna, v0);

        assertThat(descartar.getStatusCode().value()).isEqualTo(200);
        JsonNode descartado = objectMapper.readTree(descartar.getBody());
        assertThat(descartado.get("estado").asText()).isEqualTo("RECHAZADO");
        assertThat(descartado.get("motivoRechazo").asText()).isEqualTo("SIN_TRAMITE");
        assertThat(descartado.get("descartable").asBoolean()).isFalse();
        assertThat(descartado.get("version").asLong()).isEqualTo(v0 + 1);
        // Es un TramiteDetalleResponse: lleva el mensaje, el bloque whatsapp y el historial.
        assertThat(descartado.get("mensajeOriginal").asText()).isEqualTo("vale, gracias");
        assertThat(descartado.get("whatsapp").get("motivoNoDisponible").asText()).isEqualTo(MOTIVO_WHATSAPP);
        assertThat(descartado.get("historialDescarte")).hasSize(1);
        assertThat(fila(tramite)).containsEntry("estado", "RECHAZADO").containsEntry("motivo_rechazo", "SIN_TRAMITE")
                .containsEntry("version", v0 + 1);

        ResponseEntity<String> deshacer = deshacer(tramite, tokenBruno, v0 + 1);

        assertThat(deshacer.getStatusCode().value()).isEqualTo(200);
        JsonNode deshecho = objectMapper.readTree(deshacer.getBody());
        assertThat(deshecho.get("estado").asText()).isEqualTo("PENDIENTE_REVISION");
        assertThat(deshecho.get("motivoRechazo").isNull()).isTrue();
        assertThat(deshecho.get("descartable").asBoolean()).isTrue();
        assertThat(deshecho.get("version").asLong()).isEqualTo(v0 + 2);
        assertThat(deshecho.get("whatsapp").get("motivoNoDisponible").isNull()).isTrue();
        assertThat(fila(tramite)).containsEntry("estado", "PENDIENTE_REVISION").containsEntry("version", v0 + 2);
        assertThat(fila(tramite).get("motivo_rechazo")).isNull();
        // Descartar nunca envia nada.
        assertThat(envio.llamadas()).isEmpty();
    }

    /** A6: descartar -> deshacer -> descartar deja tres eventos con el nombre del usuario del JWT (nunca el
     * email ni el id), en orden y con la hora del Clock en ISO-8601 UTC con «Z». */
    @Test
    void elHistorialGuardaLasTresAccionesConQuienYCuando() throws IOException {
        Tramite tramite = sinTramite(gestoriaA, contactoA);
        long v0 = versionEnBd(tramite);

        assertThat(descartar(tramite, tokenAna, v0).getStatusCode().value()).isEqualTo(200);
        reloj.avanzar(Duration.ofMinutes(5));
        assertThat(deshacer(tramite, tokenBruno, v0 + 1).getStatusCode().value()).isEqualTo(200);
        reloj.avanzar(Duration.ofMinutes(5));
        // Un campo de mas en el cuerpo no cambia quien consta.
        ResponseEntity<String> ultima = postCrudo("/tramites/" + tramite.getId() + "/descartar", tokenAna,
                "{\"version\":" + (v0 + 2) + ",\"usuarioId\":" + brunoA.getId() + ",\"usuarioNombre\":\"Otra\"}");
        assertThat(ultima.getStatusCode().value()).isEqualTo(200);

        JsonNode detalle = detalle(tramite, tokenAna);
        JsonNode historial = detalle.get("historialDescarte");
        assertThat(historial).hasSize(3);
        assertThat(historial).extracting(e -> e.get("tipo").asText())
                .containsExactly("DESCARTADO", "DESCARTE_DESHECHO", "DESCARTADO");
        assertThat(historial).extracting(e -> e.get("usuarioNombre").asText())
                .containsExactly("Ana Pérez", "Bruno Gil", "Ana Pérez");
        assertThat(historial).extracting(e -> e.get("en").asText())
                .containsExactly("2031-03-10T09:00:00Z", "2031-03-10T09:05:00Z", "2031-03-10T09:10:00Z");
        List<String> campos = new ArrayList<>();
        historial.get(0).fieldNames().forEachRemaining(campos::add);
        assertThat(campos).containsExactlyInAnyOrder("tipo", "usuarioNombre", "en");
        assertThat(detalle.toString()).doesNotContain("ana.descarte@test.com").doesNotContain("bruno.descarte@test.com");
        assertThat(objectMapper.readTree(ultima.getBody()).get("historialDescarte")).isEqualTo(historial);
        assertThat(jdbcTemplate.queryForList("select usuario_id from tramite_evento where tramite_id = ? order by id",
                Long.class, tramite.getId())).containsExactly(anaA.getId(), brunoA.getId(), anaA.getId());
        assertThat(versionEnBd(tramite)).isEqualTo(v0 + 3);
    }

    /** El detalle carga el historial en UNA consulta con su usuario (sin N+1), y el listado no lo toca. */
    @Test
    void elDetalleCargaElHistorialEnUnaConsultaYElListadoNoLoToca() throws IOException {
        Tramite tramite = sinTramite(gestoriaA, contactoA);
        long v0 = versionEnBd(tramite);
        descartar(tramite, tokenAna, v0);
        deshacer(tramite, tokenBruno, v0 + 1);
        descartar(tramite, tokenAna, v0 + 2);

        SqlCapturadoInspector.limpiar();
        detalle(tramite, tokenAna);
        List<String> sql = SqlCapturadoInspector.capturado().stream().map(s -> s.toLowerCase(Locale.ROOT)).toList();
        assertThat(sql).filteredOn(s -> s.contains("from tramite_evento ")).hasSize(1)
                .allSatisfy(s -> assertThat(s).contains("join usuario "));
        assertThat(sql).filteredOn(s -> s.contains("from usuario ")).isEmpty();

        SqlCapturadoInspector.limpiar();
        JsonNode pagina = objectMapper.readTree(get("/tramites?size=20", tokenAna).getBody());
        assertThat(SqlCapturadoInspector.capturado()).noneMatch(s -> s.toLowerCase(Locale.ROOT).contains("tramite_evento"));
        assertThat(pagina.get("content").get(0).has("historialDescarte")).isFalse();
    }

    // ------------------------------------------------------------- aislamiento, 400 y 401

    @Test
    void unTramiteDeOtraGestoriaOInexistenteEs404SinCuerpoYNoCambiaNada() throws IOException {
        Tramite pendienteB = sinTramite(gestoriaB, contactoB);
        Tramite descartadoB = sinTramite(gestoriaB, contactoB);
        assertThat(descartar(descartadoB, tokenCarla, versionEnBd(descartadoB)).getStatusCode().value()).isEqualTo(200);
        Map<String, Object> fotoPendiente = fila(pendienteB);
        Map<String, Object> fotoDescartado = fila(descartadoB);

        List<ResponseEntity<String>> respuestas = List.of(
                descartar(pendienteB, tokenAna, versionEnBd(pendienteB)),
                deshacer(descartadoB, tokenAna, versionEnBd(descartadoB)),
                // El endpoint contrario sobre el ajeno tambien es 404 (no un 409 que revele su estado).
                deshacer(pendienteB, tokenAna, versionEnBd(pendienteB)),
                descartar(descartadoB, tokenAna, versionEnBd(descartadoB)),
                postCrudo("/tramites/999999999/descartar", tokenAna, "{\"version\":0}"),
                postCrudo("/tramites/999999999/deshacer-descarte", tokenAna, "{\"version\":0}"),
                get("/tramites/" + descartadoB.getId(), tokenAna));

        for (ResponseEntity<String> respuesta : respuestas) {
            assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
            assertThat(respuesta.getBody()).isNullOrEmpty();
        }
        assertThat(fila(pendienteB)).isEqualTo(fotoPendiente);
        assertThat(fila(descartadoB)).isEqualTo(fotoDescartado);
        assertThat(eventos(pendienteB)).isZero();
        assertThat(eventos(descartadoB)).isEqualTo(1);
        // B sigue viendo su historial (solo su evento).
        assertThat(detalle(descartadoB, tokenCarla).get("historialDescarte").get(0).get("usuarioNombre").asText())
                .isEqualTo("Carla Ruiz");
    }

    /** 400 sin version ANTES de buscar el Tramite: el mismo cuerpo para uno propio, uno ajeno y uno
     * inexistente, en los dos endpoints, y nada cambia. */
    @Test
    void sinVersionEs400ConElMismoMotivoParaPropioAjenoEInexistente() throws IOException {
        Tramite propio = sinTramite(gestoriaA, contactoA);
        Tramite ajeno = sinTramite(gestoriaB, contactoB);
        Map<String, Object> fotoPropio = fila(propio);
        Map<String, Object> fotoAjeno = fila(ajeno);

        List<String> cuerpos = new ArrayList<>();
        for (Long id : List.of(propio.getId(), ajeno.getId(), 999999999L)) {
            for (String accion : List.of("descartar", "deshacer-descarte")) {
                for (String cuerpo : Arrays.asList(null, "{}", "{\"version\":null}")) {
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
    void sinJwtEs401YNoCambiaNada() {
        Tramite tramite = sinTramite(gestoriaA, contactoA);
        Map<String, Object> foto = fila(tramite);

        for (String accion : List.of("descartar", "deshacer-descarte")) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<String> respuesta = restTemplate.exchange("/tramites/" + tramite.getId() + "/" + accion,
                    HttpMethod.POST, new HttpEntity<>("{\"version\":0}", headers), String.class);
            assertThat(respuesta.getStatusCode().value()).as(accion).isEqualTo(401);
        }
        assertThat(fila(tramite)).isEqualTo(foto);
    }

    // ------------------------------------------------------------- 409

    @Test
    void descartarFueraDePendienteNoDescartableYConVersionDesfasadaSon409YNoCambianNada() throws IOException {
        Tramite aprobado = sinTramite(gestoriaA, contactoA);
        aprobado.setEstado(EstadoTramite.APROBADO);
        tramiteRepository.save(aprobado);
        Tramite conTipo = sinTramite(gestoriaA, contactoA);
        conTipo.setTipoTramite(TipoTramite.BAJA_MUERTE);
        tramiteRepository.save(conTipo);
        Tramite completada = sinTramite(gestoriaA, contactoA);
        completada.setEstadoExtraccion(EstadoExtraccion.COMPLETADA);
        tramiteRepository.save(completada);
        Tramite descartable = sinTramite(gestoriaA, contactoA);
        Map<String, Map<String, Object>> fotos = new HashMap<>();
        for (Tramite t : List.of(aprobado, conTipo, completada, descartable)) {
            fotos.put(String.valueOf(t.getId()), fila(t));
        }

        ResponseEntity<String> estado = descartar(aprobado, tokenAna, versionEnBd(aprobado) + 3);
        assertThat(estado.getStatusCode().value()).isEqualTo(409);
        assertThat(motivo(estado)).isEqualTo(MOTIVO_DESCARTAR);
        for (Tramite t : List.of(conTipo, completada)) {
            ResponseEntity<String> noDescartable = descartar(t, tokenAna, versionEnBd(t));
            assertThat(noDescartable.getStatusCode().value()).isEqualTo(409);
            assertThat(motivo(noDescartable)).isEqualTo(MOTIVO_NO_DESCARTABLE);
        }
        // Version antes que «descartable»: con version desfasada, el motivo de version.
        ResponseEntity<String> desfasadaConTipo = descartar(conTipo, tokenAna, versionEnBd(conTipo) + 1);
        assertThat(motivo(desfasadaConTipo)).isEqualTo(MOTIVO_DESFASADA);
        for (long desfase : List.of(-1L, 1L)) {
            ResponseEntity<String> desfasada = descartar(descartable, tokenAna, versionEnBd(descartable) + desfase);
            assertThat(desfasada.getStatusCode().value()).isEqualTo(409);
            assertThat(motivo(desfasada)).isEqualTo(MOTIVO_DESFASADA);
        }
        for (Tramite t : List.of(aprobado, conTipo, completada, descartable)) {
            assertThat(fila(t)).isEqualTo(fotos.get(String.valueOf(t.getId())));
            assertThat(eventos(t)).isZero();
        }
    }

    /** A1: deshacer un rechazo normal (o un pendiente) es 409 y no cambia nada; con version desfasada, 409. */
    @Test
    void deshacerUnRechazoNormalUnPendienteOConVersionDesfasadaEs409YNoCambiaNada() throws IOException {
        Tramite rechazado = sinTramite(gestoriaA, contactoA);
        ResponseEntity<String> rechazar = postCrudo("/tramites/" + rechazado.getId() + "/rechazar", tokenAna,
                "{\"version\":" + versionEnBd(rechazado) + "}");
        assertThat(rechazar.getStatusCode().value()).isEqualTo(200);
        assertThat(objectMapper.readTree(rechazar.getBody()).get("motivoRechazo").isNull()).isTrue();
        Tramite pendiente = sinTramite(gestoriaA, contactoA);
        Tramite descartado = sinTramite(gestoriaA, contactoA);
        descartar(descartado, tokenAna, versionEnBd(descartado));
        Map<String, Object> fotoRechazado = fila(rechazado);
        Map<String, Object> fotoPendiente = fila(pendiente);
        Map<String, Object> fotoDescartado = fila(descartado);

        for (Tramite t : List.of(rechazado, pendiente)) {
            ResponseEntity<String> respuesta = deshacer(t, tokenAna, versionEnBd(t));
            assertThat(respuesta.getStatusCode().value()).isEqualTo(409);
            assertThat(motivo(respuesta)).isEqualTo(MOTIVO_DESHACER);
        }
        ResponseEntity<String> desfasada = deshacer(descartado, tokenAna, versionEnBd(descartado) - 1);
        assertThat(desfasada.getStatusCode().value()).isEqualTo(409);
        assertThat(motivo(desfasada)).isEqualTo(MOTIVO_DESFASADA);

        assertThat(fila(rechazado)).isEqualTo(fotoRechazado);
        assertThat(fila(rechazado).get("motivo_rechazo")).isNull();
        assertThat(fila(pendiente)).isEqualTo(fotoPendiente);
        assertThat(fila(descartado)).isEqualTo(fotoDescartado);
        assertThat(eventos(rechazado)).isZero();
        assertThat(eventos(descartado)).isEqualTo(1);
    }

    // ------------------------------------------------------------- suscripcion y contrato

    /** D8: sin comprobacion de suscripcion. Una gestoria SUSPENDIDA descarta y deshace, y sigue sin aprobar. */
    @Test
    void unaGestoriaSuspendidaDescartaYDeshacePeroNoApruebaNada() throws IOException {
        suscripcionA.setEstado(EstadoSuscripcion.SUSPENDIDA);
        suscripcionRepository.save(suscripcionA);
        Tramite tramite = sinTramite(gestoriaA, contactoA);
        long v0 = versionEnBd(tramite);

        assertThat(descartar(tramite, tokenAna, v0).getStatusCode().value()).isEqualTo(200);
        assertThat(deshacer(tramite, tokenAna, v0 + 1).getStatusCode().value()).isEqualTo(200);

        ResponseEntity<String> aprobar = postCrudo("/tramites/" + tramite.getId() + "/aprobar", tokenAna,
                "{\"version\":" + versionEnBd(tramite) + "}");
        assertThat(aprobar.getStatusCode().value()).isEqualTo(403);
        assertThat(estadoEnBd(tramite)).isEqualTo("PENDIENTE_REVISION");
    }

    /** La lista y el detalle exponen motivoRechazo y descartable; un detalle sin eventos lleva historial []. */
    @Test
    void laListaYElDetalleExponenMotivoRechazoYDescartable() throws IOException {
        Tramite descartable = sinTramite(gestoriaA, contactoA);
        Tramite conTipo = sinTramite(gestoriaA, contactoA);
        ResponseEntity<String> patch = patchCrudo(conTipo.getId(), tokenAna,
                Map.of("version", versionEnBd(conTipo), "tipoTramite", "BAJA_MUERTE"));
        assertThat(objectMapper.readTree(patch.getBody()).get("descartable").asBoolean()).isFalse();
        Tramite conCrotal = sinTramite(gestoriaA, contactoA);
        patchCrudo(conCrotal.getId(), tokenAna, Map.of("version", versionEnBd(conCrotal), "crotales", List.of("1234")));
        Tramite descartado = sinTramite(gestoriaA, contactoA);
        descartar(descartado, tokenAna, versionEnBd(descartado));

        Map<Long, JsonNode> filas = new HashMap<>();
        objectMapper.readTree(get("/tramites?size=20", tokenAna).getBody()).get("content")
                .forEach(t -> filas.put(t.get("id").asLong(), t));
        assertThat(filas.get(descartable.getId()).get("descartable").asBoolean()).isTrue();
        assertThat(filas.get(descartable.getId()).get("motivoRechazo").isNull()).isTrue();
        assertThat(filas.get(descartable.getId()).get("estadoExtraccion").asText()).isEqualTo("SIN_TRAMITE");
        assertThat(filas.get(conTipo.getId()).get("descartable").asBoolean()).isFalse();
        assertThat(filas.get(conCrotal.getId()).get("descartable").asBoolean()).isFalse();
        assertThat(filas.get(descartado.getId()).get("descartable").asBoolean()).isFalse();
        assertThat(filas.get(descartado.getId()).get("motivoRechazo").asText()).isEqualTo("SIN_TRAMITE");
        assertThat(filas.get(descartado.getId()).get("estado").asText()).isEqualTo("RECHAZADO");

        JsonNode sinEventos = detalle(descartable, tokenAna);
        assertThat(sinEventos.get("descartable").asBoolean()).isTrue();
        assertThat(sinEventos.get("motivoRechazo").isNull()).isTrue();
        assertThat(sinEventos.get("historialDescarte").isArray()).isTrue();
        assertThat(sinEventos.get("historialDescarte")).isEmpty();
        assertThat(sinEventos.get("whatsapp").get("motivoNoDisponible").isNull()).isTrue();
        JsonNode delDescartado = detalle(descartado, tokenAna);
        assertThat(delDescartado.get("motivoRechazo").asText()).isEqualTo("SIN_TRAMITE");
        assertThat(delDescartado.get("whatsapp").get("motivoNoDisponible").asText()).isEqualTo(MOTIVO_WHATSAPP);
        assertThat(envio.llamadas()).isEmpty();
    }

    // ------------------------------------------------------------- contrato con el frontend

    /** Fichero compartido con el frontend: sus tests usan ESTOS ejemplos como respuestas (MSW) y estas rutas. */
    static final Path CONTRATO = Path.of("..", "frontend", "src", "test", "contrato", "descarte-sin-tramite.json");

    /**
     * Contrato real backend ↔ frontend (petición de Antonio, 2026-10-12): las respuestas reales de la lista, del
     * detalle, de descartar y de deshacer tienen exactamente los campos (y tipos JSON) de los ejemplos que usa el
     * frontend, y las dos rutas del fichero existen con el cuerpo {version}. Si alguien renombra o quita un campo
     * en un lado, falla este test o los del frontend. Las respuestas reales quedan en target/contrato-real/ para
     * regenerar el fichero a mano si el contrato cambia a propósito.
     */
    @Test
    void lasRespuestasRealesCumplenElContratoQueUsaElFrontend() throws IOException {
        JsonNode contrato = objectMapper.readTree(CONTRATO.toFile());
        Tramite tramite = sinTramite(gestoriaA, contactoA);

        JsonNode itemLista = null;
        for (JsonNode t : objectMapper.readTree(get("/tramites?size=20", tokenAna).getBody()).get("content")) {
            if (t.get("id").asLong() == tramite.getId()) {
                itemLista = t;
            }
        }
        JsonNode pendiente = detalle(tramite, tokenAna);
        String rutaDescartar = contrato.at("/rutas/descartar").asText().replace("{id}", tramite.getId().toString());
        ResponseEntity<String> descartado = postCrudo(rutaDescartar, tokenAna,
                "{\"version\":" + pendiente.get("version").asLong() + "}");
        assertThat(descartado.getStatusCode().value()).isEqualTo(200);
        JsonNode detalleDescartado = objectMapper.readTree(descartado.getBody());
        String rutaDeshacer = contrato.at("/rutas/deshacerDescarte").asText().replace("{id}", tramite.getId().toString());
        ResponseEntity<String> deshecho = postCrudo(rutaDeshacer, tokenAna,
                "{\"version\":" + detalleDescartado.get("version").asLong() + "}");
        assertThat(deshecho.getStatusCode().value()).isEqualTo(200);
        JsonNode detalleDeshecho = objectMapper.readTree(deshecho.getBody());

        Path salida = Path.of("target", "contrato-real");
        Files.createDirectories(salida);
        Map<String, JsonNode> reales = new java.util.LinkedHashMap<>();
        reales.put("itemLista", itemLista);
        reales.put("detallePendiente", pendiente);
        reales.put("detalleDescartado", detalleDescartado);
        reales.put("detalleDeshecho", detalleDeshecho);
        Files.writeString(salida.resolve("descarte-sin-tramite.json"),
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(reales), StandardCharsets.UTF_8);

        for (Map.Entry<String, JsonNode> real : reales.entrySet()) {
            assertThat(contrato.has(real.getKey())).as("el contrato tiene «%s»", real.getKey()).isTrue();
            mismaForma(real.getKey(), contrato.get(real.getKey()), real.getValue());
        }
        // Los valores en los que se apoya la interfaz, iguales en el ejemplo y en la realidad.
        for (String campo : List.of("estado", "estadoExtraccion", "motivoRechazo", "descartable")) {
            for (Map.Entry<String, JsonNode> real : reales.entrySet()) {
                assertThat(real.getValue().get(campo))
                        .as("%s.%s", real.getKey(), campo)
                        .isEqualTo(contrato.get(real.getKey()).get(campo));
            }
        }
        assertThat(detalleDeshecho.at("/historialDescarte/1/tipo"))
                .isEqualTo(contrato.at("/detalleDeshecho/historialDescarte/1/tipo"));
        assertThat(detalleDescartado.at("/whatsapp/motivoNoDisponible"))
                .isEqualTo(contrato.at("/detalleDescartado/whatsapp/motivoNoDisponible"));
    }

    /** Mismos campos (ni uno más ni uno menos), mismo tipo JSON (también {@code null} donde el ejemplo es
     * {@code null}: los datos del test son fijos, así que el ejemplo y la realidad coinciden) y, en las listas, mismo
     * tamaño y elemento a elemento. Límite: una lista vacía en los dos lados (p. ej. {@code crotales: []}) no dice
     * nada de la forma de sus elementos. */
    private static void mismaForma(String ruta, JsonNode esperado, JsonNode real) {
        assertThat(real).as("%s existe", ruta).isNotNull();
        assertThat(real.getNodeType()).as("tipo JSON de %s", ruta).isEqualTo(esperado.getNodeType());
        if (esperado.isObject()) {
            List<String> camposEsperados = new ArrayList<>();
            esperado.fieldNames().forEachRemaining(camposEsperados::add);
            List<String> camposReales = new ArrayList<>();
            real.fieldNames().forEachRemaining(camposReales::add);
            assertThat(camposReales).as("campos de %s", ruta).containsExactlyInAnyOrderElementsOf(camposEsperados);
            for (String campo : camposEsperados) {
                mismaForma(ruta + "." + campo, esperado.get(campo), real.get(campo));
            }
        } else if (esperado.isArray()) {
            assertThat(real.size()).as("tamaño de %s", ruta).isEqualTo(esperado.size());
            for (int i = 0; i < esperado.size(); i++) {
                mismaForma(ruta + "[" + i + "]", esperado.get(i), real.get(i));
            }
        }
    }

    // ------------------------------------------------------------- utilidades

    private int eventos(Tramite tramite) {
        return jdbcTemplate.queryForObject("select count(*) from tramite_evento where tramite_id = ?", Integer.class,
                tramite.getId());
    }

    /** Estado, version, motivo, tipo y extraccion del Tramite leidos de la BD (claves en minusculas). */
    private Map<String, Object> fila(Tramite tramite) {
        Map<String, Object> fila = new HashMap<>();
        jdbcTemplate.queryForMap("select estado, version, motivo_rechazo, tipo_tramite, estado_extraccion "
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

    private JsonNode detalle(Tramite tramite, String token) throws IOException {
        ResponseEntity<String> respuesta = get("/tramites/" + tramite.getId(), token);
        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        return objectMapper.readTree(respuesta.getBody());
    }

    private ResponseEntity<String> descartar(Tramite tramite, String token, long version) {
        return postCrudo("/tramites/" + tramite.getId() + "/descartar", token, "{\"version\":" + version + "}");
    }

    private ResponseEntity<String> deshacer(Tramite tramite, String token, long version) {
        return postCrudo("/tramites/" + tramite.getId() + "/deshacer-descarte", token, "{\"version\":" + version + "}");
    }

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

    /** Un «vale, gracias» por WhatsApp que la IA vio como SIN_TRAMITE: pendiente, sin tipo ni crotales, con
     * el mensaje (que abre la ventana de 24 h). La explotacion solo en A (B no tiene ninguna aqui). */
    private Tramite sinTramite(Gestoria gestoria, Contacto contacto) {
        Tramite tramite = new Tramite();
        tramite.setGestoria(gestoria);
        tramite.setContacto(contacto);
        tramite.setExplotacion(gestoria == gestoriaA ? explotacionA : null);
        tramite.setEstado(EstadoTramite.PENDIENTE_REVISION);
        tramite.setOrigen(OrigenTramite.WHATSAPP);
        tramite.setEstadoExtraccion(EstadoExtraccion.SIN_TRAMITE);
        Tramite guardado = tramiteRepository.save(tramite);
        MensajeCampo mensaje = new MensajeCampo();
        mensaje.setMessageSid("SM-descarte-" + (++secuenciaSid) + "-" + UUID.randomUUID());
        mensaje.setTelefonoOrigen(contacto.getTelefono());
        mensaje.setCuerpo("vale, gracias");
        mensaje.setGestoria(gestoria);
        mensaje.setContacto(contacto);
        mensaje.setTramite(guardado);
        mensaje.setCreatedAt(AHORA.minus(Duration.ofHours(1)));
        mensajeCampoRepository.save(mensaje);
        return guardado;
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
        contacto.setNombre("Contacto descarte");
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
