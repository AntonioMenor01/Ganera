package com.ganera.core.shared.auditoria;

import java.util.Optional;

/**
 * IP del cliente y {@code request_id} de la peticion HTTP en curso (plan 2026-10-21, D5 y D10), para las filas de
 * auditoria. Lo rellena y lo limpia SIEMPRE {@link ContextoPeticionFilter}; en un hilo sin peticion (planificadores)
 * esta vacio y la fila lleva ip y request_id NULL.
 */
public final class ContextoPeticion {

    /** Lo que se guarda por peticion. */
    public record Datos(String ip, String requestId) {
    }

    private static final ThreadLocal<Datos> ACTUAL = new ThreadLocal<>();

    private ContextoPeticion() {
    }

    public static Optional<Datos> actual() {
        return Optional.ofNullable(ACTUAL.get());
    }

    /** Solo el filtro (y los tests del paquete). */
    static void establecer(String ip, String requestId) {
        ACTUAL.set(new Datos(ip, requestId));
    }

    /** Solo el filtro (y los tests del paquete), en su {@code finally}. */
    static void limpiar() {
        ACTUAL.remove();
    }
}
