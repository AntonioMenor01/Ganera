package com.ganera.core.tramite;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PreDestroy;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Extraccion con Mistral mediante su API de chat completions (compatible con el estilo OpenAI). Es la
 * segunda implementacion elegible de {@link TramiteExtractionService}: existe para poder comparar
 * Mistral con Haiku, no para sustituirlo. Anthropic sigue siendo el proveedor por defecto; esta solo
 * se usa cuando {@code AI_PROVIDER=mistral} (ver {@link ProveedorIaConfig}).
 *
 * <p>Mistral no tiene salida estructurada nativa por esquema como Anthropic, asi que se pide
 * {@code response_format: json_object} y se valida el JSON aqui: el {@code content} del mensaje debe
 * ser un objeto con {@code tipoTramite} (uno de {@link AnthropicTramiteExtractionService.TipoExtraido})
 * y {@code crotales} (lista de cadenas). El servidor vuelve a validar la forma. Reutiliza el mismo
 * prompt de dominio que Anthropic ({@link AnthropicTramiteExtractionService#PROMPT_SISTEMA}) para que
 * la comparacion sea justa, y le añade el contrato de salida en JSON.
 *
 * <p>Sin SDK propio: se usa el {@link HttpClient} del JDK para no añadir dependencias y poder probarlo
 * con un servidor HTTP local (skill test-sin-mocks-externos). El cliente se construye la primera vez
 * que se usa, no al arrancar: la app levanta sin {@code MISTRAL_API_KEY} (mismo criterio que Anthropic,
 * Twilio y Stripe). Sin clave, cada llamada es una {@link ExtraccionFallidaException} y no sale ninguna
 * peticion.
 *
 * <p>Como en Anthropic, ni la excepcion ni sus causas llevan nunca el texto del mensaje ni lo que
 * contesto el modelo: un {@code log.warn("...", ex)} en T2 imprimiria toda la cadena.
 */
public class MistralTramiteExtractionService implements TramiteExtractionService {

    static final String MODELO_POR_DEFECTO = "mistral-small-latest";
    static final String BASE_URL_POR_DEFECTO = "https://api.mistral.ai";
    static final long MAX_TOKENS = 1024;
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    /** El prompt de dominio es el mismo que el de Anthropic; aqui solo se añade el contrato de salida. */
    static final String CONTRATO_JSON = """


            Responde SOLO con un objeto JSON, sin texto alrededor ni ```:
            {"tipoTramite": "<uno de: ALTA_NACIMIENTO, BAJA_MUERTE, SOLICITUD_MOVIMIENTO, \
            CONFIRMACION_MOVIMIENTO, DECLARACION_CENSO, DEMORA_CROTALIZACION, NO_IDENTIFICADO, \
            SIN_TRAMITE>", "crotales": ["<cada crotal tal cual, o lista vacia>"]}""";

    static final String PROMPT_SISTEMA = AnthropicTramiteExtractionService.PROMPT_SISTEMA + CONTRATO_JSON;

    private final String apiKey;
    private final String baseUrl;
    private final String modelo;
    private final int maxReintentos;
    private final ObjectMapper json = new ObjectMapper();
    private volatile HttpClient cliente;

    public MistralTramiteExtractionService(String apiKey, String baseUrl, String modelo, int maxReintentos) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        String url = baseUrl == null ? "" : baseUrl.trim();
        this.baseUrl = url.isEmpty() ? BASE_URL_POR_DEFECTO : url.replaceAll("/+$", "");
        String m = modelo == null ? "" : modelo.trim();
        this.modelo = m.isEmpty() ? MODELO_POR_DEFECTO : m;
        if (maxReintentos < 0) {
            throw new IllegalArgumentException("maxReintentos no puede ser negativo");
        }
        this.maxReintentos = maxReintentos;
    }

    @Override
    public TramiteExtraido extraer(String mensajeOriginal) {
        HttpRequest peticion = construirPeticion(mensajeOriginal);
        HttpResponse<String> respuesta = enviarConReintentos(peticion);
        if (respuesta.statusCode() < 200 || respuesta.statusCode() >= 300) {
            throw new ExtraccionFallidaException("La API de Mistral respondió con error HTTP " + respuesta.statusCode());
        }
        return convertir(respuesta.body());
    }

    private HttpRequest construirPeticion(String mensajeOriginal) {
        ObjectNode cuerpo = json.createObjectNode();
        cuerpo.put("model", modelo);
        cuerpo.put("max_tokens", MAX_TOKENS);
        // Determinista para la extraccion: no queremos creatividad.
        cuerpo.put("temperature", 0);
        cuerpo.putObject("response_format").put("type", "json_object");
        ArrayNode mensajes = cuerpo.putArray("messages");
        ObjectNode sistema = mensajes.addObject();
        sistema.put("role", "system");
        sistema.put("content", PROMPT_SISTEMA);
        ObjectNode usuario = mensajes.addObject();
        usuario.put("role", "user");
        usuario.put("content", mensajeOriginal == null ? "" : mensajeOriginal);

        byte[] bytes;
        try {
            bytes = json.writeValueAsBytes(cuerpo);
        } catch (IOException e) {
            // Serializar un ObjectNode no deberia fallar; sin encadenar la causa por si acaso.
            throw new ExtraccionFallidaException("No se ha podido construir la petición para Mistral");
        }
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/chat/completions"))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .build();
    }

    /**
     * Reintenta en error de red y en 429/5xx, hasta {@code maxReintentos} veces (como el SDK de
     * Anthropic). Nunca reintenta un 4xx distinto de 429 (p. ej. 401 clave mala): no cambiaria.
     */
    private HttpResponse<String> enviarConReintentos(HttpRequest peticion) {
        IOException ultimaRed = null;
        for (int intento = 0; intento <= maxReintentos; intento++) {
            try {
                HttpResponse<String> respuesta = cliente().send(peticion, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int estado = respuesta.statusCode();
                if ((estado == 429 || estado >= 500) && intento < maxReintentos) {
                    continue;
                }
                return respuesta;
            } catch (IOException e) {
                ultimaRed = e;
                // Se reintenta mientras queden intentos; si no, cae al fallo de abajo.
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ExtraccionFallidaException("Interrumpida la llamada a la API de Mistral");
            }
        }
        // Sin encadenar la causa: el mensaje de una IOException puede llevar la URL con la clave o datos.
        throw new ExtraccionFallidaException("Error de red al llamar a la API de Mistral"
                + (ultimaRed == null ? "" : " (" + ultimaRed.getClass().getSimpleName() + ")"));
    }

    private TramiteExtraido convertir(String cuerpoRespuesta) {
        JsonNode raiz;
        try {
            raiz = json.readTree(cuerpoRespuesta);
        } catch (IOException e) {
            throw new ExtraccionFallidaException("La respuesta de Mistral no es un JSON válido");
        }
        JsonNode choice = raiz.path("choices").path(0);
        String finish = choice.path("finish_reason").asText("");
        if (!finish.equals("stop")) {
            // length/model_length/content_filter/... : respuesta no utilizable. El motivo es un enum
            // cerrado de la API (no datos del mensaje), asi que puede ir en el mensaje.
            throw new ExtraccionFallidaException("Mistral no terminó la respuesta (finish_reason "
                    + (finish.isEmpty() ? "ausente" : finish) + ")");
        }
        String contenido = choice.path("message").path("content").asText(null);
        if (contenido == null || contenido.isBlank()) {
            throw new ExtraccionFallidaException("La respuesta de Mistral no trae contenido de texto");
        }
        JsonNode salida;
        try {
            salida = json.readTree(contenido);
        } catch (IOException e) {
            throw new ExtraccionFallidaException("El contenido de Mistral no es un JSON válido");
        }
        return mapear(salida);
    }

    private TramiteExtraido mapear(JsonNode salida) {
        JsonNode tipoNodo = salida.path("tipoTramite");
        JsonNode crotalesNodo = salida.path("crotales");
        if (!tipoNodo.isTextual() || !crotalesNodo.isArray()) {
            throw new ExtraccionFallidaException("La respuesta de Mistral no cumple el esquema");
        }
        List<String> crotales = new ArrayList<>();
        for (JsonNode c : crotalesNodo) {
            if (!c.isTextual()) {
                throw new ExtraccionFallidaException("La respuesta de Mistral trae un crotal que no es texto");
            }
            crotales.add(c.asText());
        }
        AnthropicTramiteExtractionService.TipoExtraido tipoExtraido;
        try {
            tipoExtraido = AnthropicTramiteExtractionService.TipoExtraido.valueOf(tipoNodo.asText());
        } catch (IllegalArgumentException e) {
            // Sin incluir el valor: podria ser cualquier cosa que haya contestado el modelo.
            throw new ExtraccionFallidaException("Mistral devolvió un tipo de trámite fuera del catálogo");
        }
        boolean sinTramite = tipoExtraido == AnthropicTramiteExtractionService.TipoExtraido.SIN_TRAMITE;
        TipoTramite tipo = tipoExtraido == AnthropicTramiteExtractionService.TipoExtraido.NO_IDENTIFICADO || sinTramite
                ? null
                : TipoTramite.valueOf(tipoExtraido.name());
        return new TramiteExtraido(tipo, List.copyOf(crotales), sinTramite);
    }

    /** Cierra el cliente HTTP del JDK si llego a crearse (igual que Anthropic cierra el suyo). */
    @PreDestroy
    public void cerrar() {
        HttpClient actual;
        synchronized (this) {
            actual = cliente;
            cliente = null;
        }
        if (actual != null) {
            actual.close();
        }
    }

    private HttpClient cliente() {
        if (apiKey.isEmpty()) {
            throw new ExtraccionFallidaException("MISTRAL_API_KEY no está configurada");
        }
        HttpClient actual = cliente;
        if (actual == null) {
            synchronized (this) {
                actual = cliente;
                if (actual == null) {
                    actual = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
                    cliente = actual;
                }
            }
        }
        return actual;
    }
}
