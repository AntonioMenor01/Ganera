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
import com.ganera.core.explotacion.Animal;
import com.ganera.core.facturacion.EstadoSuscripcion;
import com.ganera.core.facturacion.Suscripcion;
import com.ganera.core.facturacion.SuscripcionRepository;
import com.ganera.core.explotacion.AnimalRepository;
import com.ganera.core.explotacion.Explotacion;
import com.ganera.core.explotacion.ExplotacionRepository;
import com.ganera.core.ganadero.Ganadero;
import com.ganera.core.ganadero.GanaderoRepository;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.gestoria.Usuario;
import com.ganera.core.gestoria.UsuarioRepository;
import com.ganera.core.tramite.EstadoTramite;
import com.ganera.core.tramite.ExtraccionFallidaException;
import com.ganera.core.tramite.ExtraccionTramiteService;
import com.ganera.core.tramite.IaDePruebaConfig;
import com.ganera.core.tramite.IaDePruebaConfig.IaProgramable;
import com.ganera.core.tramite.IaDePruebaConfig.RelojAjustable;
import com.ganera.core.tramite.TipoTramite;
import com.ganera.core.tramite.Tramite;
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
import org.springframework.context.annotation.Import;
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
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Recepcion + extraccion de punta a punta (B1, T2): POST de WhatsApp firmado como Twilio, despues
 * una pasada del procesamiento (llamada directa al servicio; el planificador esta desactivado en
 * los tests) y lectura por los endpoints autenticados de las dos Gestorias. La IA es falsa.
 */
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "twilio.auth-token=" + WhatsAppExtraccionEndToEndTest.AUTH_TOKEN,
        "ganera.twilio.webhook-url=" + WhatsAppExtraccionEndToEndTest.URL_PUBLICA})
@Import(IaDePruebaConfig.class)
class WhatsAppExtraccionEndToEndTest {

    static final String AUTH_TOKEN = "token-extraccion-inventado";
    static final String URL_PUBLICA = "https://ganera.example.test/webhooks/twilio/whatsapp";
    private static final String RUTA = "/webhooks/twilio/whatsapp";
    private static final String TEL_A = "+34600980001";
    private static final String TEL_B = "+34600980101";
    private static final String TEXTO = "ha muerto la vaca acabada en 1234";

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ExtraccionTramiteService extraccionTramiteService;
    @Autowired
    private IaProgramable ia;
    @Autowired
    private RelojAjustable reloj;
    @Autowired
    private MensajeCampoRepository mensajeCampoRepository;
    @Autowired
    private RetencionMensajesService retencionMensajesService;
    @Autowired
    private SuscripcionRepository suscripcionRepository;
    @Autowired
    private TramiteCrotalRepository tramiteCrotalRepository;
    @Autowired
    private TramiteRepository tramiteRepository;
    @Autowired
    private AnimalRepository animalRepository;
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

    private String tokenA;
    private String tokenB;

    @BeforeEach
    void preparar() {
        ia.reiniciar();
        reloj.fijar(IaDePruebaConfig.AHORA);
        Gestoria gestoriaA = gestoriaRepository.save(new Gestoria("Gestoria extraccion E2E A"));
        Gestoria gestoriaB = gestoriaRepository.save(new Gestoria("Gestoria extraccion E2E B"));
        crearUsuario(gestoriaA, "extraccionA@test.com");
        crearUsuario(gestoriaB, "extraccionB@test.com");
        tokenA = login("extraccionA@test.com");
        tokenB = login("extraccionB@test.com");

        Explotacion explotacionA = explotacion(gestoriaA, "ES980000000001");
        Explotacion explotacionB = explotacion(gestoriaB, "ES980000000101");
        animal(gestoriaA, explotacionA, "ES010000001234");
        // Mismos ultimos digitos en B: nunca debe completar ni enlazarse al Tramite de A.
        animal(gestoriaB, explotacionB, "ES020000001234");
        enlazar(gestoriaA, contacto(gestoriaA, TEL_A), explotacionA);
        enlazar(gestoriaB, contacto(gestoriaB, TEL_B), explotacionB);
    }

