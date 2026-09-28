package com.ganera.core.tramite;

/**
 * 409 especifico de aprobar: al re-resolver los crotales contra el inventario actual, alguno ha
 * cambiado (otro animal, ambiguo, ya no esta...) respecto a lo que el revisor vio. No se aprueba
 * nada que el revisor no haya visto. A diferencia del resto de 409, esta excepcion NO revierte la
 * transaccion de aprobar (noRollbackFor): la nueva resolucion se guarda para que el detalle la
 * muestre; el estado no cambia porque solo se fija tras pasar todas las comprobaciones.
 */
public class ResolucionCrotalesCambiadaException extends TramiteConflictoException {

    public ResolucionCrotalesCambiadaException(String motivo) {
        super(motivo);
    }
}
