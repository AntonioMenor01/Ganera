package com.ganera.core.whatsapp;

import com.twilio.http.NetworkHttpClient;
import com.twilio.http.Response;
import com.twilio.http.TwilioRestClient;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.config.RequestConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Envio real por la API de mensajes de Twilio (B2 reducido, T1).
 *
 * <ul>
 *   <li>Lee {@code twilio.account-sid}, {@code twilio.auth-token} y {@code ganera.twilio.whatsapp-from}
 *       (remitente, p. ej. el del sandbox; se normaliza a {@code whatsapp:+…}). Sin las tres
 *       {@link #configurado()} es false y el endpoint responde 503.</li>
 *   <li>El {@link TwilioRestClient} se crea de forma perezosa en el primer envio, NUNCA en el
 *       constructor (footgun de Twilio de B1: la app tiene que arrancar con todo vacio), con timeouts
 *       explicitos de conexion y lectura.</li>
 *   <li>Una sola llamada, sin reintentos. La clasificacion del resultado es la funcion pura
 *       {@link ClasificadorEnvioWhatsapp}; aqui no hay logica que probar sin red, asi que esta clase no
 *       tiene test unitario que llame a Twilio (se valida en el envio real que hace Antonio).</li>
 *   <li>Logs: solo la clase de la excepcion y el codigo de Twilio; nunca el mensaje de la excepcion
 *       (puede llevar el numero), ni el texto, ni el telefono.</li>
 * </ul>
 */
@Slf4j
@Component
public class TwilioEnvioWhatsapp implements EnvioWhatsapp {

    static final int TIMEOUT_MS = 10_000;

    private final String accountSid;
    private final String authToken;
    private final String remitente;

    private volatile TwilioRestClient cliente;

    @Autowired
    public TwilioEnvioWhatsapp(
            @Value("${twilio.account-sid:}") String accountSid,
            @Value("${twilio.auth-token:}") String authToken,
            @Value("${ganera.twilio.whatsapp-from:}") String remitente) {
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.remitente = ClasificadorEnvioWhatsapp.normalizarRemitente(remitente);
    }

    @Override
    public boolean configurado() {
        return accountSid != null && !accountSid.isBlank()
                && authToken != null && !authToken.isBlank()
                && remitente != null;
    }

    @Override
    public ResultadoEnvioWhatsapp enviar(String telefonoE164, String texto) {
        if (!configurado()) {
            // El servicio lo comprueba antes (503); esto solo evita construir un cliente sin credenciales.
            log.warn("Envio por WhatsApp sin configurar: no se llama a Twilio");
            return new ResultadoEnvioWhatsapp.Rechazado(null);
        }
        try {
            Message mensaje = Message.creator(
                            new PhoneNumber(ClasificadorEnvioWhatsapp.destino(telefonoE164)),
                            new PhoneNumber(remitente),
                            texto)
                    .create(cliente());
            return ClasificadorEnvioWhatsapp.aceptado(mensaje.getSid());
        } catch (RuntimeException e) {
            log.warn("Twilio no acepto el envio por WhatsApp: excepcion={}, status={}, codigo={}",
                    e.getClass().getName(), e instanceof com.twilio.exception.ApiException api ? api.getStatusCode() : null,
                    ClasificadorEnvioWhatsapp.codigo(e));
            return ClasificadorEnvioWhatsapp.clasificar(e);
        }
    }

    /** Si ya se creo el cliente de Twilio (solo para el test de arranque). */
    boolean clienteCreado() {
        return cliente != null;
    }

    private TwilioRestClient cliente() {
        TwilioRestClient actual = cliente;
        if (actual == null) {
            synchronized (this) {
                actual = cliente;
                if (actual == null) {
                    actual = new TwilioRestClient.Builder(accountSid, authToken)
                            .httpClient(new HttpClientSinReintentos(tiempos()))
                            .build();
                    cliente = actual;
                }
            }
        }
        return actual;
    }

    /** Timeouts explicitos de conexion, de espera de conexion del pool y de lectura. */
    static RequestConfig tiempos() {
        return RequestConfig.custom()
                .setConnectTimeout(TIMEOUT_MS)
                .setConnectionRequestTimeout(TIMEOUT_MS)
                .setSocketTimeout(TIMEOUT_MS)
                .build();
    }

    /**
     * HttpClient de Twilio que NUNCA reintenta (revision T1, I1). En twilio 10.6.4,
     * TwilioRestClient.request llama a HttpClient.reliableRequest(Request), que delega en
     * reliableRequest(Request, RETRY_CODES = {ANY_500}, RETRIES = 3, DELAY_MILLIS = 100): llama a
     * makeRequest y repite mientras {@code shouldRetry(Response, int[])} sea true, es decir, ante
     * cualquier 5xx. Para un POST de mensaje eso puede enviar el MISMO WhatsApp hasta 3 veces (un 502/504
     * de pasarela puede llegar con el mensaje ya creado) y Twilio no admite clave de idempotencia. Se
     * sobrescribe shouldRetry (protected) para que devuelva siempre false: una sola peticion; un 5xx
     * acaba en ApiException y el clasificador lo deja en SIN_CONFIRMAR. Comprobado con javap sobre el jar.
     */
    static class HttpClientSinReintentos extends NetworkHttpClient {

        HttpClientSinReintentos(RequestConfig tiempos) {
            super(tiempos);
        }

        @Override
        protected boolean shouldRetry(Response respuesta, int[] codigosDeReintento) {
            return false;
        }

        /** Lo que decidiria reliableRequest ante esta respuesta (para los tests, sin red). */
        boolean reintentaria(Response respuesta) {
            return shouldRetry(respuesta, RETRY_CODES);
        }
    }
}
