package com.ganera.core.whatsapp;

import com.ganera.core.contacto.Contacto;
import com.ganera.core.contacto.ContactoRepository;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.gestoria.Usuario;
import com.ganera.core.gestoria.UsuarioRepository;
import com.ganera.core.tramite.EstadoTramite;
import com.ganera.core.tramite.OrigenTramite;
import com.ganera.core.tramite.Tramite;
import com.ganera.core.tramite.TramiteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Callback de estado de Twilio por HTTP real (B2 reducido, T4): {@code POST /webhooks/twilio/whatsapp/estado}
 * con DOS gestorias. Twilio nunca se llama: el test hace de Twilio y firma con
 * {@link FirmaTwilioDePrueba} contra la URL CONFIGURADA, que a proposito no es la del servidor de test.
 */
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "twilio.auth-token=" + TwilioEstadoWebhookEndToEndTest.AUTH_TOKEN,
        "ganera.twilio.status-callback-url=" + TwilioEstadoWebhookEndToEndTest.URL_CALLBACK})
class TwilioEstadoWebhookEndToEndTest {

    static final String AUTH_TOKEN = "token-de-prueba-inventado-estado";
    static final String URL_CALLBACK = "https://ganera.example.test/webhooks/twilio/whatsapp/estado";
    private static final String RUTA = "/webhooks/twilio/whatsapp/estado";
    private static final String TELEFONO_A = "+34600970001";
    private static final String TELEFONO_B = "+34600970002";
    private static final String SID_A = "SMaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa01";
    private static final String SID_B = "SMbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb02";
    private static final Instant CREADA = Instant.parse("2020-03-10T09:00:00Z");

    @Autowired
    private TestRestTemplate restTemplate;
    @LocalServerPort
    private int puerto;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private RespuestaWhatsappRepository respuestaWhatsappRepository;
    @Autowired
    private TramiteRepository tramiteRepository;
    @Autowired
    private ContactoRepository contactoRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private GestoriaRepository gestoriaRepository;

    private Gestoria gestoriaA;
    private Gestoria gestoriaB;
    private Usuario usuarioA;
    private Usuario usuarioB;
    private Contacto contactoA;
    private Contacto contactoB;
    private Tramite tramiteA;
    private Tramite tramiteB;

    @BeforeEach
    void preparar() {
        gestoriaA = gestoriaRepository.save(new Gestoria("Gestoría Callback A"));
        gestoriaB = gestoriaRepository.save(new Gestoria("Gestoría Callback B"));
        usuarioA = crearUsuario(gestoriaA, "ana.callback@test.com");
        usuarioB = crearUsuario(gestoriaB, "bea.callback@test.com");
        contactoA = nuevoContacto(gestoriaA, TELEFONO_A);
        contactoB = nuevoContacto(gestoriaB, TELEFONO_B);
        tramiteA = nuevoTramite(gestoriaA, contactoA);
        tramiteB = nuevoTramite(gestoriaB, contactoB);
    }

    @AfterEach
    void limpiar() {
        jdbcTemplate.update("delete from respuesta_whatsapp");
        tramiteRepository.deleteAll();
        contactoRepository.deleteAll();
        usuarioRepository.deleteAll();
        gestoriaRepository.deleteAll();
    }

    // ------------------------------------------------------------- transiciones

    @Test
    void deliveredPasaAEntregadoYActualizaLaFecha() {
        long id = respuestaA(EstadoRespuestaWhatsapp.ENVIADO, SID_A, null);

        ResponseEntity<String> respuesta = enviarFirmado(callback(SID_A, "delivered"));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(estado(id)).isEqualTo("ENTREGADO");
        assertThat(actualizadaEn(id)).isAfter(CREADA);
    }

