package com.ganera.core.whatsapp;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * Envio por WhatsApp FALSO para los tests (B2 reducido, T1): nunca hay red ni credenciales. Registra
 * cada llamada (telefono, texto y si habia una transaccion abierta), devuelve el resultado programado
 * y deja programar configurado(). Se inyecta con {@link Config} (@Primary sobre TwilioEnvioWhatsapp).
 */
public class EnvioWhatsappFalso implements EnvioWhatsapp {

    /** Una llamada a enviar(): a que telefono, que texto y si se hizo dentro de una transaccion. */
    public record Llamada(String telefono, String texto, boolean dentroDeTransaccion) {
    }

    private final List<Llamada> llamadas = new CopyOnWriteArrayList<>();
    private volatile boolean configurado = true;
    private volatile Supplier<ResultadoEnvioWhatsapp> resultado = EnvioWhatsappFalso::aceptadoConSidNuevo;
    private volatile long esperaMs = 0;

    private static final java.util.concurrent.atomic.AtomicLong SECUENCIA = new java.util.concurrent.atomic.AtomicLong();

    private static ResultadoEnvioWhatsapp aceptadoConSidNuevo() {
        return new ResultadoEnvioWhatsapp.Aceptado("SMfalso" + SECUENCIA.incrementAndGet());
    }

    @Override
    public boolean configurado() {
        return configurado;
    }

    @Override
    public ResultadoEnvioWhatsapp enviar(String telefonoE164, String texto) {
        llamadas.add(new Llamada(telefonoE164, texto, TransactionSynchronizationManager.isActualTransactionActive()));
        if (esperaMs > 0) {
            try {
                Thread.sleep(esperaMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return resultado.get();
    }

    public void configurado(boolean valor) {
        this.configurado = valor;
    }

    public void devolver(ResultadoEnvioWhatsapp valor) {
        this.resultado = () -> valor;
    }

    /** Retrasa cada envio (para provocar la carrera de dos peticiones con la misma clave). */
    public void esperar(long milisegundos) {
        this.esperaMs = milisegundos;
    }

    public List<Llamada> llamadas() {
        return List.copyOf(llamadas);
    }

    public void reiniciar() {
        llamadas.clear();
        configurado = true;
        esperaMs = 0;
        resultado = EnvioWhatsappFalso::aceptadoConSidNuevo;
    }

    /** Sustituye al envio real en el contexto del test. */
    @TestConfiguration
    public static class Config {

        @Bean
        @Primary
        public EnvioWhatsappFalso envioWhatsappFalso() {
            return new EnvioWhatsappFalso();
        }
    }
}
