package com.ganera.core.diagnostico;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

/**
 * Comprueba si OVZnet responde desde donde corre el backend (TEMPORAL, para decidir el escalon 2). Hace UNA
 * sola peticion GET a una URL <b>fija de configuracion</b> (por defecto la de entrada de OVZ) — nunca a una
 * URL que llegue de fuera, asi que no hay SSRF. Descarta el cuerpo de la respuesta (no lee nada de OVZ) y no
 * envia credenciales. Devuelve solo el codigo HTTP, o {@code timeout}/{@code conexion}.
 */
@Service
public class DiagnosticoAlcanceService {

    private final String url;
    private final int timeoutMs;

    DiagnosticoAlcanceService(
            @Value("${ganera.diagnostico.ovz-url:https://ovznet.juntaex.es/Inicio.aspx}") String url,
            @Value("${ganera.diagnostico.timeout-ms:15000}") int timeoutMs) {
        this.url = url;
        this.timeoutMs = timeoutMs;
    }

    public DiagnosticoAlcanceResponse comprobar() {
        HttpClient cliente = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        HttpRequest peticion = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .GET()
                .build();
        try {
            // discarding(): no se lee el cuerpo de OVZ; solo interesa el codigo de estado.
            HttpResponse<Void> respuesta = cliente.send(peticion, HttpResponse.BodyHandlers.discarding());
            return DiagnosticoAlcanceResponse.conCodigo(respuesta.statusCode());
        } catch (HttpTimeoutException e) {
            return DiagnosticoAlcanceResponse.conError("timeout");
        } catch (IOException e) {
            return DiagnosticoAlcanceResponse.conError("conexion");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return DiagnosticoAlcanceResponse.conError("conexion");
        }
    }
}
