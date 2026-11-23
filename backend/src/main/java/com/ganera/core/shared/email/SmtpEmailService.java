package com.ganera.core.shared.email;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * Implementacion SMTP de {@link EmailService} sobre {@link JavaMailSender}.
 *
 * <ul>
 *   <li><b>Fail-closed.</b> Con {@code ganera.email.activo=false} (el valor por defecto) o si falta la config
 *       SMTP ({@code host} o {@code from} en blanco), {@link #configurado()} es {@code false} y los metodos de
 *       envio no llaman a {@link JavaMailSender}: registran un WARN y devuelven {@code false}.</li>
 *   <li>Un error al construir o enviar el mensaje se captura: se registra la clase de la excepcion (nunca su
 *       mensaje ni el cuerpo del email) y se devuelve {@code false}. <b>Nunca lanza</b>, para no romper al que
 *       lo llame.</li>
 *   <li>Logs sin datos personales: ni destinatario, ni asunto, ni cuerpo.</li>
 * </ul>
 *
 * <p>Pieza reutilizable; todavia no esta cableada a ningun flujo.
 */
@Slf4j
@Service
public class SmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;
    private final boolean activo;
    private final String host;
    private final String from;

    public SmtpEmailService(
            JavaMailSender mailSender,
            @Value("${ganera.email.activo:false}") boolean activo,
            @Value("${ganera.email.host:}") String host,
            @Value("${ganera.email.from:}") String from) {
        this.mailSender = mailSender;
        this.activo = activo;
        this.host = host;
        this.from = from;
    }

    @Override
    public boolean configurado() {
        return activo
                && host != null && !host.isBlank()
                && from != null && !from.isBlank();
    }

    @Override
    public boolean enviarHtml(String destinatario, String asunto, String cuerpoHtml) {
        return enviar(destinatario, asunto, cuerpoHtml, true);
    }

    @Override
    public boolean enviarTexto(String destinatario, String asunto, String cuerpoTexto) {
        return enviar(destinatario, asunto, cuerpoTexto, false);
    }

    private boolean enviar(String destinatario, String asunto, String cuerpo, boolean html) {
        if (!configurado()) {
            log.warn("Email desactivado o sin configurar (ganera.email.activo={}, host/from presentes={}): "
                    + "no se envia", activo, host != null && !host.isBlank() && from != null && !from.isBlank());
            return false;
        }
        try {
            MimeMessage mensaje = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, false, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(destinatario);
            helper.setSubject(asunto);
            helper.setText(cuerpo, html);
            mailSender.send(mensaje);
            return true;
        } catch (Exception e) {
            // Nunca propagar: el email no debe romper el flujo que lo llama. Sin datos personales en el log.
            log.warn("No se pudo enviar el email: {}", e.getClass().getName());
            return false;
        }
    }
}
