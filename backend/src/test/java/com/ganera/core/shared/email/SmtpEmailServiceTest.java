package com.ganera.core.shared.email;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Comprueba el comportamiento fail-closed del servicio de email (sin SMTP configurado NO intenta enviar) y que,
 * con la configuracion presente, construye y entrega el mensaje al {@link JavaMailSender}. Sin red: el
 * JavaMailSender es un mock y nunca se abre una conexion SMTP real.
 */
class SmtpEmailServiceTest {

    private static final String HOST = "smtp.ejemplo.test";
    private static final String FROM = "Ganera <no-responder@ganera.es>";
    private static final String DESTINATARIO = "gestor@gestoria.test";
    private static final String ASUNTO = "Tu cuenta de Ganera";
    private static final String CUERPO = "<p>Hola</p>";

    private SmtpEmailService servicio(JavaMailSender sender, boolean activo, String host, String from) {
        return new SmtpEmailService(sender, activo, host, from);
    }

    @Test
    void desactivadoNoIntentaEnviar() {
        JavaMailSender sender = mock(JavaMailSender.class);
        SmtpEmailService servicio = servicio(sender, false, HOST, FROM);

        boolean enviado = servicio.enviarHtml(DESTINATARIO, ASUNTO, CUERPO);

        assertThat(enviado).isFalse();
        assertThat(servicio.configurado()).isFalse();
        verifyNoInteractions(sender);
    }

    @Test
    void activoPeroSinHostNoIntentaEnviar() {
        JavaMailSender sender = mock(JavaMailSender.class);
        SmtpEmailService servicio = servicio(sender, true, "  ", FROM);

        boolean enviado = servicio.enviarHtml(DESTINATARIO, ASUNTO, CUERPO);

        assertThat(enviado).isFalse();
        assertThat(servicio.configurado()).isFalse();
        verifyNoInteractions(sender);
    }

    @Test
    void activoPeroSinRemitenteNoIntentaEnviar() {
        JavaMailSender sender = mock(JavaMailSender.class);
        SmtpEmailService servicio = servicio(sender, true, HOST, "");

        boolean enviado = servicio.enviarTexto(DESTINATARIO, ASUNTO, "hola");

        assertThat(enviado).isFalse();
        assertThat(servicio.configurado()).isFalse();
        verifyNoInteractions(sender);
    }

    @Test
    void configuradoYActivoConstruyeYEnviaElMensajeHtml() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenReturn(new JavaMailSenderImpl().createMimeMessage());
        SmtpEmailService servicio = servicio(sender, true, HOST, FROM);

        boolean enviado = servicio.enviarHtml(DESTINATARIO, ASUNTO, CUERPO);

        assertThat(enviado).isTrue();
        assertThat(servicio.configurado()).isTrue();

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(captor.capture());
        MimeMessage enviado0 = captor.getValue();
        assertThat(enviado0.getAllRecipients()).hasSize(1);
        assertThat(enviado0.getAllRecipients()[0].toString()).isEqualTo(DESTINATARIO);
        assertThat(enviado0.getSubject()).isEqualTo(ASUNTO);
        assertThat(enviado0.getFrom()).isNotEmpty();
        // getContentType() no se sincroniza hasta saveChanges(); el tipo real lo lleva el DataHandler.
        assertThat(enviado0.getDataHandler().getContentType()).contains("text/html");
    }

    @Test
    void configuradoYActivoEnviaTextoPlano() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenReturn(new JavaMailSenderImpl().createMimeMessage());
        SmtpEmailService servicio = servicio(sender, true, HOST, FROM);

        boolean enviado = servicio.enviarTexto(DESTINATARIO, ASUNTO, "cuerpo en texto");

        assertThat(enviado).isTrue();
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(captor.capture());
        assertThat(captor.getValue().getDataHandler().getContentType()).contains("text/plain");
    }

    @Test
    void unErrorDeEnvioNoSePropagaYDevuelveFalse() {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenReturn(new JavaMailSenderImpl().createMimeMessage());
        // El servidor SMTP falla en el envio: el servicio no debe lanzar ni romper al que lo llama.
        org.mockito.Mockito.doThrow(new org.springframework.mail.MailSendException("smtp caido"))
                .when(sender).send(org.mockito.ArgumentMatchers.any(MimeMessage.class));
        SmtpEmailService servicio = servicio(sender, true, HOST, FROM);

        boolean enviado = servicio.enviarHtml(DESTINATARIO, ASUNTO, CUERPO);

        assertThat(enviado).isFalse();
        verify(sender).send(org.mockito.ArgumentMatchers.any(MimeMessage.class));
        verify(sender, never()).send(org.mockito.ArgumentMatchers.any(org.springframework.mail.SimpleMailMessage.class));
    }
}
