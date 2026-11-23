package com.ganera.core.shared.email;

/**
 * Pieza reutilizable para enviar email por SMTP. APAGADA por defecto ({@code ganera.email.activo=false}).
 *
 * <p><b>Fail-closed.</b> Si el email esta desactivado o falta la configuracion SMTP (host o remitente en
 * blanco), ningun metodo envia: registran un WARN y devuelven {@code false}, <b>nunca lanzan</b>. Un fallo del
 * servidor SMTP tampoco se propaga (se registra y se devuelve {@code false}). Asi un flujo que llame a este
 * servicio nunca se rompe por culpa del email.
 *
 * <p>Todavia NO esta cableada a ningun flujo: es solo la infraestructura. La implementacion de produccion es
 * {@link SmtpEmailService}.
 */
public interface EmailService {

    /**
     * Envia un email con cuerpo HTML.
     *
     * @param destinatario direccion de destino
     * @param asunto       asunto del mensaje
     * @param cuerpoHtml   cuerpo en HTML
     * @return {@code true} si se entrego al servidor SMTP; {@code false} si no se envio (desactivado, sin
     * configurar o error de envio). Nunca lanza.
     */
    boolean enviarHtml(String destinatario, String asunto, String cuerpoHtml);

    /**
     * Envia un email con cuerpo de texto plano.
     *
     * @param destinatario direccion de destino
     * @param asunto       asunto del mensaje
     * @param cuerpoTexto  cuerpo en texto plano
     * @return {@code true} si se entrego al servidor SMTP; {@code false} si no se envio (desactivado, sin
     * configurar o error de envio). Nunca lanza.
     */
    boolean enviarTexto(String destinatario, String asunto, String cuerpoTexto);

    /**
     * @return {@code true} si el email esta activo y la configuracion SMTP minima (host y remitente) esta
     * presente. Cuando es {@code false}, los metodos de envio no envian nada.
     */
    boolean configurado();
}
