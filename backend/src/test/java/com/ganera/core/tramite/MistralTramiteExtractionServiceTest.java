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
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Sin mocks del cliente HTTP (skill test-sin-mocks-externos): el HttpClient del JDK habla HTTP con un
 * servidor local que hace de API de chat completions de Mistral. Las respuestas falsas tienen la forma
 * real (choices[].message.content con el JSON, finish_reason). Ninguna llamada sale a la red ni usa una
 * clave real.
 */
class MistralTramiteExtractionServiceTest {

    private static final String CLAVE_FALSA = "mistral-clave-inventada-para-tests";
    private static final String MODELO = "mistral-small-latest";
    private static final String MENSAJE = "Hola, quiero dar de alta los terneros 1234 y ES010000005678";

    private final ObjectMapper json = new ObjectMapper();
    private final List<String> cuerposRecibidos = new CopyOnWriteArrayList<>();
    private final List<String> autorizaciones = new CopyOnWriteArrayList<>();
    private HttpServer servidor;
    private volatile int estadoHttp;
    private volatile String respuesta;

    @BeforeEach
    void arrancarServidor() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        servidor.createContext("/", intercambio -> {
            autorizaciones.add(intercambio.getRequestHeaders().getFirst("Authorization"));
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

    private String baseUrl() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    private MistralTramiteExtractionService servicio() {
        return new MistralTramiteExtractionService(CLAVE_FALSA, baseUrl(), MODELO, 0);
    }

    private void responder(int estado, String cuerpo) {
        this.estadoHttp = estado;
        this.respuesta = cuerpo;
    }

    /** Respuesta con la forma real de chat completions: un choice con content (el JSON) y finish_reason. */
    private void responderContenido(String contenidoJson, String finishReason) throws IOException {
        String cuerpo = """
                {"id":"cmpl-prueba","object":"chat.completion","model":"mistral-small-latest",
                 "choices":[{"index":0,"message":{"role":"assistant","content":%s},"finish_reason":"%s"}],
                 "usage":{"prompt_tokens":120,"completion_tokens":30,"total_tokens":150}}
                """.formatted(json.writeValueAsString(contenidoJson), finishReason);
        responder(200, cuerpo);
    }

    private static void sinDatosEnLaCadena(Throwable error, String... prohibidos) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            for (String prohibido : prohibidos) {
                assertThat(String.valueOf(t.getMessage())).doesNotContain(prohibido);
                assertThat(t.toString()).doesNotContain(prohibido);
            }
        }
    }

    private Throwable fallo(MistralTramiteExtractionService servicio) {
        Throwable error = catchThrowable(() -> servicio.extraer(MENSAJE));
        assertThat(error).isInstanceOf(ExtraccionFallidaException.class);
        return error;
    }

    @Test
    void laPeticionLlevaModeloMaxTokensModoJsonYLosDosMensajes() throws Exception {
        responderContenido("{\"tipoTramite\":\"ALTA_NACIMIENTO\",\"crotales\":[\"1234\"]}", "stop");

        servicio().extraer(MENSAJE);

        assertThat(cuerposRecibidos).hasSize(1);
        assertThat(autorizaciones.get(0)).isEqualTo("Bearer " + CLAVE_FALSA);
        JsonNode peticion = json.readTree(cuerposRecibidos.get(0));
        assertThat(peticion.path("model").asText()).isEqualTo(MODELO);
        assertThat(peticion.path("max_tokens").asLong()).isEqualTo(1024);
        assertThat(peticion.path("temperature").asInt()).isEqualTo(0);
        assertThat(peticion.path("response_format").path("type").asText()).isEqualTo("json_object");
        assertThat(peticion.path("messages")).hasSize(2);
        assertThat(peticion.path("messages").get(0).path("role").asText()).isEqualTo("system");
        assertThat(peticion.path("messages").get(0).path("content").asText())
                .contains("NO_IDENTIFICADO")
                .contains("Responde SOLO con un objeto JSON")
                .doesNotContain("1234");
        assertThat(peticion.path("messages").get(1).path("role").asText()).isEqualTo("user");
        assertThat(peticion.path("messages").get(1).path("content").asText()).isEqualTo(MENSAJE);
    }