    @Test
    void readPasaALeidoYFailedConCodigoAFallido() {
        long leida = respuestaA(EstadoRespuestaWhatsapp.ENTREGADO, SID_A, null);
        long fallida = respuestaB(EstadoRespuestaWhatsapp.ENVIADO, SID_B, null);

        assertThat(enviarFirmado(callback(SID_A, "read")).getStatusCode().value()).isEqualTo(200);
        Map<String, String> fallo = callback(SID_B, "undelivered");
        fallo.put("ErrorCode", "63016");
        assertThat(enviarFirmado(fallo).getStatusCode().value()).isEqualTo(200);

        assertThat(estado(leida)).isEqualTo("LEIDO");
        assertThat(estado(fallida)).isEqualTo("FALLIDO");
        assertThat(codigo(fallida)).isEqualTo(63016);
    }

    @Test
    void sentConfirmaUnaRespuestaSinConfirmarConSidGuardado() {
        long id = respuestaA(EstadoRespuestaWhatsapp.SIN_CONFIRMAR, SID_A, null);

        assertThat(enviarFirmado(callback(SID_A, "sent")).getStatusCode().value()).isEqualTo(200);

        assertThat(estado(id)).isEqualTo("ENVIADO");
    }

    @Test
    void deliveredTrasFallidoLoCorrigeYVaciaElCodigo() {
        long id = respuestaA(EstadoRespuestaWhatsapp.FALLIDO, SID_A, 63016);

        assertThat(enviarFirmado(callback(SID_A, "delivered")).getStatusCode().value()).isEqualTo(200);

        assertThat(estado(id)).isEqualTo("ENTREGADO");
        assertThat(codigo(id)).isNull();
    }

    @Test
    void unErrorCodeNoNumericoOLargoSeIgnoraPeroElEstadoSeAplica() {
        long noNumerico = respuestaA(EstadoRespuestaWhatsapp.ENVIADO, SID_A, null);
        long largo = respuestaB(EstadoRespuestaWhatsapp.ENVIADO, SID_B, null);

        Map<String, String> uno = callback(SID_A, "failed");
        uno.put("ErrorCode", "abc' or 1=1");
        Map<String, String> otro = callback(SID_B, "failed");
        otro.put("ErrorCode", "12345678901");
        assertThat(enviarFirmado(uno).getStatusCode().value()).isEqualTo(200);
        assertThat(enviarFirmado(otro).getStatusCode().value()).isEqualTo(200);

        assertThat(estado(noNumerico)).isEqualTo("FALLIDO");
        assertThat(codigo(noNumerico)).isNull();
        assertThat(estado(largo)).isEqualTo("FALLIDO");
        assertThat(codigo(largo)).isNull();
    }

    @Test
    void unRepetidoODesordenadoNuncaRetrocede() {
        long id = respuestaA(EstadoRespuestaWhatsapp.ENVIADO, SID_A, null);

        enviarFirmado(callback(SID_A, "read"));
        Instant trasLeer = actualizadaEn(id);
        // Llegan tarde los anteriores y se repite el ultimo.
        enviarFirmado(callback(SID_A, "delivered"));
        enviarFirmado(callback(SID_A, "sent"));
        Map<String, String> fallo = callback(SID_A, "failed");
        fallo.put("ErrorCode", "30008");
        enviarFirmado(fallo);
        enviarFirmado(callback(SID_A, "read"));

        assertThat(estado(id)).isEqualTo("LEIDO");
        assertThat(codigo(id)).isNull();
        assertThat(actualizadaEn(id)).isEqualTo(trasLeer);
    }

    @Test
    void unFallidoNoPisaUnEntregado() {
        long id = respuestaA(EstadoRespuestaWhatsapp.ENTREGADO, SID_A, null);
        Map<String, String> fallo = callback(SID_A, "failed");
        fallo.put("ErrorCode", "30008");

        assertThat(enviarFirmado(fallo).getStatusCode().value()).isEqualTo(200);

        assertThat(estado(id)).isEqualTo("ENTREGADO");
        assertThat(codigo(id)).isNull();
    }

