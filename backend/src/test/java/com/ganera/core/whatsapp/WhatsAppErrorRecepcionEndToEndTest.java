package com.ganera.core.whatsapp;

import com.ganera.core.contacto.Contacto;
import com.ganera.core.contacto.ContactoRepository;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.tramite.TramiteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "Nunca se pierde un mensaje" (m1 de la revision de T1): si falla la creacion del Tramite, la
 * transaccion de la recepcion se deshace entera, pero el mensaje se guarda en otra transaccion
 * (rescate) con resultado ERROR_RECEPCION, sin gestoria ni Tramite, y se responde 200 con
 * &lt;Response/&gt; vacio (sin acuse: no hay Tramite). Si tambien falla el rescate, 500.
 *
 * Los fallos se fuerzan con proxies de nuestros propios repositorios (no de un SDK externo): el
 * TramiteRepository lanza en save() y, si se pide, el MensajeCampoRepository lanza en save(), que
 * solo usa el rescate (la recepcion normal usa saveAndFlush). La excepcion simulada lleva el
 * telefono y el texto en su mensaje: el log no puede repetirlo.
 */
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "twilio.auth-token=" + WhatsAppErrorRecepcionEndToEndTest.AUTH_TOKEN,
        "ganera.twilio.webhook-url=" + WhatsAppErrorRecepcionEndToEndTest.URL_PUBLICA})
class WhatsAppErrorRecepcionEndToEndTest {

    static final String AUTH_TOKEN = "token-error-recepcion-inventado";
    static final String URL_PUBLICA = "https://ganera.example.test/webhooks/twilio/whatsapp";
    private static final String TELEFONO = "+34600930001";
    private static final String TEXTO = "baja del 1234 texto-que-no-debe-salir-en-el-log";

    static final AtomicBoolean FALLAR_TRAMITE = new AtomicBoolean(false);
    static final AtomicBoolean FALLAR_RESCATE = new AtomicBoolean(false);

    @TestConfiguration
    static class RepositoriosQueFallan {
        @Bean
        @Primary
        TramiteRepository tramiteRepositoryQueFalla(@Qualifier("tramiteRepository") TramiteRepository real) {
            return envolver(TramiteRepository.class, real, "save", FALLAR_TRAMITE);
        }

        @Bean
        @Primary
        MensajeCampoRepository mensajeCampoRepositoryQueFalla(
                @Qualifier("mensajeCampoRepository") MensajeCampoRepository real) {
            return envolver(MensajeCampoRepository.class, real, "save", FALLAR_RESCATE);
        }

        @SuppressWarnings("unchecked")
        private static <T> T envolver(Class<T> tipo, T real, String metodoQueFalla, AtomicBoolean fallar) {
            return (T) Proxy.newProxyInstance(tipo.getClassLoader(), new Class<?>[]{tipo}, (proxy, metodo, args) -> {
                if (metodo.getName().equals(metodoQueFalla) && fallar.get()) {
                    throw new IllegalStateException("fallo simulado con " + TELEFONO + " y " + TEXTO);
                }
                try {
                    return metodo.invoke(real, args);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            });
        }
    }

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    @Qualifier("mensajeCampoRepository")
    private MensajeCampoRepository mensajeCampoRepository;
    @Autowired
    @Qualifier("tramiteRepository")
    private TramiteRepository tramiteRepository;
    @Autowired
    private ContactoRepository contactoRepository;
    @Autowired
    private GestoriaRepository gestoriaRepository;

    @BeforeEach
    void preparar() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria error recepcion"));
        Contacto contacto = new Contacto();
        contacto.setGestoria(gestoria);
        contacto.setTelefono(TELEFONO);
        contacto.setNombre("Contacto error recepcion");
        contactoRepository.save(contacto);
        FALLAR_TRAMITE.set(false);
        FALLAR_RESCATE.set(false);
    }