    @Test
    void unaRespuestaValidaSeConvierteConLosCrotalesTalCualSeEscribieron() throws Exception {
        responderContenido("{\"tipoTramite\":\"ALTA_NACIMIENTO\",\"crotales\":[\"1234\",\"ES-0100 0000 5678\"]}", "stop");

        TramiteExtraido extraido = servicio().extraer(MENSAJE);

        assertThat(extraido.tipoTramite()).isEqualTo(TipoTramite.ALTA_NACIMIENTO);
        assertThat(extraido.crotales()).containsExactly("1234", "ES-0100 0000 5678");
        assertThat(extraido.sinTramite()).isFalse();
    }

    @Test
    void noIdentificadoSeTraduceATipoNulo() throws Exception {
        responderContenido("{\"tipoTramite\":\"NO_IDENTIFICADO\",\"crotales\":[]}", "stop");

        TramiteExtraido extraido = servicio().extraer("Buenos días");

        assertThat(extraido.tipoTramite()).isNull();
        assertThat(extraido.crotales()).isEmpty();
        assertThat(extraido.sinTramite()).isFalse();
    }

    @Test
    void sinTramiteSeTraduceATipoNuloConSinTramite() throws Exception {
        responderContenido("{\"tipoTramite\":\"SIN_TRAMITE\",\"crotales\":[]}", "stop");

        TramiteExtraido extraido = servicio().extraer("vale, gracias");

        assertThat(extraido.tipoTramite()).isNull();
        assertThat(extraido.sinTramite()).isTrue();
    }

    @Test
    void sinTramiteConCrotalesLosEntregaTalCual() throws Exception {
        responderContenido("{\"tipoTramite\":\"SIN_TRAMITE\",\"crotales\":[\"4521\"]}", "stop");

        TramiteExtraido extraido = servicio().extraer("gracias, la 4521");

        assertThat(extraido.tipoTramite()).isNull();
        assertThat(extraido.crotales()).containsExactly("4521");
        assertThat(extraido.sinTramite()).isTrue();
    }

    @Test
    void cadaTipoDeDominioQueContestaLaIaSeConvierteEnElMismoTipo() throws Exception {
        for (TipoTramite tipo : TipoTramite.values()) {
            responderContenido("{\"tipoTramite\":\"" + tipo.name() + "\",\"crotales\":[]}", "stop");

            TramiteExtraido extraido = servicio().extraer(MENSAJE);
            assertThat(extraido.tipoTramite()).isEqualTo(tipo);
            assertThat(extraido.sinTramite()).isFalse();
        }
    }

    @Test
    void unFinishReasonDistintoDeStopEsExtraccionFallida() throws Exception {
        responderContenido("{\"tipoTramite\":\"ALTA_NACIMIENTO\",\"crotales\":[\"12", "length");

        assertThatThrownBy(() -> servicio().extraer(MENSAJE))
                .isInstanceOf(ExtraccionFallidaException.class)
                .hasMessageContaining("length")
                .hasMessageNotContaining("1234");
    }

    @Test
    void unErrorHttp500EsExtraccionFallidaSinReintentosEnElTest() {
        responder(500, "{\"message\":\"internal error\"}");

        sinDatosEnLaCadena(fallo(servicio()), "1234", MENSAJE);
        assertThat(cuerposRecibidos).hasSize(1);
    }

    @Test
    void conReintentosRepiteUn500YLuegoFalla() {
        responder(500, "{\"message\":\"internal error\"}");
        MistralTramiteExtractionService conReintentos =
                new MistralTramiteExtractionService(CLAVE_FALSA, baseUrl(), MODELO, 1);

        assertThatThrownBy(() -> conReintentos.extraer(MENSAJE)).isInstanceOf(ExtraccionFallidaException.class);
        assertThat(cuerposRecibidos).hasSize(2);
    }

