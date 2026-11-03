package com.ganera.core.shared.auditoria;

import com.ganera.core.shared.security.GaneraUserPrincipal;
import com.ganera.core.shared.security.JwtService;
import jakarta.servlet.ServletRequestEvent;
import jakarta.servlet.ServletRequestListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * X-Request-Id (plan 2026-10-21, D10) sobre HTTP real: lo pone {@link ContextoPeticionFilter}, el primero de todos
 * (antes de RegistroCerradoFilter, del tope del callback de estado y de Spring Security), asi que sale tambien en
 * los 401, 404, 413 y 503 que otros cortan antes del controlador. Un X-Request-Id entrante se ignora.
 *
 * <p>Que el ThreadLocal y el MDC quedan vacios despues se comprueba en el hilo de Tomcat que atendio la peticion:
 * un {@link ServletRequestListener} corre su {@code requestDestroyed} en ese hilo cuando ya ha terminado toda la
 * cadena de filtros (y el /error), justo donde un ThreadLocal sin limpiar se quedaria para la siguiente peticion.
 * java.net.http y no TestRestTemplate: el cliente no anade cabeceras ni pide XML (nota de CLAUDE.md).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(VigiaPeticion.class)
@ExtendWith(OutputCaptureExtension.class)
class ContextoPeticionFilterEndToEndTest {

    /** Lo que el listener ve al final de cada peticion, en el hilo del servidor. */
    static final Queue<String> AL_TERMINAR = new ConcurrentLinkedQueue<>();

    @TestConfiguration
    static class Vigia {
        @Bean
        ServletRequestListener vigiaDelContexto() {
            return new ServletRequestListener() {
                @Override
                public void requestDestroyed(ServletRequestEvent evento) {
                    AL_TERMINAR.add("contexto=" + ContextoPeticion.actual().isPresent()
                            + ";mdc=" + MDC.get(ContextoPeticionFilter.CLAVE_MDC));
                }
            };
        }
    }

    @LocalServerPort
    private int puerto;
    @Autowired
    private JwtService jwtService;
    @Autowired
    @Qualifier("contextoPeticionFilter")
    private FilterRegistrationBean<?> contexto;
    @Autowired
    @Qualifier("registroCerradoFilter")
    private FilterRegistrationBean<?> registroCerrado;
    @Autowired
    @Qualifier("limiteCuerpoCallbackEstado")
    private FilterRegistrationBean<?> limiteCallback;

    private final HttpClient cliente = HttpClient.newHttpClient();

    @BeforeEach
    void vaciar() {
        AL_TERMINAR.clear();
        VigiaPeticion.VISTOS.clear();
    }

