package com.ganera.core.diagnostico;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El servicio de alcance contra un servidor HTTP FALSO real (patron test-sin-mocks-externos): responde el
 * codigo HTTP, "timeout" si tarda mas del limite, "conexion" si no hay nada escuchando. Nunca lee el cuerpo.
 */
class DiagnosticoAlcanceServiceTest {

    private HttpServer servidor;

    @AfterEach
    void parar() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    private int arrancar(HttpHandler handler) throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", handler);
        servidor.setExecutor(Executors.newSingleThreadExecutor());
        servidor.start();
        return servidor.getAddress().getPort();
    }

    @Test
    void devuelveElCodigoHttpCuandoOvzResponde() throws IOException {
        int puerto = arrancar(ex -> {
            ex.sendResponseHeaders(204, -1);
            ex.close();
        });
        DiagnosticoAlcanceResponse r = new DiagnosticoAlcanceService("http://127.0.0.1:" + puerto + "/", 5000).comprobar();
        assertThat(r.codigoHttp()).isEqualTo(204);
        assertThat(r.error()).isNull();
    }

    @Test
    void devuelveTimeoutCuandoOvzTardaMasDelLimite() throws IOException {
        int puerto = arrancar(ex -> {
            try {
                Thread.sleep(2000);
                ex.sendResponseHeaders(200, -1);
            } catch (InterruptedException | IOException ignored) {
                // el cliente ya habra cortado por timeout
            } finally {
                ex.close();
            }
        });
        DiagnosticoAlcanceResponse r = new DiagnosticoAlcanceService("http://127.0.0.1:" + puerto + "/", 300).comprobar();
        assertThat(r.error()).isEqualTo("timeout");
        assertThat(r.codigoHttp()).isNull();
    }

    @Test
    void devuelveConexionCuandoNoHayNadaEscuchando() throws IOException {
        int puertoCerrado;
        try (ServerSocket ss = new ServerSocket(0)) {
            puertoCerrado = ss.getLocalPort();
        } // al salir del try el puerto queda libre: nadie escucha -> conexion rechazada
        DiagnosticoAlcanceResponse r = new DiagnosticoAlcanceService("http://127.0.0.1:" + puertoCerrado + "/", 2000).comprobar();
        assertThat(r.error()).isEqualTo("conexion");
        assertThat(r.codigoHttp()).isNull();
    }
}