    @Test
    void un401NoSeReintentaAunqueHayaReintentos() {
        responder(401, "{\"message\":\"unauthorized\"}");
        MistralTramiteExtractionService conReintentos =
                new MistralTramiteExtractionService(CLAVE_FALSA, baseUrl(), MODELO, 2);

        assertThatThrownBy(() -> conReintentos.extraer(MENSAJE)).isInstanceOf(ExtraccionFallidaException.class);
        assertThat(cuerposRecibidos).hasSize(1);
    }

    @Test
    void unErrorDeRedEsExtraccionFallida() {
        String baseUrl = baseUrl();
        servidor.stop(0);
        MistralTramiteExtractionService sinServidor =
                new MistralTramiteExtractionService(CLAVE_FALSA, baseUrl, MODELO, 0);

        assertThatThrownBy(() -> sinServidor.extraer(MENSAJE)).isInstanceOf(ExtraccionFallidaException.class);
    }

    @Test
    void unContenidoQueNoEsJsonEsExtraccionFallida() throws Exception {
        responderContenido("Es un alta del 9876", "stop");

        sinDatosEnLaCadena(fallo(servicio()), "9876", "Es un alta", "1234");
    }

    @Test
    void unTipoFueraDelCatalogoEsExtraccionFallida() throws Exception {
        responderContenido("{\"tipoTramite\":\"VENTA\",\"crotales\":[\"9876\"]}", "stop");

        sinDatosEnLaCadena(fallo(servicio()), "VENTA", "9876", "1234");
    }

    @Test
    void unContenidoSinLosCamposObligatoriosEsExtraccionFallida() throws Exception {
        responderContenido("{}", "stop");

        sinDatosEnLaCadena(fallo(servicio()), "1234");
    }

    @Test
    void unCrotalQueNoEsTextoEsExtraccionFallida() throws Exception {
        responderContenido("{\"tipoTramite\":\"ALTA_NACIMIENTO\",\"crotales\":[\"9876\",123]}", "stop");

        sinDatosEnLaCadena(fallo(servicio()), "9876", "1234");
    }

    @Test
    void sinClaveConfiguradaEsExtraccionFallidaSinLlamarALaApi() {
        MistralTramiteExtractionService sinClave =
                new MistralTramiteExtractionService("", baseUrl(), MODELO, 0);

        assertThatThrownBy(() -> sinClave.extraer(MENSAJE)).isInstanceOf(ExtraccionFallidaException.class);
        assertThat(cuerposRecibidos).isEmpty();
    }

    @Test
    void cerrarSinClienteCreadoNoFallaYTrasCerrarSePuedeVolverAExtraer() throws Exception {
        MistralTramiteExtractionService servicio = servicio();
        servicio.cerrar();

        responderContenido("{\"tipoTramite\":\"BAJA_MUERTE\",\"crotales\":[]}", "stop");
        assertThat(servicio.extraer(MENSAJE).tipoTramite()).isEqualTo(TipoTramite.BAJA_MUERTE);
        servicio.cerrar();
        assertThat(servicio.extraer(MENSAJE).tipoTramite()).isEqualTo(TipoTramite.BAJA_MUERTE);
        servicio.cerrar();
        assertThat(cuerposRecibidos).hasSize(2);
    }

    @Test
    void elModeloPorDefectoSeUsaCuandoNoSeIndicaYElBaseUrlPorDefectoApuntaAMistral() {
        MistralTramiteExtractionService porDefecto =
                new MistralTramiteExtractionService(CLAVE_FALSA, "", "", 0);

        assertThat(MistralTramiteExtractionService.MODELO_POR_DEFECTO).isEqualTo("mistral-small-latest");
        assertThat(MistralTramiteExtractionService.BASE_URL_POR_DEFECTO).isEqualTo("https://api.mistral.ai");
        // Con base-url por defecto no se puede llamar en el test (saldria a la red), pero construirlo no falla.
        assertThat(porDefecto).isNotNull();
    }

    @Test
    void elPromptReutilizaElDeAnthropicYAnadeElContratoJson() {
        assertThat(MistralTramiteExtractionService.PROMPT_SISTEMA)
                .startsWith(AnthropicTramiteExtractionService.PROMPT_SISTEMA)
                .contains("Responde SOLO con un objeto JSON");
    }
}