    @AfterEach
    void limpiar() {
        suscripcionRepository.deleteAll();
        mensajeCampoRepository.deleteAll();
        tramiteCrotalRepository.deleteAll();
        tramiteRepository.deleteAll();
        animalRepository.deleteAll();
        contactoExplotacionRepository.deleteAll();
        contactoRepository.deleteAll();
        explotacionRepository.deleteAll();
        ganaderoRepository.deleteAll();
        usuarioRepository.deleteAll();
        gestoriaRepository.deleteAll();
        ia.reiniciar();
    }

    @Test
    void elMensajeDeAAcabaEnRevisionConTipoYCrotalEnInventarioSoloParaA(CapturedOutput salida) throws IOException {
        // "12" no es un crotal (3 digitos o menos): se descarta y se cuenta (D5).
        ia.devolver(TipoTramite.BAJA_MUERTE, "1234", "12");
        assertThat(enviar(parametros("SM-ext-a", TEL_A, TEXTO)).getStatusCode().value()).isEqualTo(200);

        extraccionTramiteService.procesarPendientes();

        assertThat(ia.textos()).containsExactly(TEXTO);
        JsonNode listado = contenido(get("/tramites?estado=PENDIENTE_REVISION", tokenA));
        assertThat(listado).hasSize(1);
        JsonNode tramite = listado.get(0);
        long tramiteId = tramite.get("id").asLong();
        assertThat(tramite.get("estado").asText()).isEqualTo("PENDIENTE_REVISION");
        assertThat(tramite.get("tipoTramite").asText()).isEqualTo("BAJA_MUERTE");
        assertThat(tramite.get("crotales")).hasSize(1);
        assertThat(tramite.get("crotales").get(0).get("crotal").asText()).isEqualTo("ES010000001234");
        assertThat(tramite.get("crotales").get(0).get("resolucion").asText()).isEqualTo("EN_INVENTARIO");
        assertThat(tramite.get("origen").asText()).isEqualTo("WHATSAPP");
        assertThat(tramite.get("estadoExtraccion").asText()).isEqualTo("COMPLETADA");
        assertThat(tramite.get("crotalesDescartados").asInt()).isEqualTo(1);
        JsonNode detalle = detalle(tramiteId, tokenA);
        assertThat(detalle.get("origen").asText()).isEqualTo("WHATSAPP");
        assertThat(detalle.get("estadoExtraccion").asText()).isEqualTo("COMPLETADA");
        assertThat(detalle.get("crotalesDescartados").asInt()).isEqualTo(1);

        assertThat(contenido(get("/tramites", tokenB))).isEmpty();
        ResponseEntity<String> deB = get("/tramites/" + tramiteId, tokenB);
        assertThat(deB.getStatusCode().value()).isEqualTo(404);
        assertThat(deB.getBody()).isNullOrEmpty();

        Map<String, Object> fila = tramiteEnBd(tramiteId);
        assertThat(fila.get("estado_extraccion")).isEqualTo("COMPLETADA");
        assertThat(fila.get("proximo_intento_extraccion")).isNull();
        assertThat(salida.getAll()).doesNotContain("muerto la vaca").doesNotContain("600980001");
    }

