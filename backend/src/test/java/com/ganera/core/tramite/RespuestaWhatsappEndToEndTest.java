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
import com.ganera.core.shared.auditoria.AccionAuditoria;
import com.ganera.core.shared.auditoria.ActorTipo;
import com.ganera.core.shared.auditoria.Auditoria;
import com.ganera.core.shared.auditoria.AuditoriaRepository;
import com.ganera.core.shared.auditoria.EntidadAuditada;
import com.ganera.core.whatsapp.EstadoRespuestaWhatsapp;
import com.ganera.core.whatsapp.RespuestaWhatsapp;
import com.ganera.core.whatsapp.RespuestaWhatsappRepository;
import com.ganera.core.whatsapp.MensajeCampo;
import com.ganera.core.whatsapp.MensajeCampoRepository;
import com.ganera.core.whatsapp.ResultadoEnvioWhatsapp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E real (servidor embebido + TestRestTemplate) de la respuesta por WhatsApp (B2 reducido, T1):
 * POST /tramites/{id}/respuestas-whatsapp y el bloque «whatsapp» del detalle, con DOS Gestorias.
 * Twilio nunca se llama: {@link EnvioWhatsappFalso} sustituye al envio real y registra cada llamada
 * (y si iba dentro de una transaccion). El reloj es el ajustable de IaDePruebaConfig (2031-03-10T09:00Z):
 * la ventana de 24 h se prueba moviendolo, sin esperas.
 */
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "com.ganera.core.tramite.SqlCapturadoInspector")
@Import({IaDePruebaConfig.class, EnvioWhatsappFalso.Config.class})
class RespuestaWhatsappEndToEndTest {

    private static final Instant AHORA = IaDePruebaConfig.AHORA;
    private static final String TELEFONO_A = "+34600980001";
    private static final String TELEFONO_B = "+34600980002";
    private static final String MOTIVO_FUERA_DE_VENTANA =
            "Han pasado más de 24 horas desde su último mensaje. WhatsApp no deja escribirle hasta que vuelva a escribir.";

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private AuditoriaRepository auditoriaRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private EnvioWhatsappFalso envio;
    @Autowired
    private RelojAjustable reloj;
    @Autowired
    private RespuestaWhatsappRepository respuestaWhatsappRepository;
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
    private String tokenAna;
    private String tokenBruno;
    private String tokenCarla;
    private Suscripcion suscripcionA;
    private Explotacion explotacionA;
    private Explotacion explotacionB;
    private Contacto contactoA;
    private Contacto contactoB;
    private int secuenciaSid;

    @BeforeEach
    void preparar() {
        envio.reiniciar();
        reloj.fijar(AHORA);
        gestoriaA = gestoriaRepository.save(new Gestoria("Gestoría Respuestas A"));
        gestoriaB = gestoriaRepository.save(new Gestoria("Gestoría Respuestas B"));
        anaA = crearUsuario(gestoriaA, "ana.resp@test.com", "Ana Pérez");
        crearUsuario(gestoriaA, "bruno.resp@test.com", "Bruno Gil");
        crearUsuario(gestoriaB, "carla.resp@test.com", "Carla Ruiz");
        tokenAna = login("ana.resp@test.com");
        tokenBruno = login("bruno.resp@test.com");
        tokenCarla = login("carla.resp@test.com");
        suscripcionA = suscripcion(gestoriaA, EstadoSuscripcion.ACTIVA);
        suscripcion(gestoriaB, EstadoSuscripcion.ACTIVA);
        explotacionA = nuevaExplotacion(gestoriaA, "ES980000000001");
        explotacionB = nuevaExplotacion(gestoriaB, "ES980000000101");
        contactoA = nuevoContacto(gestoriaA, TELEFONO_A, "Juan Ganadero");
        contactoB = nuevoContacto(gestoriaB, TELEFONO_B, "Luis Vaquero");
        SqlCapturadoInspector.limpiar();
    }

    @AfterEach
    void limpiar() {
        LimpiezaAuditoria.borrar(jdbcTemplate);
        jdbcTemplate.update("delete from respuesta_whatsapp");
        jdbcTemplate.update("delete from tramite_evento");
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

    // ------------------------------------------------------------- envio correcto

    @Test
    void unEnvioCorrectoDa201EnviadoYNoTocaElTramite(CapturedOutput salida) throws IOException {
        Tramite tramite = deWhatsapp(gestoriaA, contactoA, EstadoTramite.PENDIENTE_REVISION);
        mensajeDe(gestoriaA, contactoA, tramite, AHORA.minus(Duration.ofHours(2)));
        Map<String, Object> antes = fila(tramite);
        String clave = UUID.randomUUID().toString();
        String texto = "Hola, Juan. Para tramitar la baja necesitamos: el crotal completo.\n— Gestoría Respuestas A";

        ResponseEntity<String> respuesta = responder(tramite.getId(), tokenAna, texto, clave);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(201);
        JsonNode cuerpo = objectMapper.readTree(respuesta.getBody());
        assertThat(cuerpo.get("id").asLong()).isPositive();
        assertThat(cuerpo.get("texto").asText()).isEqualTo(texto);
        assertThat(cuerpo.get("estado").asText()).isEqualTo("ENVIADO");
        assertThat(cuerpo.get("enviadoPorNombre").asText()).isEqualTo("Ana Pérez");
        assertThat(cuerpo.get("enviadoEn").asText()).isEqualTo("2031-03-10T09:00:00Z");
        assertThat(cuerpo.get("codigoError").isNull()).isTrue();
        assertThat(cuerpo.get("motivoError").isNull()).isTrue();
        assertThat(campos(cuerpo)).containsExactlyInAnyOrder(
                "id", "texto", "estado", "enviadoPorNombre", "enviadoEn", "codigoError", "motivoError");
        // Nunca el telefono, el email ni el SID.
        assertThat(respuesta.getBody()).doesNotContain(TELEFONO_A).doesNotContain("ana.resp@test.com")
                .doesNotContain("SMfalso");

        // El falso recibio exactamente el telefono E.164 del contacto y el texto, fuera de transaccion.
        assertThat(envio.llamadas()).containsExactly(new EnvioWhatsappFalso.Llamada(TELEFONO_A, texto, false));

        // La fila guardada.
        Map<String, Object> guardada = respuestaEnBd(cuerpo.get("id").asLong());
        assertThat(guardada).containsEntry("estado", "ENVIADO")
                .containsEntry("telefono_destino", TELEFONO_A)
                .containsEntry("cuerpo", texto)
                .containsEntry("enviado_por", anaA.getId())
                .containsEntry("gestoria_id", gestoriaA.getId())
                .containsEntry("tramite_id", tramite.getId())
                .containsEntry("contacto_id", contactoA.getId())
                .containsEntry("clave_idempotencia", clave);
        assertThat((String) guardada.get("twilio_message_sid")).startsWith("SMfalso");
        assertThat(guardada.get("codigo_error_twilio")).isNull();

        // El tramite no cambia: ni estado ni version.
        assertThat(fila(tramite)).isEqualTo(antes);

        // Auditoria (T3): dos filas en la gestoria del actor, RESPUESTA_WHATSAPP_CREADA (ENVIANDO) y
        // RESPUESTA_WHATSAPP_RESULTADO (ENVIANDO -> ENVIADO), con el usuario del JWT. Ninguna en B.
        long respuestaId = cuerpo.get("id").asLong();
        List<Auditoria> filasA = auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoriaA.getId());
        List<Auditoria> delaRespuesta = filasA.stream()
                .filter(f -> f.getEntidadTipo() == EntidadAuditada.RESPUESTA_WHATSAPP
                        && Long.valueOf(respuestaId).equals(f.getEntidadId()))
                .toList();
        assertThat(delaRespuesta).hasSize(2);
        assertThat(delaRespuesta).allSatisfy(f -> {
            assertThat(f.getActorTipo()).isEqualTo(ActorTipo.USUARIO);
            assertThat(f.getUsuarioId()).isEqualTo(anaA.getId());
        });
        assertThat(delaRespuesta.get(0).getAccion()).isEqualTo(AccionAuditoria.RESPUESTA_WHATSAPP_CREADA);
        assertThat(delaRespuesta.get(0).getDespues()).isEqualTo("{\"estado\":\"ENVIANDO\"}");
        assertThat(delaRespuesta.get(1).getAccion()).isEqualTo(AccionAuditoria.RESPUESTA_WHATSAPP_RESULTADO);
        assertThat(delaRespuesta.get(1).getAntes()).isEqualTo("{\"estado\":\"ENVIANDO\"}");
        assertThat(delaRespuesta.get(1).getDespues()).isEqualTo("{\"estado\":\"ENVIADO\"}");
        // B no tiene ninguna fila de respuesta (su login de setup deja otra cosa, no de este tramite).
        assertThat(auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoriaB.getId()))
                .noneMatch(f -> f.getEntidadTipo() == EntidadAuditada.RESPUESTA_WHATSAPP);

