package com.ganera.core.tramite;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganera.core.tramite.TramiteExtractionService.TramiteExtraido;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Sin mocks del SDK (skill test-sin-mocks-externos): el SDK oficial de verdad habla HTTP con un
 * servidor local del JDK que hace de API de Anthropic. Las respuestas falsas tienen la forma real de
 * la Messages API. Ninguna llamada sale a la red ni usa una clave real.
 */
class AnthropicTramiteExtractionServiceTest {

    private static final String CLAVE_FALSA = "sk-ant-clave-inventada-para-tests";
    private static final String MENSAJE = "Hola, quiero dar de alta los terneros 1234 y ES010000005678";

    private final ObjectMapper json = new ObjectMapper();
    private final List<String> cuerposRecibidos = new CopyOnWriteArrayList<>();
    private HttpServer servidor;
    private volatile int estadoHttp;
    private volatile String respuesta;

    @BeforeEach
    void arrancarServidor() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        servidor.createContext("/", intercambio -> {
            cuerposRecibidos.add(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = respuesta.getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(estadoHttp, bytes.length);
            try (OutputStream out = intercambio.getResponseBody()) {
                out.write(bytes);
            }
        });
        servidor.start();
    }

    @AfterEach
    void pararServidor() {
        servidor.stop(0);
    }

    private AnthropicTramiteExtractionService servicio() {
        String baseUrl = "http://127.0.0.1:" + servidor.getAddress().getPort();
        return new AnthropicTramiteExtractionService(CLAVE_FALSA, baseUrl, 0);
    }

    private void responder(int estado, String cuerpo) {
        this.estadoHttp = estado;
        this.respuesta = cuerpo;
    }

    /** Respuesta con la forma real de la Messages API, con un unico bloque de texto. */
    private void responderMensaje(String texto, String stopReason) throws IOException {
        String cuerpo = """
                {"id":"msg_01Prueba","type":"message","role":"assistant",
                 "model":"claude-haiku-4-5-20251001",
                 "content":[{"type":"text","text":%s}],
                 "stop_reason":"%s","stop_sequence":null,
                 "usage":{"input_tokens":120,"output_tokens":30}}
                """.formatted(json.writeValueAsString(texto), stopReason);
        responder(200, cuerpo);
    }

    @Test
    void laPeticionLlevaElModeloFijadoYLaSalidaEstructuradaNativaConElEsquema() throws Exception {
        responderMensaje("{\"tipoTramite\":\"ALTA\",\"crotales\":[\"1234\"]}", "end_turn");

        servicio().extraer(MENSAJE);

        assertThat(cuerposRecibidos).hasSize(1);
        JsonNode peticion = json.readTree(cuerposRecibidos.get(0));
        assertThat(peticion.path("model").asText()).isEqualTo("claude-haiku-4-5-20251001");
        assertThat(peticion.path("max_tokens").asLong()).isEqualTo(1024);
        assertThat(peticion.has("tools")).isFalse();
        assertThat(peticion.has("thinking")).isFalse();
        assertThat(peticion.path("system").toString()).contains("NO_IDENTIFICADO");
        // El texto del mensaje va en el turno de usuario, nunca en el system.
        assertThat(peticion.path("system").toString()).doesNotContain("1234");
        assertThat(peticion.path("messages")).hasSize(1);
        assertThat(peticion.path("messages").get(0).path("role").asText()).isEqualTo("user");
        assertThat(peticion.path("messages").get(0).path("content").toString()).contains(MENSAJE);

        JsonNode formato = peticion.path("output_config").path("format");
        assertThat(formato.path("type").asText()).isEqualTo("json_schema");
        JsonNode esquema = formato.path("schema");
        assertThat(esquema.path("type").asText()).isEqualTo("object");
        assertThat(esquema.path("additionalProperties").isBoolean()).isTrue();
        assertThat(esquema.path("additionalProperties").asBoolean()).isFalse();
        assertThat(textos(esquema.path("required"))).containsExactlyInAnyOrder("tipoTramite", "crotales");
        assertThat(textos(esquema.path("properties").path("tipoTramite").path("enum")))
                .containsExactlyInAnyOrder("ALTA", "BAJA", "CENSO", "MOVIMIENTO", "DEMORA", "NO_IDENTIFICADO");
        JsonNode crotales = esquema.path("properties").path("crotales");
        assertThat(crotales.path("type").asText()).isEqualTo("array");
        assertThat(crotales.path("items").path("type").asText()).isEqualTo("string");
    }

