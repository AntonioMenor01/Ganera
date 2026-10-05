package com.ganera.core.tramite;

/**
 * Estado de la extraccion por IA de un Tramite que llego por WhatsApp (B1, D3). Null en los
 * Tramites anteriores a B1. PENDIENTE: el planificador (T2) la hara; COMPLETADA: aplicada;
 * FALLIDA: la IA fallo y el Tramite ya esta en revision con aviso (puede reintentarse);
 * SIN_TEXTO: el mensaje no traia texto (solo foto o audio), no se llama a la IA.
 */
public enum EstadoExtraccion {
    PENDIENTE,
    COMPLETADA,
    FALLIDA,
    SIN_TEXTO
}
