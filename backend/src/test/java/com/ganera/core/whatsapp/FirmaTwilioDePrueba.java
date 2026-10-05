package com.ganera.core.whatsapp;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

/**
 * Calcula la firma X-Twilio-Signature segun la especificacion publica de Twilio, sin usar su SDK:
 * URL completa + concatenacion de nombre y valor de cada parametro del POST, ordenados por nombre;
 * HMAC-SHA1 con el auth token como clave; Base64. Asi el test no depende de que el validador del
 * SDK y el calculo de la firma salgan del mismo codigo.
 */
final class FirmaTwilioDePrueba {

    private FirmaTwilioDePrueba() {
    }

    static String calcular(String authToken, String url, Map<String, String> parametros) {
        StringBuilder datos = new StringBuilder(url);
        new TreeMap<>(parametros).forEach((nombre, valor) -> datos.append(nombre).append(valor));
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(authToken.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(datos.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