    @Test
    void dosCallbacksALaVezAcabanEnElMasAvanzado() throws Exception {
        for (int vuelta = 0; vuelta < 5; vuelta++) {
            jdbcTemplate.update("delete from respuesta_whatsapp");
            long id = respuestaA(EstadoRespuestaWhatsapp.ENVIADO, SID_A, null);
            ExecutorService hilos = Executors.newFixedThreadPool(2);
            try {
                CountDownLatch salida = new CountDownLatch(1);
                Future<Integer> leido = hilos.submit(() -> {
                    salida.await();
                    return enviarFirmado(callback(SID_A, "read")).getStatusCode().value();
                });
                Future<Integer> entregado = hilos.submit(() -> {
                    salida.await();
                    return enviarFirmado(callback(SID_A, "delivered")).getStatusCode().value();
                });
                salida.countDown();
                assertThat(leido.get(30, TimeUnit.SECONDS)).isEqualTo(200);
                assertThat(entregado.get(30, TimeUnit.SECONDS)).isEqualTo(200);
            } finally {
                hilos.shutdownNow();
            }
            assertThat(estado(id)).isEqualTo("LEIDO");
        }
    }

    @Test
    void otroMessageStatusDa200SinCambios() {
        long id = respuestaA(EstadoRespuestaWhatsapp.ENVIADO, SID_A, null);

        assertThat(enviarFirmado(callback(SID_A, "receiving")).getStatusCode().value()).isEqualTo(200);

        assertThat(estado(id)).isEqualTo("ENVIADO");
        assertThat(actualizadaEn(id)).isEqualTo(CREADA);
    }

    // ------------------------------------------------------------- aislamiento y SID

    /** El SID de A cambia solo A aunque el resto de la peticion traiga datos de B. */
    @Test
    void soloSeLocalizaPorMessageSidAunqueLaPeticionTraigaDatosDeB() {
        long a = respuestaA(EstadoRespuestaWhatsapp.ENVIADO, SID_A, null);
        long b = respuestaB(EstadoRespuestaWhatsapp.ENVIADO, SID_B, null);
        Map<String, String> parametros = callback(SID_A, "delivered");
        parametros.put("SmsSid", SID_B);
        parametros.put("SmsMessageSid", SID_B);
        parametros.put("AccountSid", SID_B);
        parametros.put("MessagingServiceSid", SID_B);
        parametros.put("To", "whatsapp:" + TELEFONO_B);
        parametros.put("GestoriaId", String.valueOf(gestoriaB.getId()));
        parametros.put("RespuestaId", String.valueOf(b));

        assertThat(enviarFirmado(parametros).getStatusCode().value()).isEqualTo(200);

        assertThat(estado(a)).isEqualTo("ENTREGADO");
        assertThat(estado(b)).isEqualTo("ENVIADO");
        assertThat(actualizadaEn(b)).isEqualTo(CREADA);
    }

    @Test
    void unSidDesconocidoDa200SinCambiosYLoRegistraSoloConElSid(CapturedOutput salida) {
        long a = respuestaA(EstadoRespuestaWhatsapp.ENVIADO, SID_A, null);

        ResponseEntity<String> respuesta = enviarFirmado(callback("SMdesconocido0000000000000000000099", "delivered"));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(estado(a)).isEqualTo("ENVIADO");
        assertThat(salida.getOut()).contains("INFO").contains("SMdesconocido0000000000000000000099");
    }

    /** Twilio puede avisar antes de que el paso 3 del envio guarde el SID: 200 y nada cambia. */
    @Test
    void unCallbackAntesDeGuardarseElSidDa200SinCambios() {
        long enviando = respuestaA(EstadoRespuestaWhatsapp.ENVIANDO, null, null);

        assertThat(enviarFirmado(callback(SID_A, "sent")).getStatusCode().value()).isEqualTo(200);

        assertThat(estado(enviando)).isEqualTo("ENVIANDO");
        assertThat(actualizadaEn(enviando)).isEqualTo(CREADA);
    }

    // ------------------------------------------------------------- 403 / 400 / 413

