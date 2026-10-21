package com.ganera.core.shared.auditoria;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El filtro en el mismo hilo (sin servidor): rellena ContextoPeticion y el MDC durante la cadena, pone X-Request-Id
 * ANTES de pasar la cadena y lo limpia todo en el finally, tambien si la cadena lanza.
 */
class ContextoPeticionFilterTest {

    @AfterEach
    void limpiar() {
        ContextoPeticion.limpiar();
        MDC.clear();
    }

    private static MockHttpServletRequest peticion(String... xff) {
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/tramites");
        peticion.setRemoteAddr("10.0.0.5");
        for (String cabecera : xff) {
            peticion.addHeader("X-Forwarded-For", cabecera);
        }
        peticion.addHeader("X-Request-Id", "entrante-que-se-ignora");
        return peticion;
    }

    @Test
    void duranteLaCadenaHayContextoYCabeceraYDespuesNadaEnElHilo() throws Exception {
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        AtomicReference<ContextoPeticion.Datos> visto = new AtomicReference<>();
        AtomicReference<String> cabeceraVista = new AtomicReference<>();
        AtomicReference<String> mdcVisto = new AtomicReference<>();

        new ContextoPeticionFilter(false).doFilter(peticion("203.0.113.7"), respuesta, (req, res) -> {
            visto.set(ContextoPeticion.actual().orElseThrow());
            cabeceraVista.set(((MockHttpServletResponse) res).getHeader(ContextoPeticionFilter.CABECERA));
            mdcVisto.set(MDC.get(ContextoPeticionFilter.CLAVE_MDC));
        });

        assertThat(visto.get().ip()).isEqualTo("10.0.0.5"); // sin confiar en XFF
        assertThat(UUID.fromString(visto.get().requestId()).toString()).isEqualTo(visto.get().requestId());
        assertThat(cabeceraVista.get()).isEqualTo(visto.get().requestId());
        assertThat(mdcVisto.get()).isEqualTo(visto.get().requestId());
        assertThat(respuesta.getHeader("X-Request-Id")).isEqualTo(visto.get().requestId());
        assertThat(ContextoPeticion.actual()).isEmpty();
        assertThat(MDC.get(ContextoPeticionFilter.CLAVE_MDC)).isNull();
    }

    @Test
    void siLaCadenaLanzaTambienSeLimpia() {
        assertThatThrownBy(() -> new ContextoPeticionFilter(false).doFilter(peticion(), new MockHttpServletResponse(),
                (req, res) -> {
                    throw new ServletException("fallo");
                })).isInstanceOf(ServletException.class);

        assertThat(ContextoPeticion.actual()).isEmpty();
        assertThat(MDC.get(ContextoPeticionFilter.CLAVE_MDC)).isNull();
    }

    @Test
    void confiandoEnXffGuardaElUltimoElementoYSiNoValeAvisaSinElValorDeLaCabecera() throws Exception {
        List<String> ips = new ArrayList<>();
        ContextoPeticionFilter filtro = new ContextoPeticionFilter(true);
        Logger log = (Logger) LoggerFactory.getLogger(ContextoPeticionFilter.class);
        ListAppender<ILoggingEvent> eventos = new ListAppender<>();
        eventos.start();
        log.addAppender(eventos);
        try {
            filtro.doFilter(peticion("1.1.1.1, 203.0.113.7"), new MockHttpServletResponse(),
                    (req, res) -> ips.add(ContextoPeticion.actual().orElseThrow().ip()));
            assertThat(eventos.list).isEmpty();
            filtro.doFilter(peticion("203.0.113.7, valor-secreto-del-cliente"), new MockHttpServletResponse(),
                    (req, res) -> ips.add(ContextoPeticion.actual().orElseThrow().ip()));
            filtro.doFilter(peticion(), new MockHttpServletResponse(),
                    (req, res) -> ips.add(ContextoPeticion.actual().orElseThrow().ip()));
        } finally {
            log.detachAppender(eventos);
            eventos.stop();
        }

        assertThat(ips).containsExactly("203.0.113.7", "10.0.0.5", "10.0.0.5");
        assertThat(eventos.list).hasSize(2).allSatisfy(evento -> {
            assertThat(evento.getLevel()).isEqualTo(Level.WARN);
            assertThat(evento.getFormattedMessage()).doesNotContain("valor-secreto-del-cliente")
                    .doesNotContain("203.0.113.7");
        });
    }

    @Test
    void cadaPeticionTieneSuRequestIdYNuncaElEntrante() throws IOException, ServletException {
        List<String> ids = new ArrayList<>();
        ContextoPeticionFilter filtro = new ContextoPeticionFilter(false);
        for (int i = 0; i < 2; i++) {
            filtro.doFilter(peticion(), new MockHttpServletResponse(),
                    (req, res) -> ids.add(ContextoPeticion.actual().orElseThrow().requestId()));
        }
        assertThat(ids).doesNotHaveDuplicates().doesNotContain("entrante-que-se-ignora");
    }
}