    /**
     * Ni la excepcion ni ninguna de sus causas puede llevar lo que contesto el modelo ni el texto
     * del mensaje: en T2 un log.warn("...", ex) imprimiria toda la cadena.
     */
    private static void sinDatosEnLaCadena(Throwable error, String... prohibidos) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            for (String prohibido : prohibidos) {
                assertThat(String.valueOf(t.getMessage())).doesNotContain(prohibido);
                assertThat(t.toString()).doesNotContain(prohibido);
            }
        }
    }

    private Throwable fallo(AnthropicTramiteExtractionService servicio) {
        Throwable error = catchThrowable(() -> servicio.extraer(MENSAJE));
        assertThat(error).isInstanceOf(ExtraccionFallidaException.class);
        return error;
    }

    private static List<String> textos(JsonNode array) {
        List<String> resultado = new ArrayList<>();
        StreamSupport.stream(array.spliterator(), false).forEach(n -> resultado.add(n.asText()));
        return resultado;
    }

    @Test
    void unaRespuestaValidaSeConvierteEnElResultadoConLosCrotalesTalCualSeEscribieron() throws Exception {
        responderMensaje("{\"tipoTramite\":\"ALTA\",\"crotales\":[\"1234\",\"ES-0100 0000 5678\"]}", "end_turn");

        TramiteExtraido extraido = servicio().extraer(MENSAJE);

        assertThat(extraido.tipoTramite()).isEqualTo(TipoTramite.ALTA);
        assertThat(extraido.crotales()).containsExactly("1234", "ES-0100 0000 5678");
    }

    @Test
    void noIdentificadoSeTraduceATipoNulo() throws Exception {
        responderMensaje("{\"tipoTramite\":\"NO_IDENTIFICADO\",\"crotales\":[]}", "end_turn");

        TramiteExtraido extraido = servicio().extraer("Buenos días");

        assertThat(extraido.tipoTramite()).isNull();
        assertThat(extraido.crotales()).isEmpty();
    }

    @Test
    void unaNegativaDelModeloEsExtraccionFallida() throws Exception {
        responderMensaje("", "refusal");

        assertThatThrownBy(() -> servicio().extraer(MENSAJE))
                .isInstanceOf(ExtraccionFallidaException.class)
                .hasMessageNotContaining("1234");
    }

    @Test
    void unaRespuestaCortadaPorMaxTokensEsExtraccionFallida() throws Exception {
        responderMensaje("{\"tipoTramite\":\"ALTA\",\"crotales\":[\"12", "max_tokens");

        assertThatThrownBy(() -> servicio().extraer(MENSAJE))
                .isInstanceOf(ExtraccionFallidaException.class)
                .hasMessageNotContaining("1234");
    }

    /** Aunque el texto sea un JSON valido, una negativa nunca se usa como extraccion. */
    @Test
    void unaNegativaConUnJsonValidoTambienEsExtraccionFallida() throws Exception {
        responderMensaje("{\"tipoTramite\":\"BAJA\",\"crotales\":[\"1234\"]}", "refusal");

        assertThatThrownBy(() -> servicio().extraer(MENSAJE)).isInstanceOf(ExtraccionFallidaException.class);
    }

    /** max_tokens puede cortar justo tras cerrar el JSON: tampoco se da por buena. */
    @Test
    void maxTokensConUnJsonCompletoTambienEsExtraccionFallida() throws Exception {
        responderMensaje("{\"tipoTramite\":\"BAJA\",\"crotales\":[\"1234\"]}", "max_tokens");

        assertThatThrownBy(() -> servicio().extraer(MENSAJE)).isInstanceOf(ExtraccionFallidaException.class);
    }

    @Test
    void unErrorHttp500EsExtraccionFallidaSinReintentosEnElTest() {
        responder(500, """
                {"type":"error","error":{"type":"api_error","message":"Internal server error"}}
                """);

        sinDatosEnLaCadena(fallo(servicio()), "1234", MENSAJE);
        assertThat(cuerposRecibidos).hasSize(1);
    }

    @Test
    void conReintentosElSdkRepiteUn500YLuegoFalla() {
        responder(500, """
                {"type":"error","error":{"type":"api_error","message":"Internal server error"}}
                """);
        String baseUrl = "http://127.0.0.1:" + servidor.getAddress().getPort();
        AnthropicTramiteExtractionService conReintentos = new AnthropicTramiteExtractionService(CLAVE_FALSA, baseUrl, 1);

        assertThatThrownBy(() -> conReintentos.extraer(MENSAJE)).isInstanceOf(ExtraccionFallidaException.class);
        assertThat(cuerposRecibidos).hasSize(2);
    }

    @Test
    void unErrorDeRedEsExtraccionFallida() {
        String baseUrl = "http://127.0.0.1:" + servidor.getAddress().getPort();
        servidor.stop(0);
        AnthropicTramiteExtractionService sinServidor = new AnthropicTramiteExtractionService(CLAVE_FALSA, baseUrl, 0);

        assertThatThrownBy(() -> sinServidor.extraer(MENSAJE)).isInstanceOf(ExtraccionFallidaException.class);
    }

    @Test
    void unTipoFueraDelEnumEsExtraccionFallida() throws Exception {
        responderMensaje("{\"tipoTramite\":\"VENTA\",\"crotales\":[\"9876\"]}", "end_turn");

        sinDatosEnLaCadena(fallo(servicio()), "9876", "VENTA", "1234");
    }

    @Test
    void unTextoQueNoEsJsonEsExtraccionFallida() throws Exception {
        responderMensaje("Es un alta del 9876", "end_turn");

        sinDatosEnLaCadena(fallo(servicio()), "9876", "Es un alta", "1234");
    }

    @Test
    void unJsonSinLosCamposObligatoriosEsExtraccionFallida() throws Exception {
        responderMensaje("{\"tipoTramite\":\"ALTA\"}", "end_turn");

        sinDatosEnLaCadena(fallo(servicio()), "1234");
    }

    @Test
    void unCrotalNuloEsExtraccionFallida() throws Exception {
        responderMensaje("{\"tipoTramite\":\"ALTA\",\"crotales\":[\"9876\",null]}", "end_turn");

        sinDatosEnLaCadena(fallo(servicio()), "9876", "1234");
    }

    /**
     * TipoExtraido (lo que puede contestar la IA) duplica TipoTramite mas NO_IDENTIFICADO. Si B2
     * cambia el catalogo y no los dos, este test lo dice antes de que valueOf falle en produccion
     * o la IA no pueda devolver un tipo nuevo.
     */
    @Test
    void tipoExtraidoTieneLosMismosValoresQueTipoTramiteMasNoIdentificado() {
        List<String> extraidos = new ArrayList<>();
        for (AnthropicTramiteExtractionService.TipoExtraido t : AnthropicTramiteExtractionService.TipoExtraido.values()) {
            extraidos.add(t.name());
        }
        List<String> esperados = new ArrayList<>();
        for (TipoTramite t : TipoTramite.values()) {
            esperados.add(t.name());
        }
        esperados.add("NO_IDENTIFICADO");

        assertThat(extraidos).containsExactlyInAnyOrderElementsOf(esperados);
    }

    @Test
    void cerrarSinClienteCreadoNoFallaYTrasCerrarSePuedeVolverAExtraer() throws Exception {
        AnthropicTramiteExtractionService servicio = servicio();
        servicio.cerrar();

        responderMensaje("{\"tipoTramite\":\"BAJA\",\"crotales\":[]}", "end_turn");
        assertThat(servicio.extraer(MENSAJE).tipoTramite()).isEqualTo(TipoTramite.BAJA);
        servicio.cerrar();
        assertThat(servicio.extraer(MENSAJE).tipoTramite()).isEqualTo(TipoTramite.BAJA);
        servicio.cerrar();
        assertThat(cuerposRecibidos).hasSize(2);
    }

    @Test
    void sinClaveConfiguradaEsExtraccionFallidaSinLlamarALaApi() {
        String baseUrl = "http://127.0.0.1:" + servidor.getAddress().getPort();
        AnthropicTramiteExtractionService sinClave = new AnthropicTramiteExtractionService("", baseUrl, 0);

        assertThatThrownBy(() -> sinClave.extraer(MENSAJE)).isInstanceOf(ExtraccionFallidaException.class);
        assertThat(cuerposRecibidos).isEmpty();
    }
}
