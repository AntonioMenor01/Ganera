package com.ganera.core.tramite;

/**
 * La IA no ha devuelto una extraccion utilizable: negativa del modelo, respuesta cortada, error HTTP
 * o de red, JSON que no valida contra el esquema, o clave sin configurar. Su mensaje nunca lleva el
 * texto del mensaje de WhatsApp ni lo que el modelo contesto (datos de un tercero, van a los logs).
 */
public class ExtraccionFallidaException extends RuntimeException {

    public ExtraccionFallidaException(String motivo) {
        super(motivo);
    }

    public ExtraccionFallidaException(String motivo, Throwable causa) {
        super(motivo, causa);
    }
}
