package com.ganera.core.tramite;

/** Como se resolvio un crotal de un Tramite contra el inventario (decision 20). */
public enum ResolucionCrotal {
    /** Un unico Animal de la Explotacion del Tramite: crotal completo y animal enlazado. */
    EN_INVENTARIO,
    /** Crotal incompleto con varios Animales posibles: se guarda tal cual, sin enlace. */
    AMBIGUO,
    /** Ningun Animal de la Explotacion del Tramite: tal cual, sin enlace. */
    NO_ENCONTRADO,
    /** El Tramite aun no tiene Explotacion: tal cual, sin enlace. */
    SIN_EXPLOTACION
}
