package com.ganera.core.ovz;

/**
 * Todo lo que el worker necesita para presentar una baja, cargado en una transacción corta (T4). Nunca se registra
 * ni se audita (lleva crotal, REGA y usuario OVZ): {@code toString()} redactado.
 */
public record DatosPresentacionBaja(Long tramiteId, Long ganaderoId, String crotal, String rega,
                                    String fechaMuerteDdMmAaaa, String numeroMer, String usuarioOvz,
                                    boolean usaCertificado) {

    @Override
    public String toString() {
        return "DatosPresentacionBaja[REDACTADO]";
    }
}
