package com.ganera.core.ganadero;

/**
 * Se intento descifrar la credencial OVZ de un Ganadero de OTRA gestoria distinta a la del actor autenticado
 * (M2, aislamiento). No se descifra nada ni se deja fila de auditoria. El mensaje no lleva ningun valor sensible.
 */
public class CredencialOvzDeOtraGestoriaException extends RuntimeException {

    public CredencialOvzDeOtraGestoriaException() {
        super("La credencial OVZ es de otra gestoria");
    }
}
