package com.ganera.core.whatsapp;

import com.ganera.core.contacto.Contacto;
import com.ganera.core.contacto.ContactoRepository;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.tramite.TramiteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
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
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Camino determinista de la carrera de duplicados: dos entregas del mismo MessageSid pasan a la vez
 * la comprobacion previa (existsByMessageSid = false) y la segunda choca con el UNIQUE(message_sid)
 * al insertar. Para reproducirlo sin depender de tiempos, el MensajeCampoRepository del contexto se
 * envuelve en un proxy (de nuestro propio repositorio, no de un SDK externo) que, cuando se le
 * pide, "ciega" UNA llamada a existsByMessageSid devolviendo false; todo lo demas va al repositorio
 * real. El mensaje "ganador" ya esta confirmado en la BD antes de la peticion.
 *
 * Esperado: la violacion se propaga fuera del @Transactional (que deshace todo, incluido el
 * Tramite), el controlador la captura, confirma con existsByMessageSid que de verdad es un
 * duplicado y responde 200 con &lt;Response/&gt; vacio. Si la violacion no es por un duplicado,
 * 500.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "twilio.auth-token=" + WhatsAppDuplicadoPorViolacionEndToEndTest.AUTH_TOKEN,
        "ganera.twilio.webhook-url=" + WhatsAppDuplicadoPorViolacionEndToEndTest.URL_PUBLICA})
class WhatsAppDuplicadoPorViolacionEndToEndTest {

    static final String AUTH_TOKEN = "token-violacion-inventado";
    static final String URL_PUBLICA = "https://ganera.example.test/webhooks/twilio/whatsapp";
    private static final String TELEFONO = "+34600940001";

    static final AtomicBoolean CEGAR_UNA_COMPROBACION = new AtomicBoolean(false);

    @TestConfiguration
    static class RepositorioConComprobacionCegable {
        @Bean
        @Primary
        MensajeCampoRepository mensajeCampoRepositoryCegable(
                @Qualifier("mensajeCampoRepository") MensajeCampoRepository real) {
            return (MensajeCampoRepository) Proxy.newProxyInstance(
                    MensajeCampoRepository.class.getClassLoader(),
                    new Class<?>[]{MensajeCampoRepository.class},
                    (proxy, metodo, args) -> {
                        if (metodo.getName().equals("existsByMessageSid")
                                && CEGAR_UNA_COMPROBACION.compareAndSet(true, false)) {
                            return false;
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
    private TramiteRepository tramiteRepository;
    @Autowired
    private ContactoRepository contactoRepository;
    @Autowired
    private GestoriaRepository gestoriaRepository;

    @BeforeEach
    void preparar() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria violacion"));
        Contacto contacto = new Contacto();
        contacto.setGestoria(gestoria);
        contacto.setTelefono(TELEFONO);
        contacto.setNombre("Contacto violacion");
        contactoRepository.save(contacto);
        CEGAR_UNA_COMPROBACION.set(false);
    }

    @AfterEach
    void limpiar() {
        CEGAR_UNA_COMPROBACION.set(false);
        mensajeCampoRepository.deleteAll();
        tramiteRepository.deleteAll();
        contactoRepository.deleteAll();
        gestoriaRepository.deleteAll();
    }

    @Test
    void laEntregaQuePierdeLaCarreraRecibeRespuestaVaciaYNoDejaTramite() {
        Map<String, String> parametros = parametros("SM-violacion-1");
        assertThat(enviar(parametros).getBody()).isEqualTo(WhatsAppRecepcionEndToEndTest.TWIML_ACUSE);
        assertThat(contar("tramite")).isEqualTo(1);

        CEGAR_UNA_COMPROBACION.set(true);
        ResponseEntity<String> segunda = enviar(parametros);

        assertThat(CEGAR_UNA_COMPROBACION.get()).as("la comprobacion previa se llego a cegar").isFalse();
        assertThat(segunda.getStatusCode().value()).isEqualTo(200);
        assertThat(segunda.getBody()).isEqualTo(WhatsAppRecepcionEndToEndTest.TWIML_VACIO);
        assertThat(contar("tramite")).isEqualTo(1);
        assertThat(contar("mensaje_campo")).isEqualTo(1);
    }

    /**
     * Una violacion de integridad que NO es por un MessageSid repetido no se disfraza de duplicado.
     * Con un MessageSid demasiado largo tampoco el rescate (ERROR_RECEPCION) puede guardarlo: 500.
     */
    @Test
    void unaViolacionQueNoEsUnDuplicadoAcabaEn500YNoGuardaNada() {
        // message_sid es VARCHAR(100): uno mas largo viola la columna, no el UNIQUE.
        ResponseEntity<String> respuesta = enviar(parametros("SM" + "x".repeat(120)));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(500);
        assertThat(contar("tramite")).isZero();
        assertThat(contar("mensaje_campo")).isZero();
    }

    private long contar(String tabla) {
        return jdbcTemplate.queryForObject("select count(*) from " + tabla, Long.class);
    }

    private static Map<String, String> parametros(String messageSid) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("MessageSid", messageSid);
        p.put("From", "whatsapp:" + TELEFONO);
        p.put("Body", "baja del 1234");
        p.put("NumMedia", "0");
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
