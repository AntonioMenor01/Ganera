package com.ganera.core.alta;

import com.ganera.core.shared.email.EmailService;

import java.util.ArrayList;
import java.util.List;

/**
 * Doble de {@link EmailService} para los tests del alta automatica (no toca SMTP). Captura los emails que se
 * "envian" para poder afirmar sobre su contenido, y permite simular que el email esta apagado
 * ({@code configurado=false}) o que el envio falla ({@code fallar=true}).
 */
class EmailServiceFalso implements EmailService {

    record Enviado(String destinatario, String asunto, String cuerpo, boolean html) {
    }

    private final boolean configurado;
    private boolean fallar;
    final List<Enviado> enviados = new ArrayList<>();

    EmailServiceFalso(boolean configurado) {
        this.configurado = configurado;
    }

    void fallar(boolean fallar) {
        this.fallar = fallar;
    }

    @Override
    public boolean configurado() {
        return configurado;
    }

    @Override
    public boolean enviarHtml(String destinatario, String asunto, String cuerpoHtml) {
        if (fallar) {
            return false;
        }
        enviados.add(new Enviado(destinatario, asunto, cuerpoHtml, true));
        return true;
    }

    @Override
    public boolean enviarTexto(String destinatario, String asunto, String cuerpoTexto) {
        if (fallar) {
            return false;
        }
        enviados.add(new Enviado(destinatario, asunto, cuerpoTexto, false));
        return true;
    }

    /** Emails enviados a un destinatario concreto. */
    List<Enviado> aDestinatario(String destinatario) {
        return enviados.stream().filter(e -> e.destinatario().equals(destinatario)).toList();
    }
}
