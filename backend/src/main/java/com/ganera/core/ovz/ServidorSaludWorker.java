package com.ganera.core.ovz;

import com.sun.net.httpserver.HttpServer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Servidor de salud del worker de OVZ (fix despliegue en Clever). El perfil {@code worker} arranca SIN servidor web
 * (ni Tomcat ni {@code DispatcherServlet}): no expone ningun endpoint de la aplicacion. Pero Clever exige que toda
 * aplicacion (tambien las Docker) escuche en {@code 0.0.0.0:8080}, o rechaza el despliegue con «Your application is
 * not listening on 8080».
 *
 * <p>Este servidor es un {@link HttpServer} del JDK ({@code com.sun.net.httpserver}), <b>independiente de Spring MVC</b>:
 * no levanta Tomcat ni añade ninguna ruta de la aplicacion. Solo responde {@code 200 ok} (texto plano, sin datos) a
 * {@code GET /} y {@code 404} a todo lo demas (otra ruta u otro metodo), para que Clever vea el puerto abierto. Se cierra
 * al parar el contexto.
 *
 * <p>Solo existe en el perfil {@code worker} ({@code ganera.ovz.worker.activo=true}, como {@link WorkerOvzScheduler}):
 * en el backend normal Tomcat ya escucha en 8080, asi que este servidor NO debe arrancar ahi.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ganera.ovz.worker.activo", havingValue = "true")
class ServidorSaludWorker {

    private static final byte[] CUERPO_OK = "ok".getBytes(StandardCharsets.US_ASCII);

    private final int puertoConfigurado;
    private HttpServer servidor;

    // Clever inyecta el puerto en la variable de entorno PORT (8080 por defecto). Escuchamos en 0.0.0.0 (todas las
    // interfaces), que es lo que Clever comprueba.
    ServidorSaludWorker(@Value("${PORT:8080}") int puertoConfigurado) {
        this.puertoConfigurado = puertoConfigurado;
    }

    @PostConstruct
    void arrancar() {
        try {
            servidor = HttpServer.create(new InetSocketAddress("0.0.0.0", puertoConfigurado), 0);
        } catch (IOException e) {
            // Solo el tipo: el mensaje podria llevar el puerto/host.
            throw new UncheckedIOException("No se pudo abrir el servidor de salud del worker", e);
        }
        servidor.createContext("/", intercambio -> {
            try (intercambio) {
                boolean salud = "GET".equals(intercambio.getRequestMethod())
                        && "/".equals(intercambio.getRequestURI().getPath());
                if (salud) {
                    intercambio.getResponseHeaders().set("Content-Type", "text/plain; charset=us-ascii");
                    intercambio.sendResponseHeaders(200, CUERPO_OK.length);
                    try (OutputStream salida = intercambio.getResponseBody()) {
                        salida.write(CUERPO_OK);
                    }
                } else {
                    // 404 sin cuerpo.
                    intercambio.sendResponseHeaders(404, -1);
                }
            }
        });
        // Ejecutor por defecto (atiende en el hilo del despachador): basta para un chequeo de salud trivial.
        servidor.setExecutor(null);
        servidor.start();
        log.info("Servidor de salud del worker escuchando en 0.0.0.0:{}", puerto());
    }

    @PreDestroy
    void parar() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    /** Puerto realmente enlazado (util en tests con {@code PORT=0}). */
    int puerto() {
        return servidor.getAddress().getPort();
    }
}