        // El detalle lo muestra.
        JsonNode whatsapp = detalle(tramite.getId(), tokenAna).get("whatsapp");
        assertThat(whatsapp.get("respuestas")).hasSize(1);
        assertThat(whatsapp.get("respuestas").get(0).get("estado").asText()).isEqualTo("ENVIADO");
        assertThat(whatsapp.get("respuestas").get(0).get("texto").asText()).isEqualTo(texto);

        // Los logs: ni el texto ni el telefono.
        assertThat(salida.getAll()).doesNotContain(TELEFONO_A).doesNotContain("el crotal completo")
                .contains("respuestaId=" + cuerpo.get("id").asLong());
    }

    /** D6: un campo de mas en el cuerpo (telefono, contactoId, gestoriaId) se ignora; el destino sale
     * SIEMPRE del contacto del tramite. */
    @Test
    void losCamposDeMasEnElCuerpoSeIgnoranYElDestinoEsElContactoDelTramite() throws IOException {
        Tramite tramite = deWhatsapp(gestoriaA, contactoA, EstadoTramite.APROBADO);
        mensajeDe(gestoriaA, contactoA, tramite, AHORA.minus(Duration.ofHours(1)));
        String cuerpo = "{\"texto\":\"Hola\",\"claveIdempotencia\":\"" + UUID.randomUUID()
                + "\",\"telefono\":\"" + TELEFONO_B + "\",\"contactoId\":" + contactoB.getId()
                + ",\"gestoriaId\":" + gestoriaB.getId() + ",\"tramiteId\":999}";

        ResponseEntity<String> respuesta = postCrudo("/tramites/" + tramite.getId() + "/respuestas-whatsapp", tokenAna, cuerpo);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(201);
        assertThat(envio.llamadas()).extracting(EnvioWhatsappFalso.Llamada::telefono).containsExactly(TELEFONO_A);
        Map<String, Object> guardada = respuestaEnBd(objectMapper.readTree(respuesta.getBody()).get("id").asLong());
        assertThat(guardada).containsEntry("contacto_id", contactoA.getId())
                .containsEntry("gestoria_id", gestoriaA.getId())
                .containsEntry("telefono_destino", TELEFONO_A);
    }

    // ------------------------------------------------------------- idempotencia

    @Test
    void laMismaClaveDosVecesDevuelveLaGuardadaSinVolverALlamar() throws IOException {
        Tramite tramite = deWhatsapp(gestoriaA, contactoA, EstadoTramite.PENDIENTE_REVISION);
        mensajeDe(gestoriaA, contactoA, tramite, AHORA.minus(Duration.ofHours(1)));
        String clave = UUID.randomUUID().toString();

        ResponseEntity<String> primera = responder(tramite.getId(), tokenAna, "Hola", clave);
        ResponseEntity<String> segunda = responder(tramite.getId(), tokenBruno, "Hola", clave);

        assertThat(primera.getStatusCode().value()).isEqualTo(201);
        assertThat(segunda.getStatusCode().value()).isEqualTo(200);
        assertThat(objectMapper.readTree(segunda.getBody())).isEqualTo(objectMapper.readTree(primera.getBody()));
        assertThat(envio.llamadas()).hasSize(1);
        assertThat(cuantasRespuestas()).isEqualTo(1);
        // Auditoria (T3): la repeticion idempotente no escribe fila -> siguen exactamente las 2 del primer
        // envio (RESPUESTA_WHATSAPP_CREADA + RESPUESTA_WHATSAPP_RESULTADO), ninguna de la segunda llamada.
        long respuestaId = objectMapper.readTree(primera.getBody()).get("id").asLong();
        List<Auditoria> delaRespuesta = auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoriaA.getId()).stream()
                .filter(f -> f.getEntidadTipo() == EntidadAuditada.RESPUESTA_WHATSAPP
                        && Long.valueOf(respuestaId).equals(f.getEntidadId()))
                .toList();
        assertThat(delaRespuesta).extracting(Auditoria::getAccion).containsExactly(
                AccionAuditoria.RESPUESTA_WHATSAPP_CREADA, AccionAuditoria.RESPUESTA_WHATSAPP_RESULTADO);
    }

    @Test
    void laMismaClaveConOtroTextoUOtroTramiteEs409SinLlamar() throws IOException {
        Tramite tramite = deWhatsapp(gestoriaA, contactoA, EstadoTramite.PENDIENTE_REVISION);
        Tramite otro = deWhatsapp(gestoriaA, contactoA, EstadoTramite.APROBADO);
        mensajeDe(gestoriaA, contactoA, tramite, AHORA.minus(Duration.ofHours(1)));
        String clave = UUID.randomUUID().toString();
        assertThat(responder(tramite.getId(), tokenAna, "Hola", clave).getStatusCode().value()).isEqualTo(201);

        ResponseEntity<String> otroTexto = responder(tramite.getId(), tokenAna, "Hola otra vez", clave);
        ResponseEntity<String> otroTramite = responder(otro.getId(), tokenAna, "Hola", clave);

        for (ResponseEntity<String> respuesta : List.of(otroTexto, otroTramite)) {
            assertThat(respuesta.getStatusCode().value()).isEqualTo(409);
            assertThat(motivo(respuesta))
                    .isEqualTo("Esta respuesta ya se envió con otro texto. Escribe otra.");
        }
        assertThat(envio.llamadas()).hasSize(1);
        assertThat(cuantasRespuestas()).isEqualTo(1);
    }

    /** Dos peticiones con la misma clave A LA VEZ: el UNIQUE(gestoria_id, clave_idempotencia) corta la
     * carrera y Twilio recibe una sola llamada. */
    @Test
    void dosPeticionesALaVezConLaMismaClaveLlamanUnaSolaVez() throws Exception {
        Tramite tramite = deWhatsapp(gestoriaA, contactoA, EstadoTramite.PENDIENTE_REVISION);
        mensajeDe(gestoriaA, contactoA, tramite, AHORA.minus(Duration.ofHours(1)));
        String clave = UUID.randomUUID().toString();
        envio.esperar(300);

        for (int intento = 0; intento < 3; intento++) {
            String claveIntento = intento == 0 ? clave : UUID.randomUUID().toString();
            CountDownLatch salida = new CountDownLatch(1);
            ExecutorService hilos = Executors.newFixedThreadPool(2);
            try {
                List<Future<ResponseEntity<String>>> futuros = new ArrayList<>();
                for (int i = 0; i < 2; i++) {
                    futuros.add(hilos.submit(() -> {
                        salida.await();
                        return responder(tramite.getId(), tokenAna, "Hola a la vez", claveIntento);
                    }));
                }
                salida.countDown();
                List<Integer> estados = new ArrayList<>();
                List<Long> ids = new ArrayList<>();
                for (Future<ResponseEntity<String>> futuro : futuros) {
                    ResponseEntity<String> respuesta = futuro.get(30, TimeUnit.SECONDS);
                    estados.add(respuesta.getStatusCode().value());
                    ids.add(objectMapper.readTree(respuesta.getBody()).get("id").asLong());
                }
                assertThat(estados).containsExactlyInAnyOrder(201, 200);
                assertThat(ids.get(0)).isEqualTo(ids.get(1));
            } finally {
                hilos.shutdownNow();
            }
        }
        assertThat(envio.llamadas()).hasSize(3);
        assertThat(cuantasRespuestas()).isEqualTo(3);
    }

    // ------------------------------------------------------------- aislamiento

    @Test
    void unTramiteDeOtraGestoriaOInexistenteEs404SinCuerpoNiLlamadas() throws IOException {
        Tramite deB = deWhatsapp(gestoriaB, contactoB, EstadoTramite.PENDIENTE_REVISION);
        mensajeDe(gestoriaB, contactoB, deB, AHORA.minus(Duration.ofHours(1)));

        for (Long id : List.of(deB.getId(), 999999999L)) {
            ResponseEntity<String> respuesta = responder(id, tokenAna, "Hola", UUID.randomUUID().toString());
            assertThat(respuesta.getStatusCode().value()).as("id " + id).isEqualTo(404);
            assertThat(respuesta.getBody()).isNullOrEmpty();
        }
        assertThat(envio.llamadas()).isEmpty();
        assertThat(cuantasRespuestas()).isZero();
        // Y el GET del detalle de B desde A, tambien 404.
        assertThat(get("/tramites/" + deB.getId(), tokenAna).getStatusCode().value()).isEqualTo(404);
    }

    /** La respuesta de A no aparece en el detalle de B, aunque (datos inconsistentes sembrados a mano)
     * la fila apunte a un tramite de B. */
    @Test
    void elDetalleDeBNoMuestraRespuestasDeA() throws IOException {
        Tramite deA = deWhatsapp(gestoriaA, contactoA, EstadoTramite.PENDIENTE_REVISION);
        Tramite deB = deWhatsapp(gestoriaB, contactoB, EstadoTramite.PENDIENTE_REVISION);
        mensajeDe(gestoriaA, contactoA, deA, AHORA.minus(Duration.ofHours(1)));
        mensajeDe(gestoriaB, contactoB, deB, AHORA.minus(Duration.ofHours(1)));
        assertThat(responder(deA.getId(), tokenAna, "Texto secreto de A", UUID.randomUUID().toString())
                .getStatusCode().value()).isEqualTo(201);
        // Fila de A enlazada al tramite de B (inconsistente): el detalle de B no la muestra.
        jdbcTemplate.update("insert into respuesta_whatsapp (gestoria_id, tramite_id, contacto_id, telefono_destino, "
                        + "cuerpo, enviado_por, creado_en, actualizado_en, estado, clave_idempotencia) values "
                        + "(?, ?, ?, ?, 'Otro texto secreto de A', ?, ?, ?, 'ENVIADO', ?)",
                gestoriaA.getId(), deB.getId(), contactoA.getId(), TELEFONO_A, anaA.getId(),
                "2031-03-10 08:00:00", "2031-03-10 08:00:00",
                UUID.randomUUID().toString());

        ResponseEntity<String> detalleB = get("/tramites/" + deB.getId(), tokenCarla);

        assertThat(detalleB.getStatusCode().value()).isEqualTo(200);
        assertThat(objectMapper.readTree(detalleB.getBody()).get("whatsapp").get("respuestas")).isEmpty();
        assertThat(detalleB.getBody()).doesNotContain("secreto").doesNotContain("Ana Pérez");
    }

    // ------------------------------------------------------------- 400, 503

    @Test
    void cada400EsIgualParaPropioAjenoEInexistenteYNoLlama() throws IOException {
        Tramite propio = deWhatsapp(gestoriaA, contactoA, EstadoTramite.PENDIENTE_REVISION);
        Tramite ajeno = deWhatsapp(gestoriaB, contactoB, EstadoTramite.PENDIENTE_REVISION);
        mensajeDe(gestoriaA, contactoA, propio, AHORA.minus(Duration.ofHours(1)));
        String clave = "\"" + UUID.randomUUID() + "\"";
        Map<String, List<String>> casos = new java.util.LinkedHashMap<>();
        casos.put("Falta la clave de la respuesta.", java.util.Arrays.asList(
                null, "{}", "{\"texto\":\"Hola\"}", "{\"texto\":\"Hola\",\"claveIdempotencia\":null}",
                "{\"texto\":\"Hola\",\"claveIdempotencia\":\"\"}",
                "{\"texto\":\"Hola\",\"claveIdempotencia\":\"no-es-un-uuid\"}",
                "{\"texto\":\"Hola\",\"claveIdempotencia\":\"1-1-1-1-1\"}"));
        casos.put("Escribe el texto de la respuesta.", List.of(
                "{\"claveIdempotencia\":" + clave + "}",
                "{\"texto\":null,\"claveIdempotencia\":" + clave + "}",
                "{\"texto\":\"   \\n \",\"claveIdempotencia\":" + clave + "}"));
        casos.put("La respuesta admite como máximo 1600 caracteres.", List.of(
                "{\"texto\":\"" + "a".repeat(1601) + "\",\"claveIdempotencia\":" + clave + "}"));
        casos.put("Completa el hueco ___ antes de enviar.", List.of(
                "{\"texto\":\"Necesitamos: ___\",\"claveIdempotencia\":" + clave + "}"));

        for (Map.Entry<String, List<String>> caso : casos.entrySet()) {
            List<String> cuerpos = new ArrayList<>();
            for (Long id : List.of(propio.getId(), ajeno.getId(), 999999999L)) {
                for (String cuerpo : caso.getValue()) {
                    ResponseEntity<String> respuesta = postCrudo("/tramites/" + id + "/respuestas-whatsapp", tokenAna, cuerpo);
                    assertThat(respuesta.getStatusCode().value()).as(id + " " + cuerpo).isEqualTo(400);
                    cuerpos.add(respuesta.getBody());
                }
            }
            assertThat(cuerpos).containsOnly(cuerpos.get(0));
            assertThat(objectMapper.readTree(cuerpos.get(0)).get("motivo").asText()).isEqualTo(caso.getKey());
        }
        assertThat(envio.llamadas()).isEmpty();
        assertThat(cuantasRespuestas()).isZero();
        // Exactamente 1600 caracteres si se acepta.
        assertThat(responder(propio.getId(), tokenAna, "b".repeat(1600), UUID.randomUUID().toString())
                .getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void sinConfigurarEs503SinCrearNadaNiLlamar() throws IOException {
        Tramite tramite = deWhatsapp(gestoriaA, contactoA, EstadoTramite.PENDIENTE_REVISION);
        mensajeDe(gestoriaA, contactoA, tramite, AHORA.minus(Duration.ofHours(1)));
        Tramite ajeno = deWhatsapp(gestoriaB, contactoB, EstadoTramite.PENDIENTE_REVISION);
        mensajeDe(gestoriaB, contactoB, ajeno, AHORA.minus(Duration.ofHours(1)));
        envio.configurado(false);

        // Revision T1, N4: el mismo 503 para un tramite propio, uno ajeno y uno inexistente (va antes de la BD).
        List<String> cuerpos = new ArrayList<>();
        for (Long id : List.of(tramite.getId(), ajeno.getId(), 999999999L)) {
            ResponseEntity<String> respuesta = responder(id, tokenAna, "Hola", UUID.randomUUID().toString());
            assertThat(respuesta.getStatusCode().value()).as("id " + id).isEqualTo(503);
            assertThat(motivo(respuesta))
                    .isEqualTo("El envío por WhatsApp no está configurado. Ponte en contacto con Ganera.");
            cuerpos.add(respuesta.getBody());
        }
        assertThat(cuerpos).containsOnly(cuerpos.get(0));
        assertThat(envio.llamadas()).isEmpty();
        assertThat(cuantasRespuestas()).isZero();
    }

    // ------------------------------------------------------------- clave repetida antes que los 409 (revision M1)

    /** Reintento con la misma clave cuando la ventana ya se ha cerrado: 200 con la respuesta guardada (el
     * mensaje SI salio), no el 409 de fuera de ventana, y sin volver a llamar a Twilio. */
    @Test
    void laMismaClaveTrasCerrarseLaVentanaDevuelveLaGuardadaYNoEl409() throws IOException {
        Tramite tramite = deWhatsapp(gestoriaA, contactoA, EstadoTramite.PENDIENTE_REVISION);
        mensajeDe(gestoriaA, contactoA, tramite, AHORA.minus(Duration.ofHours(23)));
        String clave = UUID.randomUUID().toString();
        ResponseEntity<String> primera = responder(tramite.getId(), tokenAna, "Hola", clave);
        assertThat(primera.getStatusCode().value()).isEqualTo(201);

        reloj.avanzar(Duration.ofHours(1).plusSeconds(1));
        assertThat(responder(tramite.getId(), tokenAna, "Otra", UUID.randomUUID().toString()).getStatusCode().value())
                .as("una clave nueva ya ve la ventana cerrada").isEqualTo(409);
        ResponseEntity<String> repetida = responder(tramite.getId(), tokenAna, "Hola", clave);

        assertThat(repetida.getStatusCode().value()).isEqualTo(200);
        assertThat(objectMapper.readTree(repetida.getBody())).isEqualTo(objectMapper.readTree(primera.getBody()));
        assertThat(envio.llamadas()).hasSize(1);
        assertThat(cuantasRespuestas()).isEqualTo(1);
    }

    /** Lo mismo si el contacto se da de baja entre el envio y el reintento. */
    @Test
    void laMismaClaveTrasDarDeBajaAlContactoDevuelveLaGuardadaYNoEl409() throws IOException {
        Tramite tramite = deWhatsapp(gestoriaA, contactoA, EstadoTramite.APROBADO);
        mensajeDe(gestoriaA, contactoA, tramite, AHORA.minus(Duration.ofHours(1)));
        String clave = UUID.randomUUID().toString();
        ResponseEntity<String> primera = responder(tramite.getId(), tokenAna, "Hola", clave);
        assertThat(primera.getStatusCode().value()).isEqualTo(201);

        contactoA.setActivo(false);
        contactoRepository.save(contactoA);
        ResponseEntity<String> repetida = responder(tramite.getId(), tokenAna, "Hola", clave);

        assertThat(repetida.getStatusCode().value()).isEqualTo(200);
        assertThat(objectMapper.readTree(repetida.getBody())).isEqualTo(objectMapper.readTree(primera.getBody()));
        assertThat(envio.llamadas()).hasSize(1);
        assertThat(cuantasRespuestas()).isEqualTo(1);
    }

    // ------------------------------------------------------------- contacto de otra gestoria (revision N3)

    /** Datos inconsistentes sembrados a mano: un tramite de A cuyo contacto es de B. No se le escribe
     * (404 sin cuerpo, sin llamada ni fila) y el detalle de A no muestra datos de ese contacto. */
    @Test
    void unTramiteCuyoContactoEsDeOtraGestoriaNoSeResponde() throws IOException {
        Tramite tramite = deWhatsapp(gestoriaA, contactoB, EstadoTramite.PENDIENTE_REVISION);
        mensajeDe(gestoriaA, contactoB, tramite, AHORA.minus(Duration.ofHours(1)));
        mensajeDe(gestoriaB, contactoB, null, AHORA.minus(Duration.ofHours(1)));

        ResponseEntity<String> respuesta = responder(tramite.getId(), tokenAna, "Hola", UUID.randomUUID().toString());

        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
        assertThat(respuesta.getBody()).isNullOrEmpty();
        assertThat(envio.llamadas()).isEmpty();
        assertThat(cuantasRespuestas()).isZero();
        ResponseEntity<String> detalle = get("/tramites/" + tramite.getId(), tokenAna);
        assertThat(detalle.getStatusCode().value()).isEqualTo(200);
        assertThat(objectMapper.readTree(detalle.getBody()).get("whatsapp").isNull()).isTrue();
        assertThat(detalle.getBody()).doesNotContain("Luis Vaquero").doesNotContain("+34******002").doesNotContain(TELEFONO_B);
    }

    // ------------------------------------------------------------- 409 de disponibilidad

    @Test
    void cada409DeDisponibilidadNoLlamaNiGuarda() throws IOException {
        Tramite sinOrigen = nuevoTramite(gestoriaA, contactoA, null, EstadoTramite.PENDIENTE_REVISION);
        Tramite extrayendo = deWhatsapp(gestoriaA, contactoA, EstadoTramite.PENDIENTE_EXTRACCION);
        mensajeDe(gestoriaA, contactoA, extrayendo, AHORA.minus(Duration.ofHours(1)));
        Contacto inactivo = nuevoContacto(gestoriaA, "+34600980003", "Contacto de baja");
        Tramite deInactivo = deWhatsapp(gestoriaA, inactivo, EstadoTramite.PENDIENTE_REVISION);
        mensajeDe(gestoriaA, inactivo, deInactivo, AHORA.minus(Duration.ofHours(1)));
        inactivo.setActivo(false);
        contactoRepository.save(inactivo);

        Map<Tramite, String> casos = new java.util.LinkedHashMap<>();
        casos.put(sinOrigen, "Este trámite no llegó por WhatsApp; no hay a quién responder.");
        casos.put(extrayendo, "En este estado no se puede responder por WhatsApp.");
        casos.put(deInactivo, "El contacto está dado de baja; no se le puede escribir.");
        for (Map.Entry<Tramite, String> caso : casos.entrySet()) {
            Map<String, Object> antes = fila(caso.getKey());
            ResponseEntity<String> respuesta = responder(caso.getKey().getId(), tokenAna, "Hola", UUID.randomUUID().toString());
            assertThat(respuesta.getStatusCode().value()).as(caso.getValue()).isEqualTo(409);
            assertThat(motivo(respuesta)).isEqualTo(caso.getValue());
            assertThat(fila(caso.getKey())).isEqualTo(antes);
        }
        assertThat(envio.llamadas()).isEmpty();
        assertThat(cuantasRespuestas()).isZero();
        // El detalle de un tramite que no es de WhatsApp no tiene bloque whatsapp.
        assertThat(detalle(sinOrigen.getId(), tokenAna).get("whatsapp").isNull()).isTrue();
        assertThat(detalle(deInactivo.getId(), tokenAna).get("whatsapp").get("contactoActivo").asBoolean()).isFalse();
    }

    @Test
    void laVentanaEstaAbiertaA24HorasMenosUnSegundoYCerradaA24HorasMasUnSegundo() throws IOException {
        Tramite tramite = deWhatsapp(gestoriaA, contactoA, EstadoTramite.APROBADO);
        Instant ultimo = AHORA.minus(Duration.ofDays(3));
        mensajeDe(gestoriaA, contactoA, tramite, ultimo);
        // Un mensaje anterior del mismo contacto (otro tramite) no cuenta: vale el ultimo.
        mensajeDe(gestoriaA, contactoA, null, ultimo.minus(Duration.ofHours(5)));

        reloj.fijar(ultimo.plus(Duration.ofHours(24)).plusSeconds(1));
        ResponseEntity<String> cerrada = responder(tramite.getId(), tokenAna, "Hola", UUID.randomUUID().toString());
        assertThat(cerrada.getStatusCode().value()).isEqualTo(409);
        assertThat(motivo(cerrada)).isEqualTo(MOTIVO_FUERA_DE_VENTANA);
        JsonNode bloqueCerrada = detalle(tramite.getId(), tokenAna).get("whatsapp");
        assertThat(bloqueCerrada.get("motivoNoDisponible").asText()).isEqualTo(MOTIVO_FUERA_DE_VENTANA);
        assertThat(bloqueCerrada.get("ventanaAbiertaHasta").asText()).isEqualTo(ultimo.plus(Duration.ofHours(24)).toString());
        assertThat(envio.llamadas()).isEmpty();

        reloj.fijar(ultimo.plus(Duration.ofHours(24)).minusSeconds(1));
        assertThat(detalle(tramite.getId(), tokenAna).get("whatsapp").get("motivoNoDisponible").isNull()).isTrue();
        ResponseEntity<String> abierta = responder(tramite.getId(), tokenAna, "Hola", UUID.randomUUID().toString());
        assertThat(abierta.getStatusCode().value()).isEqualTo(201);
        assertThat(envio.llamadas()).hasSize(1);
    }

    /** Un MensajeCampo de la gestoria B (sembrado a mano) con el mismo contacto_id de A no abre la
     * ventana en A: la consulta lleva el gestoriaId. */
    @Test
    void unMensajeDeOtraGestoriaConElMismoContactoNoAbreLaVentana() throws IOException {
        Tramite tramite = deWhatsapp(gestoriaA, contactoA, EstadoTramite.PENDIENTE_REVISION);
        mensajeDe(gestoriaA, contactoA, tramite, AHORA.minus(Duration.ofDays(2)));
        mensajeDe(gestoriaB, contactoA, null, AHORA.minus(Duration.ofMinutes(5)));

        ResponseEntity<String> respuesta = responder(tramite.getId(), tokenAna, "Hola", UUID.randomUUID().toString());

        assertThat(respuesta.getStatusCode().value()).isEqualTo(409);
        assertThat(motivo(respuesta)).isEqualTo(MOTIVO_FUERA_DE_VENTANA);
        assertThat(envio.llamadas()).isEmpty();
        assertThat(detalle(tramite.getId(), tokenAna).get("whatsapp").get("ventanaAbiertaHasta").asText())
                .isEqualTo(AHORA.minus(Duration.ofDays(2)).plus(Duration.ofHours(24)).toString());
    }

    // ------------------------------------------------------------- fallos de Twilio

    @Test
    void unRechazoDeTwilioEs201FallidoConSuMotivoYUnoSinConfirmarEs201SinConfirmar() throws IOException {
        Tramite tramite = deWhatsapp(gestoriaA, contactoA, EstadoTramite.RECHAZADO);
        mensajeDe(gestoriaA, contactoA, tramite, AHORA.minus(Duration.ofHours(1)));
        Map<String, Object> antes = fila(tramite);

        envio.devolver(new ResultadoEnvioWhatsapp.Rechazado(63016));
        ResponseEntity<String> fallido = responder(tramite.getId(), tokenAna, "Hola", UUID.randomUUID().toString());
        envio.devolver(new ResultadoEnvioWhatsapp.SinConfirmar());
        ResponseEntity<String> sinConfirmar = responder(tramite.getId(), tokenAna, "Hola 2", UUID.randomUUID().toString());

        assertThat(fallido.getStatusCode().value()).isEqualTo(201);
        JsonNode cuerpoFallido = objectMapper.readTree(fallido.getBody());
        assertThat(cuerpoFallido.get("estado").asText()).isEqualTo("FALLIDO");
        assertThat(cuerpoFallido.get("codigoError").asInt()).isEqualTo(63016);
        assertThat(cuerpoFallido.get("motivoError").asText()).isEqualTo(MOTIVO_FUERA_DE_VENTANA);
        assertThat(respuestaEnBd(cuerpoFallido.get("id").asLong()))
                .containsEntry("estado", "FALLIDO").containsEntry("codigo_error_twilio", 63016);
        assertThat(respuestaEnBd(cuerpoFallido.get("id").asLong()).get("twilio_message_sid")).isNull();

        assertThat(sinConfirmar.getStatusCode().value()).isEqualTo(201);
        JsonNode cuerpoSinConfirmar = objectMapper.readTree(sinConfirmar.getBody());
        assertThat(cuerpoSinConfirmar.get("estado").asText()).isEqualTo("SIN_CONFIRMAR");
        assertThat(cuerpoSinConfirmar.get("codigoError").isNull()).isTrue();
        assertThat(cuerpoSinConfirmar.get("motivoError").asText())
                .isEqualTo("No sabemos si ha llegado. Compruébalo antes de volver a enviarlo.");
        assertThat(fila(tramite)).isEqualTo(antes);
    }

    /** Una fila que se quedo en ENVIANDO (el proceso se cayo entre los pasos) se muestra como
     * SIN_CONFIRMAR pasados 2 minutos, sin reescribirla en la BD. */
    @Test
    void unaRespuestaEnviandoDeHaceMasDeDosMinutosSeMuestraSinConfirmar() throws IOException {
        Tramite tramite = deWhatsapp(gestoriaA, contactoA, EstadoTramite.PENDIENTE_REVISION);
        mensajeDe(gestoriaA, contactoA, tramite, AHORA.minus(Duration.ofHours(1)));
        // Sembradas con la entidad (Instant), como las guarda la aplicacion.
        enviando(tramite, "Atascada", AHORA.minus(Duration.ofMinutes(2)).minusSeconds(1));
        enviando(tramite, "En curso", AHORA.minus(Duration.ofMinutes(2)));

        JsonNode respuestas = detalle(tramite.getId(), tokenAna).get("whatsapp").get("respuestas");

        assertThat(respuestas).hasSize(2);
        assertThat(respuestas.get(0).get("texto").asText()).isEqualTo("Atascada");
        assertThat(respuestas.get(0).get("estado").asText()).isEqualTo("SIN_CONFIRMAR");
        assertThat(respuestas.get(0).get("motivoError").asText())
                .isEqualTo("No sabemos si ha llegado. Compruébalo antes de volver a enviarlo.");
        assertThat(respuestas.get(1).get("estado").asText()).isEqualTo("ENVIANDO");
        assertThat(jdbcTemplate.queryForList("select estado from respuesta_whatsapp", String.class))
                .containsOnly("ENVIANDO");
    }

    // ------------------------------------------------------------- suscripcion

    @Test
    void unaGestoriaSuspendidaPuedeResponderPeroNoAprobar() throws IOException {
        suscripcionA.setEstado(EstadoSuscripcion.SUSPENDIDA);
        suscripcionRepository.save(suscripcionA);
        Tramite tramite = deWhatsapp(gestoriaA, contactoA, EstadoTramite.PENDIENTE_REVISION);
        tramite.setExplotacion(explotacionA);
        tramite.setTipoTramite(TipoTramite.BAJA_MUERTE);
        tramite = tramiteRepository.save(tramite);
        mensajeDe(gestoriaA, contactoA, tramite, AHORA.minus(Duration.ofHours(1)));

        assertThat(responder(tramite.getId(), tokenAna, "Hola", UUID.randomUUID().toString())
                .getStatusCode().value()).isEqualTo(201);
        ResponseEntity<String> aprobar = postCrudo("/tramites/" + tramite.getId() + "/aprobar", tokenAna,
                "{\"version\":" + versionEnBd(tramite) + "}");
        assertThat(aprobar.getStatusCode().value()).isEqualTo(403);
        assertThat(envio.llamadas()).hasSize(1);
    }

    // ------------------------------------------------------------- bloque whatsapp del detalle

    @Test
    void elDetalleLlevaElBloqueWhatsappConSusCamposYLasRespuestasEnOrden() throws IOException {
        Tramite tramite = deWhatsapp(gestoriaA, contactoA, EstadoTramite.PENDIENTE_REVISION);
        Instant ultimo = AHORA.minus(Duration.ofHours(3));
        mensajeDe(gestoriaA, contactoA, tramite, ultimo);
        responder(tramite.getId(), tokenAna, "Primera", UUID.randomUUID().toString());
        reloj.avanzar(Duration.ofMinutes(1));
        responder(tramite.getId(), tokenBruno, "Segunda", UUID.randomUUID().toString());
        reloj.avanzar(Duration.ofMinutes(1));
        responder(tramite.getId(), tokenAna, "Tercera", UUID.randomUUID().toString());
        SqlCapturadoInspector.limpiar();

        JsonNode cuerpo = detalle(tramite.getId(), tokenAna);

        JsonNode whatsapp = cuerpo.get("whatsapp");
        assertThat(campos(whatsapp)).containsExactlyInAnyOrder("destinatarioNombre", "telefonoEnmascarado",
                "contactoActivo", "ventanaAbiertaHasta", "firmaGestoria", "motivoNoDisponible", "respuestas");
        assertThat(whatsapp.get("destinatarioNombre").asText()).isEqualTo("Juan Ganadero");
        assertThat(whatsapp.get("telefonoEnmascarado").asText()).isEqualTo("+34******001");
        assertThat(whatsapp.get("contactoActivo").asBoolean()).isTrue();
        assertThat(whatsapp.get("ventanaAbiertaHasta").asText()).isEqualTo(ultimo.plus(Duration.ofHours(24)).toString());
        assertThat(whatsapp.get("firmaGestoria").asText()).isEqualTo("Gestoría Respuestas A");
        assertThat(whatsapp.get("motivoNoDisponible").isNull()).isTrue();
        assertThat(whatsapp.get("respuestas")).extracting(r -> r.get("texto").asText())
                .containsExactly("Primera", "Segunda", "Tercera");
        assertThat(whatsapp.get("respuestas")).extracting(r -> r.get("enviadoPorNombre").asText())
                .containsExactly("Ana Pérez", "Bruno Gil", "Ana Pérez");
        assertThat(whatsapp.get("respuestas").get(1).get("enviadoEn").asText()).isEqualTo("2031-03-10T09:01:00Z");
        assertThat(cuerpo.toString()).doesNotContain(TELEFONO_A);

        // Sin N+1: una sola consulta a respuesta_whatsapp, con el usuario en el mismo join.
        List<String> sql = SqlCapturadoInspector.capturado().stream().map(s -> s.toLowerCase(Locale.ROOT)).toList();
        assertThat(sql).filteredOn(s -> s.contains("from respuesta_whatsapp ")).hasSize(1)
                .allSatisfy(s -> assertThat(s).contains("join usuario "));
        assertThat(sql).filteredOn(s -> s.contains("from usuario ")).isEmpty();

        // El resto del contrato del detalle no cambia.
        assertThat(campos(cuerpo)).containsExactlyInAnyOrder("id", "tipoTramite", "estado", "motivoError",
                "explotacionId", "explotacionCodigoRega", "explotacionNombre", "ganaderoNombre", "ganaderoNif",
                "mensajeOriginal", "crotales", "version", "origen", "estadoExtraccion", "crotalesDescartados",
                "hechoEnOvzEn", "hechoEnOvzPorNombre", "whatsapp", "motivoRechazo", "descartable", "historialDescarte");
    }

    @Test
    void laRespuestaDelPatchTambienLlevaElBloqueWhatsapp() throws IOException {
        Tramite tramite = deWhatsapp(gestoriaA, contactoA, EstadoTramite.PENDIENTE_REVISION);
        mensajeDe(gestoriaA, contactoA, tramite, AHORA.minus(Duration.ofHours(1)));
        responder(tramite.getId(), tokenAna, "Antes del patch", UUID.randomUUID().toString());

        ResponseEntity<String> patch = patchCrudo(tramite.getId(), tokenAna,
                Map.of("version", versionEnBd(tramite), "tipoTramite", "BAJA_MUERTE"));

        assertThat(patch.getStatusCode().value()).isEqualTo(200);
        JsonNode whatsapp = objectMapper.readTree(patch.getBody()).get("whatsapp");
        assertThat(whatsapp.get("respuestas")).extracting(r -> r.get("texto").asText()).containsExactly("Antes del patch");
        assertThat(whatsapp.get("destinatarioNombre").asText()).isEqualTo("Juan Ganadero");
    }

    // ------------------------------------------------------------- SIN_TRAMITE (D9)

    /** Un descartado no se responde: 409 con el motivo de D9, sin fila ni llamada a Twilio; el detalle lo
     * dice en whatsapp.motivoNoDisponible. */
    @Test
    void responderAUnDescartadoEs409SinCrearFilaNiLlamar() throws IOException {
        Tramite tramite = sinTramiteDeWhatsapp();
        mensajeDe(gestoriaA, contactoA, tramite, AHORA.minus(Duration.ofHours(1)));
        ResponseEntity<String> descartar = postCrudo("/tramites/" + tramite.getId() + "/descartar", tokenAna,
                "{\"version\":" + versionEnBd(tramite) + "}");
        assertThat(descartar.getStatusCode().value()).isEqualTo(200);
        Map<String, Object> antes = fila(tramite);

        ResponseEntity<String> respuesta = responder(tramite.getId(), tokenAna, "Hola", UUID.randomUUID().toString());

        assertThat(respuesta.getStatusCode().value()).isEqualTo(409);
        assertThat(motivo(respuesta)).isEqualTo("Este mensaje se descartó como sin trámite; no se responde desde Ganera.");
        assertThat(envio.llamadas()).isEmpty();
        assertThat(cuantasRespuestas()).isZero();
        assertThat(fila(tramite)).isEqualTo(antes);
        assertThat(detalle(tramite.getId(), tokenAna).get("whatsapp").get("motivoNoDisponible").asText())
                .isEqualTo("Este mensaje se descartó como sin trámite; no se responde desde Ganera.");
    }

    /** Tras deshacer el descarte se vuelve a poder responder. */
    @Test
    void trasDeshacerElDescarteSeVuelveAPoderResponder() throws IOException {
        Tramite tramite = sinTramiteDeWhatsapp();
        mensajeDe(gestoriaA, contactoA, tramite, AHORA.minus(Duration.ofHours(1)));
        long v0 = versionEnBd(tramite);
        assertThat(postCrudo("/tramites/" + tramite.getId() + "/descartar", tokenAna, "{\"version\":" + v0 + "}")
                .getStatusCode().value()).isEqualTo(200);
        ResponseEntity<String> deshacer = postCrudo("/tramites/" + tramite.getId() + "/deshacer-descarte", tokenBruno,
                "{\"version\":" + (v0 + 1) + "}");
        assertThat(deshacer.getStatusCode().value()).isEqualTo(200);
        assertThat(objectMapper.readTree(deshacer.getBody()).get("whatsapp").get("motivoNoDisponible").isNull()).isTrue();

        ResponseEntity<String> respuesta = responder(tramite.getId(), tokenAna, "¿Abrimos el lunes? Sí.",
                UUID.randomUUID().toString());

        assertThat(respuesta.getStatusCode().value()).isEqualTo(201);
        assertThat(envio.llamadas()).hasSize(1);
    }

    @Test
    void elListadoDeTramitesNoCambia() throws IOException {
        Tramite tramite = deWhatsapp(gestoriaA, contactoA, EstadoTramite.PENDIENTE_REVISION);
        mensajeDe(gestoriaA, contactoA, tramite, AHORA.minus(Duration.ofHours(1)));
        responder(tramite.getId(), tokenAna, "Hola", UUID.randomUUID().toString());

        JsonNode fila = objectMapper.readTree(get("/tramites", tokenAna).getBody()).get("content").get(0);

        assertThat(fila.has("whatsapp")).isFalse();
        assertThat(fila.has("respuestas")).isFalse();
    }

    // ------------------------------------------------------------- utilidades

    private static List<String> campos(JsonNode nodo) {
        List<String> nombres = new ArrayList<>();
        nodo.fieldNames().forEachRemaining(nombres::add);
        return nombres;
    }

    private ResponseEntity<String> responder(Long tramiteId, String token, String texto, String clave) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("texto", texto);
        cuerpo.put("claveIdempotencia", clave);
        try {
            return postCrudo("/tramites/" + tramiteId + "/respuestas-whatsapp", token,
                    objectMapper.writeValueAsString(cuerpo));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonNode detalle(Long tramiteId, String token) throws IOException {
        ResponseEntity<String> respuesta = get("/tramites/" + tramiteId, token);
        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        return objectMapper.readTree(respuesta.getBody());
    }

    /** Una respuesta que se quedo en ENVIANDO, creada en {@code creadaEn}. */
    private void enviando(Tramite tramite, String texto, Instant creadaEn) {
        RespuestaWhatsapp respuesta = new RespuestaWhatsapp();
        respuesta.setGestoria(tramite.getGestoria());
        respuesta.setTramite(tramite);
        respuesta.setContacto(tramite.getContacto());
        respuesta.setTelefonoDestino(tramite.getContacto().getTelefono());
        respuesta.setCuerpo(texto);
        respuesta.setEnviadoPor(anaA);
        respuesta.setCreatedAt(creadaEn);
        respuesta.setActualizadoEn(creadaEn);
        respuesta.setEstado(EstadoRespuestaWhatsapp.ENVIANDO);
        respuesta.setClaveIdempotencia(UUID.randomUUID().toString());
        respuestaWhatsappRepository.save(respuesta);
    }

    private int cuantasRespuestas() {
        return jdbcTemplate.queryForObject("select count(*) from respuesta_whatsapp", Integer.class);
    }

    private Map<String, Object> respuestaEnBd(long id) {
        Map<String, Object> fila = new HashMap<>();
        jdbcTemplate.queryForMap("select * from respuesta_whatsapp where id = ?", id)
                .forEach((columna, valor) -> fila.put(columna.toLowerCase(Locale.ROOT), valor));
        return fila;
    }

    /** Estado y version del Tramite leidos directamente de la BD. */
    private Map<String, Object> fila(Tramite tramite) {
        Map<String, Object> fila = new HashMap<>();
        jdbcTemplate.queryForMap("select estado, version, tipo_tramite from tramite where id = ?", tramite.getId())
                .forEach((columna, valor) -> fila.put(columna.toLowerCase(Locale.ROOT), valor));
        return fila;
    }

    private long versionEnBd(Tramite tramite) {
        return jdbcTemplate.queryForObject("select version from tramite where id = ?", Long.class, tramite.getId());
    }

    private String motivo(ResponseEntity<String> respuesta) throws IOException {
        return objectMapper.readTree(respuesta.getBody()).get("motivo").asText();
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

    /** Un mensaje que la IA vio como SIN_TRAMITE: pendiente, sin tipo ni crotales (descartable). */
    private Tramite sinTramiteDeWhatsapp() {
        Tramite tramite = deWhatsapp(gestoriaA, contactoA, EstadoTramite.PENDIENTE_REVISION);
        tramite.setEstadoExtraccion(EstadoExtraccion.SIN_TRAMITE);
        return tramiteRepository.save(tramite);
    }

    private Tramite deWhatsapp(Gestoria gestoria, Contacto contacto, EstadoTramite estado) {
        return nuevoTramite(gestoria, contacto, OrigenTramite.WHATSAPP, estado);
    }

    private Tramite nuevoTramite(Gestoria gestoria, Contacto contacto, OrigenTramite origen, EstadoTramite estado) {
        Tramite tramite = new Tramite();
        tramite.setGestoria(gestoria);
        tramite.setContacto(contacto);
        tramite.setEstado(estado);
        tramite.setOrigen(origen);
        if (origen != null) {
            tramite.setEstadoExtraccion(EstadoExtraccion.COMPLETADA);
        }
        return tramiteRepository.save(tramite);
    }

    private void mensajeDe(Gestoria gestoria, Contacto contacto, Tramite tramite, Instant cuando) {
        MensajeCampo mensaje = new MensajeCampo();
        mensaje.setMessageSid("SM-resp-" + (++secuenciaSid) + "-" + UUID.randomUUID());
        mensaje.setTelefonoOrigen(contacto.getTelefono());
        mensaje.setCuerpo("Mensaje del ganadero");
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

    private Contacto nuevoContacto(Gestoria gestoria, String telefono, String nombre) {
        Contacto contacto = new Contacto();
        contacto.setTelefono(telefono);
        contacto.setNombre(nombre);
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
