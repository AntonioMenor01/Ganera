package com.ganera.core.tramite;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * IA y reloj de prueba para la extraccion (B1, T2). Nunca se llama a Anthropic: el
 * {@link IaProgramable} sustituye a AnthropicTramiteExtractionService (@Primary) y el
 * {@link RelojAjustable} al Clock de RelojConfig, para mover "ahora" sin esperas reales.
 */
@TestConfiguration
public class IaDePruebaConfig {

    public static final Instant AHORA = Instant.parse("2031-03-10T09:00:00Z");

    @Bean
    @Primary
    public IaProgramable iaProgramable() {
        return new IaProgramable();
    }

    @Bean
    @Primary
    public RelojAjustable relojAjustable() {
        return new RelojAjustable(AHORA);
    }

    /** IA falsa: responde con la funcion programada (devuelve un resultado o lanza) y cuenta. */
    public static class IaProgramable implements TramiteExtractionService {

        private volatile Function<String, TramiteExtraido> respuesta = texto -> {
            throw new IllegalStateException("IA de prueba sin programar");
        };
        private final List<String> textos = new CopyOnWriteArrayList<>();
        private final List<Boolean> conTransaccion = new CopyOnWriteArrayList<>();

        @Override
        public TramiteExtraido extraer(String mensajeOriginal) {
            textos.add(mensajeOriginal);
            conTransaccion.add(TransactionSynchronizationManager.isActualTransactionActive());
            return respuesta.apply(mensajeOriginal);
        }

        public void responder(Function<String, TramiteExtraido> respuesta) {
            this.respuesta = respuesta;
        }

        public void devolver(TipoTramite tipo, String... crotales) {
            TramiteExtraido extraido = new TramiteExtraido(tipo, List.of(crotales));
            responder(texto -> extraido);
        }

        public void lanzar(RuntimeException fallo) {
            responder(texto -> {
                throw fallo;
            });
        }

        public int llamadas() {
            return textos.size();
        }

        public List<String> textos() {
            return List.copyOf(textos);
        }

        /** Si alguna llamada a la IA se hizo con una transaccion (y por tanto un posible bloqueo) abierta. */
        public boolean algunaLlamadaDentroDeTransaccion() {
            return conTransaccion.contains(Boolean.TRUE);
        }

        public void reiniciar() {
            textos.clear();
            conTransaccion.clear();
            respuesta = texto -> {
                throw new IllegalStateException("IA de prueba sin programar");
            };
        }
    }

    /** Reloj que solo avanza cuando el test lo mueve. */
    public static class RelojAjustable extends Clock {

        private volatile Instant ahora;

        public RelojAjustable(Instant ahora) {
            this.ahora = ahora;
        }

        public void fijar(Instant instante) {
            this.ahora = instante;
        }

        public void avanzar(Duration duracion) {
            this.ahora = ahora.plus(duracion);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }
}
