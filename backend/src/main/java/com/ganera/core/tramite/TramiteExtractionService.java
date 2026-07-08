package com.ganera.core.tramite;

/**
 * Abstrae el proveedor de IA usado para extraer {tipoTramite, ultimosDigitosCrotales}
 * de un mensaje de WhatsApp en lenguaje natural. Sin lógica de prompt todavía
 * (Prompt 3b) — solo la interfaz y un esqueleto que compila.
 */
public interface TramiteExtractionService {
    TramiteExtraido extraer(String mensajeOriginal);

    record TramiteExtraido(TipoTramite tipoTramite, java.util.List<String> ultimosDigitosCrotales) {
    }
}
