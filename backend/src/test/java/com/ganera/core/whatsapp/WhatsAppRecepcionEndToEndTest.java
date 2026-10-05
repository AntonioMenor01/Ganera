package com.ganera.core.whatsapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganera.core.auth.LoginRequest;
import com.ganera.core.auth.LoginResponse;
import com.ganera.core.contacto.Contacto;
import com.ganera.core.contacto.ContactoExplotacion;
import com.ganera.core.contacto.ContactoExplotacionRepository;
import com.ganera.core.contacto.ContactoRepository;
import com.ganera.core.contacto.RolContacto;
import com.ganera.core.explotacion.Explotacion;
import com.ganera.core.explotacion.ExplotacionRepository;
import com.ganera.core.ganadero.Ganadero;
import com.ganera.core.ganadero.GanaderoRepository;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.gestoria.Usuario;
import com.ganera.core.gestoria.UsuarioRepository;
import com.ganera.core.tramite.TramiteCrotalRepository;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Recepcion de WhatsApp por HTTP real (T1): POST de formulario firmado como lo firma Twilio, con DOS
 * Gestorias con datos propios. El estado se lee con JdbcTemplate (fuera de cualquier transaccion
 * del servidor) y el aislamiento se comprueba por los endpoints autenticados de cada Gestoria.
 */
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "twilio.auth-token=" + WhatsAppRecepcionEndToEndTest.AUTH_TOKEN,
        "ganera.twilio.webhook-url=" + WhatsAppRecepcionEndToEndTest.URL_PUBLICA})
class WhatsAppRecepcionEndToEndTest {

    static final String AUTH_TOKEN = "token-recepcion-inventado";
    static final String URL_PUBLICA = "https://ganera.example.test/webhooks/twilio/whatsapp";
    private static final String RUTA = "/webhooks/twilio/whatsapp";
    static final String TWIML_ACUSE = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<Response><Message>Recibido, tu gestoría lo revisará.</Message></Response>";
    static final String TWIML_VACIO = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response/>";

    private static final String TEL_A_UNA = "+34600950001";
    private static final String TEL_A_SIN_ENLACES = "+34600950002";
    private static final String TEL_A_INACTIVO = "+34600950003";
    private static final String TEL_B = "+34600950101";
    private static final String TEL_DESCONOCIDO = "+34600950999";

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private MensajeCampoRepository mensajeCampoRepository;
    @Autowired
    private TramiteCrotalRepository tramiteCrotalRepository;
    @Autowired
    private TramiteRepository tramiteRepository;
    @Autowired
    private ContactoExplotacionRepository contactoExplotacionRepository;
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
    private String tokenA;
    private String tokenB;
    private Explotacion explotacionA;
    private Explotacion explotacionB;
    private Contacto contactoAUna;
    private Contacto contactoASinEnlaces;

    @BeforeEach
    void preparar() {
        gestoriaA = gestoriaRepository.save(new Gestoria("Gestoria WhatsApp A"));
        gestoriaB = gestoriaRepository.save(new Gestoria("Gestoria WhatsApp B"));
        crearUsuario(gestoriaA, "whatsappA@test.com");
        crearUsuario(gestoriaB, "whatsappB@test.com");
        tokenA = login("whatsappA@test.com");
        tokenB = login("whatsappB@test.com");

        explotacionA = nuevaExplotacion(gestoriaA, "ES950000000001");
        explotacionB = nuevaExplotacion(gestoriaB, "ES950000000101");
        Explotacion explotacionB2 = nuevaExplotacion(gestoriaB, "ES950000000102");

        contactoAUna = nuevoContacto(gestoriaA, TEL_A_UNA, true);
        enlazar(gestoriaA, contactoAUna, explotacionA);
        // Enlaces inconsistentes (se saltan la decision 15): no deben contar para D2.
        enlazar(gestoriaA, contactoAUna, explotacionB);
        enlazar(gestoriaB, contactoAUna, explotacionB2);

        contactoASinEnlaces = nuevoContacto(gestoriaA, TEL_A_SIN_ENLACES, true);
        nuevoContacto(gestoriaA, TEL_A_INACTIVO, false);
        Contacto contactoB = nuevoContacto(gestoriaB, TEL_B, true);
        enlazar(gestoriaB, contactoB, explotacionB);
    }

    @AfterEach
    void limpiar() {
        mensajeCampoRepository.deleteAll();
        tramiteCrotalRepository.deleteAll();
        tramiteRepository.deleteAll();
        contactoExplotacionRepository.deleteAll();
        contactoRepository.deleteAll();
        explotacionRepository.deleteAll();
        ganaderoRepository.deleteAll();
        usuarioRepository.deleteAll();
        gestoriaRepository.deleteAll();
    }

    // ----------------------------------------------------------- tramite creado