    @AfterEach
    void limpiar() {
        FALLAR_TRAMITE.set(false);
        FALLAR_RESCATE.set(false);
        mensajeCampoRepository.deleteAll();
        tramiteRepository.deleteAll();
        contactoRepository.deleteAll();
        gestoriaRepository.deleteAll();
    }

    @Test
    void siFallaLaCreacionDelTramiteElMensajeSeGuardaComoErrorYSeRespondeVacio(CapturedOutput salida) {
        FALLAR_TRAMITE.set(true);

        ResponseEntity<String> respuesta = enviar(parametros("SM-error-1"));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(respuesta.getHeaders().getContentType().toString()).isEqualTo("text/xml;charset=UTF-8");
        assertThat(respuesta.getBody()).isEqualTo(WhatsAppRecepcionEndToEndTest.TWIML_VACIO);
        assertThat(contar("tramite")).isZero();
        List<Map<String, Object>> mensajes = jdbcTemplate.queryForList(
                "select message_sid, resultado, telefono_origen, cuerpo, num_media, gestoria_id, contacto_id, tramite_id"
                        + " from mensaje_campo");
        assertThat(mensajes).singleElement().satisfies(m -> {
            assertThat(m.get("MESSAGE_SID")).isEqualTo("SM-error-1");
            assertThat(m.get("RESULTADO")).isEqualTo("ERROR_RECEPCION");
            assertThat(m.get("TELEFONO_ORIGEN")).isEqualTo(TELEFONO);
            assertThat(m.get("CUERPO")).isEqualTo(TEXTO);
            assertThat(((Number) m.get("NUM_MEDIA")).intValue()).isEqualTo(1);
            assertThat(m.get("GESTORIA_ID")).isNull();
            assertThat(m.get("CONTACTO_ID")).isNull();
            assertThat(m.get("TRAMITE_ID")).isNull();
        });
        assertThat(salida.getAll()).contains("SM-error-1").contains("IllegalStateException")
                .doesNotContain("texto-que-no-debe-salir-en-el-log").doesNotContain("600930001");
    }

    /** Un reintento posterior de Twilio con ese MessageSid ya es un duplicado: vacio y sin Tramite. */
    @Test
    void unReintentoTrasElErrorEsDuplicado() {
        FALLAR_TRAMITE.set(true);
        enviar(parametros("SM-error-2"));
        FALLAR_TRAMITE.set(false);

        ResponseEntity<String> reintento = enviar(parametros("SM-error-2"));

        assertThat(reintento.getBody()).isEqualTo(WhatsAppRecepcionEndToEndTest.TWIML_VACIO);
        assertThat(contar("tramite")).isZero();
        assertThat(contar("mensaje_campo")).isEqualTo(1);
    }

    @Test
    void siTambienFallaElRescateResponde500YNoQuedaNada(CapturedOutput salida) {
        FALLAR_TRAMITE.set(true);
        FALLAR_RESCATE.set(true);

        ResponseEntity<String> respuesta = enviar(parametros("SM-error-3"));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(500);
        assertThat(contar("tramite")).isZero();
        assertThat(contar("mensaje_campo")).isZero();
        assertThat(salida.getAll()).contains("SM-error-3")
                .doesNotContain("texto-que-no-debe-salir-en-el-log").doesNotContain("600930001");
    }

    private long contar(String tabla) {
        return jdbcTemplate.queryForObject("select count(*) from " + tabla, Long.class);
    }

    private static Map<String, String> parametros(String messageSid) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("MessageSid", messageSid);
        p.put("From", "whatsapp:" + TELEFONO);
        p.put("Body", TEXTO);
        p.put("NumMedia", "1");
        return p;
    }

    private ResponseEntity<String> enviar(Map<String, String> parametros) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("X-Twilio-Signature", FirmaTwilioDePrueba.calcular(AUTH_TOKEN, URL_PUBLICA, parametros));
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        parametros.forEach(form::add);
        return restTemplate.postForEntity("/webhooks/twilio/whatsapp", new HttpEntity<>(form, headers), String.class);
    }
}
