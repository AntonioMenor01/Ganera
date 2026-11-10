package com.ganera.core.ganadero;

/**
 * Contrasena OVZ EN CLARO, devuelta por {@code DescifradoCredencialOvz} solo en la via de envio a OVZ (M2).
 * No es un {@code String} desnudo: su {@link #toString()} esta redactado, de modo que la contrasena no se cuele
 * en un log, una traza o un mensaje de error. El valor solo se obtiene con {@link #valor()}.
 */
public record CredencialOvzEnClaro(String valor) {

    @Override
    public String toString() {
        return "CredencialOvzEnClaro[REDACTADO]";
    }
}