    @Test
    void elMensajeDelContactoDeACreaElTramiteSoloEnAYBNoLoVe() throws IOException {
        ResponseEntity<String> respuesta = enviar(parametros("SM-e2e-a", TEL_A_UNA, "baja del 1234"));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(respuesta.getHeaders().getContentType().toString()).isEqualTo("text/xml;charset=UTF-8");
        assertThat(respuesta.getBody()).isEqualTo(TWIML_ACUSE);

        Map<String, Object> tramite = unicoTramite();
        long tramiteId = ((Number) tramite.get("id")).longValue();
        assertThat(((Number) tramite.get("gestoria_id")).longValue()).isEqualTo(gestoriaA.getId());
        assertThat(((Number) tramite.get("contacto_id")).longValue()).isEqualTo(contactoAUna.getId());
        assertThat(((Number) tramite.get("explotacion_id")).longValue()).isEqualTo(explotacionA.getId());
        assertThat(tramite.get("origen")).isEqualTo("WHATSAPP");
        assertThat(tramite.get("estado")).isEqualTo("PENDIENTE_EXTRACCION");
        assertThat(tramite.get("estado_extraccion")).isEqualTo("PENDIENTE");
        assertThat(tramite.get("proximo_intento_extraccion")).isNotNull();
        assertThat(tramite.get("tipo_tramite")).isNull();

        Map<String, Object> mensaje = unicoMensaje();
        assertThat(mensaje.get("resultado")).isEqualTo("TRAMITE_CREADO");
        assertThat(mensaje.get("telefono_origen")).isEqualTo(TEL_A_UNA);
        assertThat(((Number) mensaje.get("gestoria_id")).longValue()).isEqualTo(gestoriaA.getId());
        assertThat(((Number) mensaje.get("tramite_id")).longValue()).isEqualTo(tramiteId);

        assertThat(idsDelListado(tokenA)).containsExactly(tramiteId);
        assertThat(idsDelListado(tokenB)).doesNotContain(tramiteId);
        assertThat(get("/tramites/" + tramiteId, tokenA).getStatusCode().value()).isEqualTo(200);
        ResponseEntity<String> deB = get("/tramites/" + tramiteId, tokenB);
        assertThat(deB.getStatusCode().value()).isEqualTo(404);
        assertThat(deB.getBody()).isNullOrEmpty();
    }

    /** B tiene explotaciones y enlaces; el contacto de A no tiene ninguno: la explotacion queda null. */
    @Test
    void unaExplotacionOEnlaceDeBNuncaSeAsignaAlTramiteDeA() {
        ResponseEntity<String> respuesta = enviar(parametros("SM-e2e-sin", TEL_A_SIN_ENLACES, "alta de dos terneros"));

        assertThat(respuesta.getBody()).isEqualTo(TWIML_ACUSE);
        Map<String, Object> tramite = unicoTramite();
        assertThat(((Number) tramite.get("gestoria_id")).longValue()).isEqualTo(gestoriaA.getId());
        assertThat(((Number) tramite.get("contacto_id")).longValue()).isEqualTo(contactoASinEnlaces.getId());
        assertThat(tramite.get("explotacion_id")).isNull();
    }

    @Test
    void unMensajeSinTextoTambienAcusaYQuedaEnRevisionSinTexto() {
        Map<String, String> parametros = parametros("SM-e2e-foto", TEL_A_UNA, null);
        parametros.put("NumMedia", "1");
        parametros.put("MediaUrl0", "https://api.twilio.com/2010-04-01/Accounts/AC0/Messages/MM0/Media/ME0");
        parametros.put("MediaContentType0", "image/jpeg");

        ResponseEntity<String> respuesta = enviar(parametros);

        assertThat(respuesta.getBody()).isEqualTo(TWIML_ACUSE);
        Map<String, Object> tramite = unicoTramite();
        assertThat(tramite.get("estado")).isEqualTo("PENDIENTE_REVISION");
        assertThat(tramite.get("estado_extraccion")).isEqualTo("SIN_TEXTO");
        Map<String, Object> mensaje = unicoMensaje();
        assertThat(((Number) mensaje.get("num_media")).intValue()).isEqualTo(1);
        assertThat(mensaje.get("cuerpo")).isEqualTo("");
    }

    // ------------------------------------------------- sin tramite, sin respuesta

