package com.ganera.core.whatsapp;

/**
 * Puerto de envio de un mensaje de WhatsApp (B2 reducido, T1). La implementacion real es
 * {@link TwilioEnvioWhatsapp}; los tests usan un falso y nunca llaman a Twilio.
 */
public interface EnvioWhatsapp {

    /** Si hay credenciales y remitente: sin ellos el endpoint responde 503 sin crear nada. */
    boolean configurado();

    /**
     * Envia {@code texto} tal cual a {@code telefonoE164}. Una sola llamada: nunca reintenta (Twilio no
     * admite clave de idempotencia). No lanza: cualquier fallo es Rechazado o SinConfirmar.
     */
    ResultadoEnvioWhatsapp enviar(String telefonoE164, String texto);
}