    @Test
    void sinFirmaFirmaBasuraOtraUrlUOtroTokenEs403SinCambios() {
        long id = respuestaA(EstadoRespuestaWhatsapp.ENVIADO, SID_A, null);
        Map<String, String> parametros = callback(SID_A, "delivered");

        assertThat(enviar(parametros, null).getStatusCode().value()).isEqualTo(403);
        assertThat(enviar(parametros, "basura").getStatusCode().value()).isEqualTo(403);
        assertThat(enviar(parametros, FirmaTwilioDePrueba.calcular(AUTH_TOKEN, restTemplate.getRootUri() + RUTA,
                parametros)).getStatusCode().value()).isEqualTo(403);
        assertThat(enviar(parametros, FirmaTwilioDePrueba.calcular("otro-token", URL_CALLBACK, parametros))
                .getStatusCode().value()).isEqualTo(403);
        // La firma del webhook de ENTRADA (otra URL) tampoco vale aqui.
        assertThat(enviar(parametros, FirmaTwilioDePrueba.calcular(AUTH_TOKEN,
                "https://ganera.example.test/webhooks/twilio/whatsapp", parametros)).getStatusCode().value())
                .isEqualTo(403);

        assertThat(estado(id)).isEqualTo("ENVIADO");
    }

    @Test
    void unParametroAlteradoTrasFirmarEs403() {
        long id = respuestaA(EstadoRespuestaWhatsapp.ENVIADO, SID_A, null);
        Map<String, String> parametros = callback(SID_A, "failed");
        String firma = FirmaTwilioDePrueba.calcular(AUTH_TOKEN, URL_CALLBACK, parametros);
        parametros.put("MessageStatus", "read");

        assertThat(enviar(parametros, firma).getStatusCode().value()).isEqualTo(403);
        assertThat(estado(id)).isEqualTo("ENVIADO");
    }