    @Test
    void conLaIaFallandoElMensajeAparecePendienteDeRevisionConExtraccionFallida(CapturedOutput salida) throws IOException {
        ia.lanzar(new ExtraccionFallidaException("el modelo dijo: " + TEXTO));
        enviar(parametros("SM-ext-fallo", TEL_A, TEXTO));

        extraccionTramiteService.procesarPendientes();

        JsonNode listado = contenido(get("/tramites?estado=PENDIENTE_REVISION", tokenA));
        assertThat(listado).hasSize(1);
        long tramiteId = listado.get(0).get("id").asLong();
        assertThat(listado.get(0).get("tipoTramite").isNull()).isTrue();
        assertThat(listado.get(0).get("crotales")).isEmpty();
        assertThat(get("/tramites/" + tramiteId, tokenB).getStatusCode().value()).isEqualTo(404);
        assertThat(listado.get(0).get("origen").asText()).isEqualTo("WHATSAPP");
        assertThat(listado.get(0).get("estadoExtraccion").asText()).isEqualTo("FALLIDA");
        assertThat(listado.get(0).get("crotalesDescartados").asInt()).isZero();
        JsonNode detalle = detalle(tramiteId, tokenA);
        assertThat(detalle.get("mensajeOriginal").asText()).isEqualTo(TEXTO);
        assertThat(detalle.get("origen").asText()).isEqualTo("WHATSAPP");
        assertThat(detalle.get("estadoExtraccion").asText()).isEqualTo("FALLIDA");
        assertThat(detalle.get("crotalesDescartados").asInt()).isZero();
        // El motivo tecnico del fallo de la IA nunca sale por la API (solo a los logs, y sin datos).
        assertThat(detalle.get("motivoError").isNull()).isTrue();
        assertThat(get("/tramites/" + tramiteId, tokenA).getBody()).doesNotContain("el modelo dijo")
                .doesNotContain("ExtraccionFallida");

        Map<String, Object> fila = tramiteEnBd(tramiteId);
        assertThat(fila.get("estado_extraccion")).isEqualTo("FALLIDA");
        assertThat(((Number) fila.get("intentos_extraccion")).intValue()).isEqualTo(1);
        assertThat(((Number) fila.get("version_tras_fallo")).longValue())
                .isEqualTo(((Number) fila.get("version")).longValue());
        assertThat(fila.get("proximo_intento_extraccion")).isNotNull();
        assertThat(salida.getAll()).doesNotContain("muerto la vaca").doesNotContain("600980001")
                .doesNotContain("el modelo dijo");
    }

    @Test
    void unMensajeSinTextoSaleConEstadoDeExtraccionSinTextoYBRecibe404() throws IOException {
        assertThat(enviar(parametros("SM-ext-vacio", TEL_A, "")).getStatusCode().value()).isEqualTo(200);

        JsonNode listado = contenido(get("/tramites", tokenA));
        assertThat(listado).hasSize(1);
        long tramiteId = listado.get(0).get("id").asLong();
        assertThat(listado.get(0).get("origen").asText()).isEqualTo("WHATSAPP");
        assertThat(listado.get(0).get("estadoExtraccion").asText()).isEqualTo("SIN_TEXTO");
        assertThat(listado.get(0).get("crotalesDescartados").asInt()).isZero();
        JsonNode detalle = detalle(tramiteId, tokenA);
        assertThat(detalle.get("origen").asText()).isEqualTo("WHATSAPP");
        assertThat(detalle.get("estadoExtraccion").asText()).isEqualTo("SIN_TEXTO");
        assertThat(detalle.get("crotalesDescartados").asInt()).isZero();
        assertThat(ia.llamadas()).isZero();

        ResponseEntity<String> deB = get("/tramites/" + tramiteId, tokenB);
        assertThat(deB.getStatusCode().value()).isEqualTo(404);
        assertThat(deB.getBody()).isNullOrEmpty();

        // Pasados los 12 meses, un mensaje sin texto no se "vacia": nunca hubo texto que eliminar.
        reloj.fijar(IaDePruebaConfig.AHORA.plus(java.time.Duration.ofDays(367)));
        assertThat(retencionMensajesService.aplicar().vaciados()).isZero();
        assertThat(detalle(tramiteId, tokenA).get("mensajeOriginal").asText()).isEmpty();
    }

