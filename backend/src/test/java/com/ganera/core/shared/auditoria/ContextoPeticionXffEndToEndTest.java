package com.ganera.core.shared.auditoria;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Revision de T1, menores 1 y 3, con la propiedad como en el perfil clever
 * ({@code ganera.auditoria.ip-desde-x-forwarded-for=true}): fija el NOMBRE de la propiedad que lee el {@code @Value}
 * de ContextoPeticionConfig (una errata la dejaria en false en silencio) comprobando que la IP de ContextoPeticion es
 * la del ultimo elemento de X-Forwarded-For; y que el WARN de una cabecera sin IP valida sale en el log con el
 * requestId que devolvio X-Request-Id (logging.pattern.correlation) y sin el valor de la cabecera. El caso
 * {@code false} esta en ContextoPeticionFilterEndToEndTest.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "ganera.auditoria.ip-desde-x-forwarded-for=true")
@Import(VigiaPeticion.class)
@ExtendWith(OutputCaptureExtension.class)
class ContextoPeticionXffEndToEndTest {

    @LocalServerPort
    private int puerto;

    private final HttpClient cliente = HttpClient.newHttpClient();

    @BeforeEach
    void vaciar() {
        VigiaPeticion.VISTOS.clear();
    }

    private HttpResponse<String> getConXff(String xff) throws Exception {
        HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + puerto + "/tramites"))
                .header("Accept", "application/json").GET();
        if (xff != null) {
            peticion.header("X-Forwarded-For", xff);
        }
        return cliente.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void conLaPropiedadATrueLaIpEsElUltimoElementoDeXForwardedFor() throws Exception {
        HttpResponse<String> respuesta = getConXff("1.1.1.1, 203.0.113.7");

        assertThat(respuesta.statusCode()).isEqualTo(401);
        assertThat(VigiaPeticion.VISTOS).hasSize(1);
        ContextoPeticion.Datos visto = VigiaPeticion.VISTOS.peek();
        assertThat(visto.ip()).isEqualTo("203.0.113.7");
        assertThat(visto.requestId()).isEqualTo(respuesta.headers().firstValue("X-Request-Id").orElseThrow());
    }

    @Test
    void sinIpValidaEnLaCabeceraSeQuedaLaDeLaConexionYElWarnLlevaElRequestId(CapturedOutput salida) throws Exception {
        HttpResponse<String> respuesta = getConXff("203.0.113.7, valor-secreto-del-cliente");

        String requestId = respuesta.headers().firstValue("X-Request-Id").orElseThrow();
        assertThat(VigiaPeticion.VISTOS).extracting(ContextoPeticion.Datos::ip).containsExactly("127.0.0.1");
        assertThat(salida.getAll().lines().filter(linea -> linea.contains("X-Forwarded-For ausente")))
                .singleElement().satisfies(linea -> {
                    assertThat(linea).contains("WARN").contains("[" + requestId + "] ");
                    assertThat(linea).doesNotContain("valor-secreto-del-cliente");
                });
    }
}