    private HttpResponse<String> enviar(HttpRequest.Builder peticion) throws Exception {
        return cliente.send(peticion.header("Accept", "application/json").build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder get(String ruta) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta)).GET();
    }

    private HttpRequest.Builder post(String ruta, String cuerpo, String tipo) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .header("Content-Type", tipo)
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo));
    }

    private String token() {
        return jwtService.generarToken(new GaneraUserPrincipal(1L, Long.MAX_VALUE, "contexto@auditoria.test"));
    }

    private static String requestId(HttpResponse<String> respuesta) {
        List<String> valores = respuesta.headers().allValues("X-Request-Id");
        assertThat(valores).as("X-Request-Id en un " + respuesta.statusCode()).hasSize(1);
        String valor = valores.get(0);
        assertThat(UUID.fromString(valor).toString()).isEqualTo(valor);
        return valor;
    }

    @Test
    void elFiltroDelContextoVaAntesQueTodosLosDemas() {
        assertThat(contexto.getFilter()).isInstanceOf(ContextoPeticionFilter.class);
        assertThat(contexto.getOrder()).isLessThan(registroCerrado.getOrder())
                .isLessThan(limiteCallback.getOrder())
                .isLessThan(SecurityProperties.DEFAULT_FILTER_ORDER);
    }

    @Test
    void unaRespuesta200LlevaUnRequestId() throws Exception {
        HttpResponse<String> respuesta = enviar(get("/explotaciones").header("Authorization", "Bearer " + token()));

        assertThat(respuesta.statusCode()).isEqualTo(200);
        requestId(respuesta);
    }

    @Test
    void los401404413Y503CortadosAntesDelControladorTambienLoLlevan() throws Exception {
        HttpResponse<String> sinJwt = enviar(get("/tramites"));
        assertThat(sinJwt.statusCode()).isEqualTo(401);
        requestId(sinJwt);

        HttpResponse<String> noExiste = enviar(get("/no-existe-esta-ruta").header("Authorization", "Bearer " + token()));
        assertThat(noExiste.statusCode()).isEqualTo(404);
        requestId(noExiste);

        HttpResponse<String> registroCerradoRespuesta = enviar(post("/gestorias/registro", "{}", "application/json"));
        assertThat(registroCerradoRespuesta.statusCode()).isEqualTo(404);
        assertThat(registroCerradoRespuesta.body()).isEmpty();
        requestId(registroCerradoRespuesta);

        HttpResponse<String> demasiadoGrande = enviar(post("/webhooks/twilio/whatsapp/estado",
                "MessageSid=" + "x".repeat(9000), "application/x-www-form-urlencoded"));
        assertThat(demasiadoGrande.statusCode()).isEqualTo(413);
        requestId(demasiadoGrande);

        HttpResponse<String> sinConfigurar = enviar(post("/webhooks/twilio/whatsapp/estado",
                "MessageSid=SM1&MessageStatus=delivered", "application/x-www-form-urlencoded"));
        assertThat(sinConfigurar.statusCode()).isEqualTo(503);
        requestId(sinConfigurar);
    }

    @Test
    void cadaPeticionTieneUnoDistintoYUnRequestIdEntranteSeIgnora() throws Exception {
        String entrante = UUID.randomUUID().toString();
        List<String> vistos = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            vistos.add(requestId(enviar(get("/tramites").header("X-Request-Id", entrante))));
        }

        assertThat(vistos).doesNotHaveDuplicates().doesNotContain(entrante);
    }

    @Test
    void trasCadaPeticionElHiloDelServidorQuedaSinContextoNiMdc() throws Exception {
        enviar(get("/explotaciones").header("Authorization", "Bearer " + token()));
        enviar(get("/tramites"));
        enviar(get("/no-existe-esta-ruta").header("Authorization", "Bearer " + token()));
        enviar(post("/gestorias/registro", "{}", "application/json"));

        // El listener corre despues de enviar la respuesta: se espera un poco a que lleguen los cuatro.
        long limite = System.currentTimeMillis() + 5_000;
        while (AL_TERMINAR.size() < 4 && System.currentTimeMillis() < limite) {
            Thread.sleep(10);
        }
        assertThat(AL_TERMINAR).hasSizeGreaterThanOrEqualTo(4).containsOnly("contexto=false;mdc=null");
    }

    /**
     * Revision de T1, menor 3 (caso false; el true en ContextoPeticionXffEndToEndTest): con la propiedad por defecto
     * X-Forwarded-For no cuenta, la IP es la de la conexion.
     */
    @Test
    void sinLaPropiedadXForwardedForSeIgnoraYLaIpEsLaDeLaConexion() throws Exception {
        HttpResponse<String> respuesta = enviar(HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + puerto + "/tramites")).GET().header("X-Forwarded-For", "203.0.113.7"));

        assertThat(respuesta.statusCode()).isEqualTo(401);
        assertThat(VigiaPeticion.VISTOS).extracting(ContextoPeticion.Datos::ip).containsExactly("127.0.0.1");
    }

    /**
     * Revision de T1, menor 1 (D10): un log emitido durante la peticion lleva el requestId que devolvio X-Request-Id
     * (logging.pattern.correlation con el MDC; el application.yml real lo fija igual, ver
     * ConfiguracionSeguridadProduccionTest).
     */
    @Test
    void unLogEmitidoDuranteLaPeticionLlevaElRequestIdDeLaRespuesta(CapturedOutput salida) throws Exception {
        String requestId = requestId(enviar(get("/tramites")));

        assertThat(salida.getAll().lines().filter(linea -> linea.contains(VigiaPeticion.MENSAJE_LOG)))
                .anySatisfy(linea -> assertThat(linea).contains("[" + requestId + "] "));
    }
}