    @Test
    void lasRespuestasDePatchAprobarYRechazarLlevanLosTresCampos() throws IOException {
        Gestoria gestoriaA = contactoRepository.findByTelefono(TEL_A).orElseThrow().getGestoria();
        Suscripcion suscripcion = new Suscripcion();
        suscripcion.setGestoria(gestoriaA);
        suscripcion.setEstado(EstadoSuscripcion.ACTIVA);
        suscripcionRepository.save(suscripcion);
        // "12" se descarta (D5): crotalesDescartados = 1 en los dos tramites.
        ia.devolver(TipoTramite.BAJA_MUERTE, "1234", "12");
        enviar(parametros("SM-ext-aprobar", TEL_A, TEXTO));
        enviar(parametros("SM-ext-rechazar", TEL_A, TEXTO));
        extraccionTramiteService.procesarPendientes();
        JsonNode listado = contenido(get("/tramites", tokenA));
        assertThat(listado).hasSize(2);
        long aprobable = listado.get(0).get("id").asLong();
        long rechazable = listado.get(1).get("id").asLong();

        ResponseEntity<String> patch = enviarJson(HttpMethod.PATCH, "/tramites/" + aprobable,
                "{\"version\":" + detalle(aprobable, tokenA).get("version").asLong() + ",\"tipoTramite\":\"BAJA_MUERTE\"}");
        assertThat(patch.getStatusCode().value()).isEqualTo(200);
        tieneLosTresCampos(objectMapper.readTree(patch.getBody()));

        ResponseEntity<String> aprobada = enviarJson(HttpMethod.POST, "/tramites/" + aprobable + "/aprobar",
                "{\"version\":" + objectMapper.readTree(patch.getBody()).get("version").asLong() + "}");
        assertThat(aprobada.getStatusCode().value()).isEqualTo(200);
        assertThat(objectMapper.readTree(aprobada.getBody()).get("estado").asText()).isEqualTo("APROBADO");
        tieneLosTresCampos(objectMapper.readTree(aprobada.getBody()));

        ResponseEntity<String> rechazada = enviarJson(HttpMethod.POST, "/tramites/" + rechazable + "/rechazar",
                "{\"version\":" + detalle(rechazable, tokenA).get("version").asLong() + "}");
        assertThat(rechazada.getStatusCode().value()).isEqualTo(200);
        assertThat(objectMapper.readTree(rechazada.getBody()).get("estado").asText()).isEqualTo("RECHAZADO");
        tieneLosTresCampos(objectMapper.readTree(rechazada.getBody()));
    }

    private static void tieneLosTresCampos(JsonNode respuesta) {
        assertThat(respuesta.get("origen").asText()).isEqualTo("WHATSAPP");
        assertThat(respuesta.get("estadoExtraccion").asText()).isEqualTo("COMPLETADA");
        assertThat(respuesta.get("crotalesDescartados").asInt()).isEqualTo(1);
    }

