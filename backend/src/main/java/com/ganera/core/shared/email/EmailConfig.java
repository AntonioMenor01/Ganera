package com.ganera.core.shared.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Construye el {@link JavaMailSender} a partir de {@code ganera.email.*} (no de {@code spring.mail.*}), para
 * mantener toda la configuracion de email bajo el espacio {@code ganera.*} del proyecto.
 *
 * <p>El bean se crea siempre, tambien con la config en blanco: es inofensivo porque {@link JavaMailSenderImpl}
 * no abre ninguna conexion hasta el primer envio, y {@link SmtpEmailService} solo lo usa cuando el email esta
 * activo y configurado. Asi la app arranca igual con el email apagado (el valor por defecto).
 *
 * <p>{@link ConditionalOnMissingBean} deja ganar a la autoconfiguracion de Boot si algun dia se definiera
 * {@code spring.mail.host}; por defecto Boot se abstiene (no hay host), asi que este es el unico.
 */
@Configuration
public class EmailConfig {

    @Bean
    @ConditionalOnMissingBean(JavaMailSender.class)
    public JavaMailSender javaMailSender(
            @Value("${ganera.email.host:}") String host,
            @Value("${ganera.email.puerto:587}") int puerto,
            @Value("${ganera.email.usuario:}") String usuario,
            @Value("${ganera.email.password:}") String password) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(puerto);
        sender.setUsername(usuario);
        sender.setPassword(password);
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());

        Properties propiedades = sender.getJavaMailProperties();
        propiedades.put("mail.transport.protocol", "smtp");
        // Solo autentica si hay usuario; STARTTLS para el puerto 587 habitual.
        propiedades.put("mail.smtp.auth", String.valueOf(usuario != null && !usuario.isBlank()));
        propiedades.put("mail.smtp.starttls.enable", "true");
        return sender;
    }
}
