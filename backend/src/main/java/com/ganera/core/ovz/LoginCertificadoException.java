package com.ganera.core.ovz;

/** Fallo del navegador OVZ (T4). El mensaje es el motivo corto para el gestor: nunca lleva datos del trámite. */
public class LoginCertificadoException extends RuntimeException {

    public LoginCertificadoException() {
        super("Esta cuenta de OVZ entra con certificado digital; la baja se hace a mano.");
    }

    public LoginCertificadoException(String motivo) {
        super(motivo);
    }
}