    private ResponseEntity<String> enviarJson(HttpMethod metodo, String path, String cuerpo) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenA);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(path, metodo, new HttpEntity<>(cuerpo, headers), String.class);
    }

    @Test
    void unTramiteSinOrigenAnteriorABUnoDaNullYCeroEnListadoYDetalle() throws IOException {
        Contacto contactoA = contactoRepository.findByTelefono(TEL_A).orElseThrow();
        Tramite anterior = new Tramite();
        anterior.setGestoria(contactoA.getGestoria());
        anterior.setContacto(contactoA);
        anterior.setEstado(EstadoTramite.PENDIENTE_REVISION);
        long tramiteId = tramiteRepository.save(anterior).getId();

        JsonNode fila = contenido(get("/tramites", tokenA)).get(0);
        assertThat(fila.get("id").asLong()).isEqualTo(tramiteId);
        assertThat(fila.get("origen").isNull()).isTrue();
        assertThat(fila.get("estadoExtraccion").isNull()).isTrue();
        assertThat(fila.get("crotalesDescartados").asInt()).isZero();
        JsonNode detalle = detalle(tramiteId, tokenA);
        assertThat(detalle.has("origen")).isTrue();
        assertThat(detalle.get("origen").isNull()).isTrue();
        assertThat(detalle.get("estadoExtraccion").isNull()).isTrue();
        assertThat(detalle.get("crotalesDescartados").asInt()).isZero();
        assertThat(get("/tramites/" + tramiteId, tokenB).getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void pasadoElPlazoElDetalleDevuelveElTextoEliminadoYElTramiteSigueIgual() throws IOException {
        ia.devolver(TipoTramite.BAJA_MUERTE, "1234");
        enviar(parametros("SM-ext-retencion", TEL_A, TEXTO));
        extraccionTramiteService.procesarPendientes();
        long tramiteId = contenido(get("/tramites", tokenA)).get(0).get("id").asLong();
        JsonNode antes = detalle(tramiteId, tokenA);
        assertThat(antes.get("mensajeOriginal").asText()).isEqualTo(TEXTO);

        // Un dia antes de los 12 meses no se vacia; pasados los 12 meses, si.
        reloj.fijar(IaDePruebaConfig.AHORA.plus(java.time.Duration.ofDays(364)));
        assertThat(retencionMensajesService.aplicar().vaciados()).isZero();
        reloj.fijar(IaDePruebaConfig.AHORA.plus(java.time.Duration.ofDays(367)));
        assertThat(retencionMensajesService.aplicar().vaciados()).isEqualTo(1);

        JsonNode despues = detalle(tramiteId, tokenA);
        assertThat(despues.get("mensajeOriginal").asText()).isEqualTo(RetencionMensajesService.TEXTO_ELIMINADO);
        assertThat(despues.get("version").asLong()).isEqualTo(antes.get("version").asLong());
        assertThat(despues.get("tipoTramite").asText()).isEqualTo("BAJA_MUERTE");
        assertThat(despues.get("estadoExtraccion").asText()).isEqualTo("COMPLETADA");
        assertThat(despues.get("crotales")).hasSize(1);
        assertThat(get("/tramites/" + tramiteId, tokenB).getStatusCode().value()).isEqualTo(404);
    }

    // ------------------------------------------------------------ ayudas

    private JsonNode detalle(long tramiteId, String token) throws IOException {
        ResponseEntity<String> respuesta = get("/tramites/" + tramiteId, token);
        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        return objectMapper.readTree(respuesta.getBody());
    }

    private Map<String, Object> tramiteEnBd(long id) {
        Map<String, Object> resultado = new LinkedHashMap<>();
        jdbcTemplate.queryForMap("select * from tramite where id = ?", id)
                .forEach((k, v) -> resultado.put(k.toLowerCase(), v));
        return resultado;
    }

    private static Map<String, String> parametros(String messageSid, String telefono, String cuerpo) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("MessageSid", messageSid);
        p.put("AccountSid", "AC00000000000000000000000000000000");
        p.put("From", "whatsapp:" + telefono);
        p.put("To", "whatsapp:+14155238886");
        p.put("Body", cuerpo);
        p.put("NumMedia", "0");
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

    private JsonNode contenido(ResponseEntity<String> respuesta) throws IOException {
        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        return objectMapper.readTree(respuesta.getBody()).get("content");
    }

    private ResponseEntity<String> get(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private Explotacion explotacion(Gestoria gestoria, String codigoRega) {
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

    private void animal(Gestoria gestoria, Explotacion explotacion, String crotal) {
        Animal animal = new Animal();
        animal.setGestoria(gestoria);
        animal.setExplotacion(explotacion);
        animal.setCrotal(crotal);
        animal.setCrotalUltimosDigitos(crotal.substring(crotal.length() - 6));
        animalRepository.save(animal);
    }

    private Contacto contacto(Gestoria gestoria, String telefono) {
        Contacto contacto = new Contacto();
        contacto.setGestoria(gestoria);
        contacto.setTelefono(telefono);
        contacto.setNombre("Contacto extraccion E2E");
        contacto.setActivo(true);
        return contactoRepository.save(contacto);
    }

    private void enlazar(Gestoria gestoria, Contacto contacto, Explotacion explotacion) {
        ContactoExplotacion enlace = new ContactoExplotacion();
        enlace.setGestoria(gestoria);
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
        usuario.setNombre("Usuario extraccion E2E");
        usuario.setActivo(true);
        usuarioRepository.save(usuario);
    }

    private String login(String email) {
        ResponseEntity<LoginResponse> respuesta = restTemplate.postForEntity(
                "/auth/login", new LoginRequest(email, "password123"), LoginResponse.class);
        return respuesta.getBody().token();
    }
}
