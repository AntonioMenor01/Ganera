package com.ganera.core.ganadero;

/**
 * Cuerpo de PUT /ganaderos/{id}/credenciales-ovz (escalon 2, T1). La contrasena es SOLO de escritura: entra por
 * aqui, se cifra y nunca vuelve en ninguna respuesta. Para cambiar el usuario hay que reenviar la contrasena.
 *
 * <p>Si {@code usaCertificado} es {@code true}, la cuenta entra con certificado digital (no soportada en
 * automatico, D8): el usuario y la contrasena se ignoran y se borran. Si es {@code false}/{@code null}, el
 * controlador exige usuario y contrasena no vacios.
 *
 * <p>{@code toString()} redacta usuario y contrasena: aunque algo registrara el request por error, no se filtra
 * ninguna credencial a los logs.
 */
public record CredencialesOvzRequest(String usuario, String password, Boolean usaCertificado) {

    @Override
    public String toString() {
        return "CredencialesOvzRequest[usuario=***, password=***, usaCertificado=" + usaCertificado + "]";
    }
}