    @Test
    void unNumeroDesconocidoRecibeRespuestaVaciaYNoCreaNadaEnNingunaGestoria() throws IOException {
        ResponseEntity<String> respuesta = enviar(parametros("SM-e2e-desc", TEL_DESCONOCIDO, "hola"));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(respuesta.getHeaders().getContentType().toString()).isEqualTo("text/xml;charset=UTF-8");
        assertThat(respuesta.getBody()).isEqualTo(TWIML_VACIO);
        assertThat(contarTramites()).isZero();
        assertThat(idsDelListado(tokenA)).isEmpty();
        assertThat(idsDelListado(tokenB)).isEmpty();
        Map<String, Object> mensaje = unicoMensaje();
        assertThat(mensaje.get("resultado")).isEqualTo("NUMERO_DESCONOCIDO");
        assertThat(mensaje.get("gestoria_id")).isNull();
        assertThat(mensaje.get("contacto_id")).isNull();
        assertThat(mensaje.get("tramite_id")).isNull();
    }

    /** m2: un From no normalizable de 40 caracteres no revienta la columna: 200 vacio y desconocido. */
    @Test
    void unFromNoNormalizableDe40CaracteresEsNumeroDesconocidoYNoDa500() {
        Map<String, String> parametros = parametros("SM-e2e-from-largo", TEL_A_UNA, "hola");
        String from = "whatsapp:canal-raro-0123456789abcdefghij";
        assertThat(from).hasSize(40);
        parametros.put("From", from);

        ResponseEntity<String> respuesta = enviar(parametros);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(respuesta.getBody()).isEqualTo(TWIML_VACIO);
        assertThat(contarTramites()).isZero();
        Map<String, Object> mensaje = unicoMensaje();
        assertThat(mensaje.get("resultado")).isEqualTo("NUMERO_DESCONOCIDO");
        assertThat(mensaje.get("telefono_origen")).isEqualTo(from.substring(0, 30));
    }

    @Test
    void unContactoInactivoRecibeRespuestaVaciaYNoCreaTramite() {
        ResponseEntity<String> respuesta = enviar(parametros("SM-e2e-inactivo", TEL_A_INACTIVO, "baja del 1234"));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(respuesta.getBody()).isEqualTo(TWIML_VACIO);
        assertThat(contarTramites()).isZero();
        Map<String, Object> mensaje = unicoMensaje();
        assertThat(mensaje.get("resultado")).isEqualTo("CONTACTO_INACTIVO");
        assertThat(((Number) mensaje.get("gestoria_id")).longValue()).isEqualTo(gestoriaA.getId());
    }

    // ------------------------------------------------------------ duplicados

    @Test
    void unaEntregaRepetidaRecibeRespuestaVaciaYNoDuplicaNiTramiteNiMensaje() {
        Map<String, String> parametros = parametros("SM-e2e-dup", TEL_A_UNA, "baja del 1234");

        assertThat(enviar(parametros).getBody()).isEqualTo(TWIML_ACUSE);
        ResponseEntity<String> segunda = enviar(parametros);

        assertThat(segunda.getStatusCode().value()).isEqualTo(200);
        assertThat(segunda.getBody()).isEqualTo(TWIML_VACIO);
        assertThat(contarTramites()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from mensaje_campo", Long.class)).isEqualTo(1);
    }

    /**
     * Entregas simultaneas del mismo MessageSid: varias pueden pasar a la vez la comprobacion previa,
     * y entonces la que pierde choca con el UNIQUE(message_sid). Ninguna puede acabar en 500 ni crear
     * un segundo Tramite. (El camino de la violacion se prueba de forma determinista en
     * WhatsAppDuplicadoPorViolacionEndToEndTest; aqui se comprueba que, con concurrencia real, el
     * resultado es el mismo.)
     */
    @Test
    void entregasConcurrentesDelMismoMessageSidCreanUnSoloTramiteYNingun500() throws Exception {
        int rondas = 5;
        int hilos = 6;
        ExecutorService ejecutor = Executors.newFixedThreadPool(hilos);
        try {
            for (int ronda = 0; ronda < rondas; ronda++) {
                Map<String, String> parametros = parametros("SM-e2e-carrera-" + ronda, TEL_A_UNA, "baja del 1234");
                CountDownLatch salida = new CountDownLatch(1);
                List<Future<ResponseEntity<String>>> futuros = new ArrayList<>();
                for (int i = 0; i < hilos; i++) {
                    futuros.add(ejecutor.submit(() -> {
                        salida.await();
                        return enviar(parametros);
                    }));
                }
                salida.countDown();
                List<String> cuerpos = new ArrayList<>();
                for (Future<ResponseEntity<String>> futuro : futuros) {
                    ResponseEntity<String> respuesta = futuro.get(30, TimeUnit.SECONDS);
                    assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
                    cuerpos.add(respuesta.getBody());
                }
                assertThat(cuerpos).containsOnly(TWIML_ACUSE, TWIML_VACIO);
                assertThat(cuerpos).filteredOn(TWIML_ACUSE::equals).hasSize(1);
            }
        } finally {
            ejecutor.shutdownNow();
        }
        assertThat(contarTramites()).isEqualTo(rondas);
        assertThat(jdbcTemplate.queryForObject("select count(*) from mensaje_campo", Long.class)).isEqualTo(rondas);
    }

    // ------------------------------------------------------------ logs

    @Test
    void niElTextoNiElTelefonoCompletoAparecenEnLosLogs(CapturedOutput salida) {
        String texto = "Baja del 4321 texto-que-no-debe-salir-en-el-log";

        enviar(parametros("SM-e2e-log-1", TEL_A_UNA, texto));
        enviar(parametros("SM-e2e-log-2", TEL_DESCONOCIDO, texto));
        enviar(parametros("SM-e2e-log-3", TEL_A_INACTIVO, texto));
        enviar(parametros("SM-e2e-log-1", TEL_A_UNA, texto)); // duplicado

        assertThat(contarTramites()).isEqualTo(1);
        assertThat(salida.getAll())
                .doesNotContain("texto-que-no-debe-salir-en-el-log")
                .doesNotContain("600950001").doesNotContain("600950999").doesNotContain("600950003")
                .doesNotContain(AUTH_TOKEN);
    }

    // ------------------------------------------------------------ ayudas

    private static Map<String, String> parametros(String messageSid, String telefono, String cuerpo) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("MessageSid", messageSid);
        p.put("SmsMessageSid", messageSid);
        p.put("AccountSid", "AC00000000000000000000000000000000");
        p.put("From", "whatsapp:" + telefono);
        p.put("To", "whatsapp:+14155238886");
        if (cuerpo != null) {
            p.put("Body", cuerpo);
        }
        p.put("NumMedia", "0");
        p.put("ProfileName", "Nombre De Perfil");
        p.put("WaId", telefono.substring(1));
        return p;
    }

