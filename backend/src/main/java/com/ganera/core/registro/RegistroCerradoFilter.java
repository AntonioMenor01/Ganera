package com.ganera.core.registro;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Cierre del registro publico (Prompt C, T2, D4). Con {@code ganera.registro.abierto=false} (el
 * valor por defecto) cualquier peticion a /gestorias/registro, con cualquier metodo, cuerpo,
 * Content-Type o cabecera Authorization, recibe un 404 sin cuerpo y no llega ni a Spring Security
 * ni al controlador: no se lee ni se valida el cuerpo, asi que un cuerpo valido, uno invalido, un
 * JSON roto o ninguno dan exactamente la misma respuesta, y no se crea nada.
 *
 * <p>Va como filtro de servlet (registrado en {@link RegistroConfig} solo para esa ruta y antes
 * que la cadena de seguridad) y no como comprobacion dentro del controlador porque el controlador
 * lee el cuerpo antes de ejecutarse: un JSON mal formado o un cuerpo vacio darian 400 y un
 * Content-Type distinto 415, distinguibles del 404. Se usa {@code setStatus} y no
 * {@code sendError}: sendError pasaria por /error y pintaria el cuerpo JSON por defecto de Boot.
 */
class RegistroCerradoFilter extends OncePerRequestFilter {

    private final boolean registroAbierto;

    RegistroCerradoFilter(boolean registroAbierto) {
        this.registroAbierto = registroAbierto;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (!registroAbierto) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.setContentLength(0);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