    @Test
    void sinFirmaYSinMessageSidEs403YNo400() {
        Map<String, String> parametros = callback(SID_A, "delivered");
        parametros.remove("MessageSid");

        assertThat(enviar(parametros, null).getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void firmaValidaSinMessageSidOSinMessageStatusEs400() {
        long id = respuestaA(EstadoRespuestaWhatsapp.ENVIADO, SID_A, null);
        Map<String, String> sinSid = callback(SID_A, "delivered");
        sinSid.remove("MessageSid");
        sinSid.put("SmsSid", SID_A);
        Map<String, String> sinStatus = callback(SID_A, "delivered");
        sinStatus.remove("MessageStatus");
        sinStatus.put("SmsStatus", "delivered");
        Map<String, String> enBlanco = callback(" ", "delivered");

        assertThat(enviarFirmado(sinSid).getStatusCode().value()).isEqualTo(400);
        assertThat(enviarFirmado(sinStatus).getStatusCode().value()).isEqualTo(400);
        assertThat(enviarFirmado(enBlanco).getStatusCode().value()).isEqualTo(400);
        assertThat(estado(id)).isEqualTo("ENVIADO");
    }

    /** Un cuerpo de mas de 8 KB con firma VALIDA: 413 y nada cambia (sin el tope, se aplicaria). */
    @Test
    void unCuerpoDeMasDe8KbConLongitudDeclaradaEs413SinCambios() throws Exception {
        long id = respuestaA(EstadoRespuestaWhatsapp.ENVIADO, SID_A, null);
        Map<String, String> parametros = callback(SID_A, "delivered");
        parametros.put("Relleno", "x".repeat(9000));
        byte[] cuerpo = formulario(parametros);

        HttpResponse<String> respuesta = postCrudo(cuerpo, firma(parametros), false);

        assertThat(respuesta.statusCode()).isEqualTo(413);
        assertThat(estado(id)).isEqualTo("ENVIADO");
    }

    @Test
    void unCuerpoDeMasDe8KbSinLongitudDeclaradaEs413SinCambios() throws Exception {
        long id = respuestaA(EstadoRespuestaWhatsapp.ENVIADO, SID_A, null);
        Map<String, String> parametros = callback(SID_A, "delivered");
        parametros.put("Relleno", "x".repeat(9000));
        byte[] cuerpo = formulario(parametros);

        HttpResponse<String> respuesta = postCrudo(cuerpo, firma(parametros), true);

        assertThat(respuesta.statusCode()).isEqualTo(413);
        assertThat(estado(id)).isEqualTo("ENVIADO");
    }

    /** El tope va antes que la firma: un cuerpo enorme sin firma tambien es 413, no 403. */
    @Test
    void elTopeVaAntesQueLaFirma() throws Exception {
        Map<String, String> parametros = callback(SID_A, "delivered");
        parametros.put("Relleno", "x".repeat(9000));

        assertThat(postCrudo(formulario(parametros), null, false).statusCode()).isEqualTo(413);
        assertThat(postCrudo(formulario(parametros), null, true).statusCode()).isEqualTo(413);
    }

    /** Un cuerpo pequeno sin longitud declarada (chunked) se lee con tope y funciona igual. */
    @Test
    void unCuerpoPequenoSinLongitudDeclaradaSeAplica() throws Exception {
        long id = respuestaA(EstadoRespuestaWhatsapp.ENVIADO, SID_A, null);
        Map<String, String> parametros = callback(SID_A, "read");
        parametros.put("Body", "Texto con tildes y ñ & = %");

        HttpResponse<String> respuesta = postCrudo(formulario(parametros), firma(parametros), true);

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(estado(id)).isEqualTo("LEIDO");
    }

    /** Justo en el limite (8192 bytes) se acepta. */
    @Test
    void unCuerpoDeExactamente8KbSeAcepta() throws Exception {
        long id = respuestaA(EstadoRespuestaWhatsapp.ENVIADO, SID_A, null);
        Map<String, String> parametros = callback(SID_A, "delivered");
        parametros.put("Relleno", "");
        int sinRelleno = formulario(parametros).length;
        parametros.put("Relleno", "x".repeat(8192 - sinRelleno));
        byte[] cuerpo = formulario(parametros);
        assertThat(cuerpo).hasSize(8192);

        assertThat(postCrudo(cuerpo, firma(parametros), false).statusCode()).isEqualTo(200);
        assertThat(estado(id)).isEqualTo("ENTREGADO");
    }

    // ------------------------------------------------------------- logs

    @Test
    void losLogsNoLlevanTelefonosNiTexto(CapturedOutput salida) {
        long id = respuestaA(EstadoRespuestaWhatsapp.ENVIADO, SID_A, null);
        Map<String, String> parametros = callback(SID_A, "undelivered");
        parametros.put("ErrorCode", "63016");
        parametros.put("Body", "texto-secreto-que-no-debe-salir");
        parametros.put("ChannelToAddress", "+34600970001");

        assertThat(enviarFirmado(parametros).getStatusCode().value()).isEqualTo(200);
        enviarFirmado(callback("SMdesconocido0000000000000000000077", "delivered"));
        enviar(parametros, "basura");

        assertThat(estado(id)).isEqualTo("FALLIDO");
        String log = salida.getOut();
        assertThat(log).contains(SID_A).contains("FALLIDO").contains("63016");
        assertThat(log).doesNotContain("600970001").doesNotContain("600970002").doesNotContain("14155238886")
                .doesNotContain("texto-secreto-que-no-debe-salir");
    }

    // ------------------------------------------------------------- ayudas

    /** Parametros como los que manda Twilio en un callback de estado de WhatsApp (con To/From/Body). */
    private static Map<String, String> callback(String sid, String status) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("MessageSid", sid);
        p.put("SmsSid", sid);
        p.put("MessageStatus", status);
        p.put("SmsStatus", status);
        p.put("AccountSid", "AC00000000000000000000000000000000");
        p.put("From", "whatsapp:+14155238886");
        p.put("To", "whatsapp:" + TELEFONO_A);
        p.put("ApiVersion", "2010-04-01");
        p.put("ChannelPrefix", "whatsapp");
        return p;
    }

    private static String firma(Map<String, String> parametros) {
        return FirmaTwilioDePrueba.calcular(AUTH_TOKEN, URL_CALLBACK, parametros);
    }

    private ResponseEntity<String> enviarFirmado(Map<String, String> parametros) {
        return enviar(parametros, firma(parametros));
    }

    private ResponseEntity<String> enviar(Map<String, String> parametros, String firma) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        if (firma != null) {
            headers.set("X-Twilio-Signature", firma);
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        parametros.forEach(form::add);
        return restTemplate.postForEntity(RUTA, new HttpEntity<>(form, headers), String.class);
    }

    private static byte[] formulario(Map<String, String> parametros) {
        return parametros.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"))
                .getBytes(StandardCharsets.UTF_8);
    }

    /** POST con java.net.http: con {@code sinLongitud} el cuerpo va chunked, sin Content-Length. */
    private HttpResponse<String> postCrudo(byte[] cuerpo, String firma, boolean sinLongitud) throws Exception {
        HttpRequest.BodyPublisher publicador = sinLongitud
                ? HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(cuerpo))
                : HttpRequest.BodyPublishers.ofByteArray(cuerpo);
        HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + RUTA))
                .version(HttpClient.Version.HTTP_1_1)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(publicador);
        if (firma != null) {
            peticion.header("X-Twilio-Signature", firma);
        }
        try (HttpClient cliente = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()) {
            return cliente.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private long respuestaA(EstadoRespuestaWhatsapp estado, String sid, Integer codigo) {
        return nuevaRespuesta(tramiteA, usuarioA, estado, sid, codigo);
    }

    private long respuestaB(EstadoRespuestaWhatsapp estado, String sid, Integer codigo) {
        return nuevaRespuesta(tramiteB, usuarioB, estado, sid, codigo);
    }

    private long nuevaRespuesta(Tramite tramite, Usuario usuario, EstadoRespuestaWhatsapp estado, String sid,
                                Integer codigo) {
        RespuestaWhatsapp respuesta = new RespuestaWhatsapp();
        respuesta.setGestoria(tramite.getGestoria());
        respuesta.setTramite(tramite);
        respuesta.setContacto(tramite.getContacto());
        respuesta.setTelefonoDestino(tramite.getContacto().getTelefono());
        respuesta.setCuerpo("Respuesta de prueba");
        respuesta.setEnviadoPor(usuario);
        respuesta.setCreatedAt(CREADA);
        respuesta.setActualizadoEn(CREADA);
        respuesta.setEstado(estado);
        respuesta.setTwilioMessageSid(sid);
        respuesta.setCodigoErrorTwilio(codigo);
        respuesta.setClaveIdempotencia(UUID.randomUUID().toString());
        return respuestaWhatsappRepository.save(respuesta).getId();
    }

    private Map<String, Object> fila(long id) {
        Map<String, Object> fila = new HashMap<>();
        jdbcTemplate.queryForMap("select * from respuesta_whatsapp where id = ?", id)
                .forEach((columna, valor) -> fila.put(columna.toLowerCase(Locale.ROOT), valor));
        return fila;
    }

    private String estado(long id) {
        return (String) fila(id).get("estado");
    }

    private Integer codigo(long id) {
        return (Integer) fila(id).get("codigo_error_twilio");
    }

    private Instant actualizadaEn(long id) {
        List<Timestamp> valores = jdbcTemplate.queryForList(
                "select actualizado_en from respuesta_whatsapp where id = ?", Timestamp.class, id);
        return valores.get(0).toInstant();
    }

    private Tramite nuevoTramite(Gestoria gestoria, Contacto contacto) {
        Tramite tramite = new Tramite();
        tramite.setGestoria(gestoria);
        tramite.setContacto(contacto);
        tramite.setEstado(EstadoTramite.PENDIENTE_REVISION);
        tramite.setOrigen(OrigenTramite.WHATSAPP);
        return tramiteRepository.save(tramite);
    }

    private Contacto nuevoContacto(Gestoria gestoria, String telefono) {
        Contacto contacto = new Contacto();
        contacto.setTelefono(telefono);
        contacto.setNombre("Contacto " + telefono);
        contacto.setGestoria(gestoria);
        return contactoRepository.save(contacto);
    }

    private Usuario crearUsuario(Gestoria gestoria, String email) {
        Usuario usuario = new Usuario();
        usuario.setGestoria(gestoria);
        usuario.setEmail(email);
        usuario.setPasswordHash("no-se-usa");
        usuario.setNombre("Usuario " + email);
        usuario.setActivo(true);
        return usuarioRepository.save(usuario);
    }
}