    private ResponseEntity<String> enviar(Map<String, String> parametros) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("X-Twilio-Signature", FirmaTwilioDePrueba.calcular(AUTH_TOKEN, URL_PUBLICA, parametros));
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        parametros.forEach(form::add);
        return restTemplate.postForEntity(RUTA, new HttpEntity<>(form, headers), String.class);
    }

    private long contarTramites() {
        return jdbcTemplate.queryForObject("select count(*) from tramite", Long.class);
    }

    private Map<String, Object> unicoTramite() {
        List<Map<String, Object>> filas = jdbcTemplate.queryForList("select * from tramite");
        assertThat(filas).hasSize(1);
        return minusculas(filas.get(0));
    }

    private Map<String, Object> unicoMensaje() {
        List<Map<String, Object>> filas = jdbcTemplate.queryForList("select * from mensaje_campo");
        assertThat(filas).hasSize(1);
        return minusculas(filas.get(0));
    }

    private static Map<String, Object> minusculas(Map<String, Object> fila) {
        Map<String, Object> resultado = new LinkedHashMap<>();
        fila.forEach((k, v) -> resultado.put(k.toLowerCase(), v));
        return resultado;
    }

    private List<Long> idsDelListado(String token) throws IOException {
        ResponseEntity<String> respuesta = get("/tramites?size=100", token);
        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        JsonNode contenido = objectMapper.readTree(respuesta.getBody()).get("content");
        List<Long> ids = new ArrayList<>();
        contenido.forEach(n -> ids.add(n.get("id").asLong()));
        return ids;
    }

    private ResponseEntity<String> get(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
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

    private Contacto nuevoContacto(Gestoria gestoria, String telefono, boolean activo) {
        Contacto contacto = new Contacto();
        contacto.setGestoria(gestoria);
        contacto.setTelefono(telefono);
        contacto.setNombre("Contacto WhatsApp E2E");
        contacto.setActivo(activo);
        return contactoRepository.save(contacto);
    }

    private void enlazar(Gestoria gestoriaDelEnlace, Contacto contacto, Explotacion explotacion) {
        ContactoExplotacion enlace = new ContactoExplotacion();
        enlace.setGestoria(gestoriaDelEnlace);
        enlace.setContacto(contacto);
        enlace.setExplotacion(explotacion);
        enlace.setRol(RolContacto.TITULAR);
        contactoExplotacionRepository.save(enlace);
    }

    private void crearUsuario(Gestoria gestoria, String email) {
        Usuario usuario = new Usuario();
        usuario.setGestoria(gestoria);
        usuario.setEmail(email);
        usuario.setPasswordHash(passwordEncoder.encode("password123"));
        usuario.setNombre("Usuario WhatsApp E2E");
        usuario.setActivo(true);
        usuarioRepository.save(usuario);
    }

    private String login(String email) {
        ResponseEntity<LoginResponse> respuesta = restTemplate.postForEntity(
                "/auth/login", new LoginRequest(email, "password123"), LoginResponse.class);
        return respuesta.getBody().token();
    }
}
