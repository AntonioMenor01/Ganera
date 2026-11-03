package com.ganera.core.shared.auditoria;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;

/**
 * Contexto de cada peticion para la auditoria (plan 2026-10-21, D5 y D10). El primero de todos los filtros (ver
 * {@link ContextoPeticionConfig}):
 *
 * <ul>
 *   <li>genera un {@code request_id} (UUID nuevo; un {@code X-Request-Id} entrante se IGNORA: lo pone Ganera);</li>
 *   <li>calcula la IP con {@link IpCliente} (X-Forwarded-For solo si la propiedad lo dice; si no trae una IP valida,
 *       la de la conexion y un WARN sin el valor de la cabecera, que viene del cliente);</li>
 *   <li>los guarda en {@link ContextoPeticion} y el request_id en el MDC ({@code requestId});</li>
 *   <li>anade {@code X-Request-Id} a la respuesta ANTES de pasar la cadena, para que tambien salga en los 401, 404,
 *       413 o 503 que corten otros filtros;</li>
 *   <li>y en el {@code finally} limpia SIEMPRE el ThreadLocal y el MDC (los hilos de Tomcat se reutilizan).</li>
 * </ul>
 *
 * No lee el cuerpo.
 */
@Slf4j
class ContextoPeticionFilter extends OncePerRequestFilter {

    static final String CABECERA = "X-Request-Id";
    static final String CLAVE_MDC = "requestId";
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    private final boolean confiarEnXff;

    ContextoPeticionFilter(boolean confiarEnXff) {
        this.confiarEnXff = confiarEnXff;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        try {
            // El MDC lo primero: asi el WARN de abajo ya sale con el requestId (logging.pattern.correlation).
            MDC.put(CLAVE_MDC, requestId);
            List<String> cabecerasXff = cabeceras(request.getHeaders(X_FORWARDED_FOR));
            if (confiarEnXff && IpCliente.ultimaIpValidaDeXff(cabecerasXff).isEmpty()) {
                // Sin el valor de la cabecera: lo escribe el cliente.
                log.warn("X-Forwarded-For ausente o sin una IP valida al final; se audita la IP de la conexion");
            }
            String ip = IpCliente.de(request.getRemoteAddr(), cabecerasXff, confiarEnXff);
            ContextoPeticion.establecer(ip, requestId);
            response.setHeader(CABECERA, requestId);
            filterChain.doFilter(request, response);
        } finally {
            ContextoPeticion.limpiar();
            MDC.remove(CLAVE_MDC);
        }
    }

    private static List<String> cabeceras(Enumeration<String> valores) {
        return valores == null ? List.of() : Collections.list(valores);
    }
}
