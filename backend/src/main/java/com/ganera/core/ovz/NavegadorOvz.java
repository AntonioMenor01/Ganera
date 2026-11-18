package com.ganera.core.ovz;

import com.ganera.core.ganadero.CredencialOvzEnClaro;

import java.util.List;

/**
 * Puerto del navegador de OVZnet que usa el worker (T4). La implementación real es {@link PlaywrightNavegadorOvz};
 * los tests usan un falso. Navega SOLO por menús (único {@code goto}: la entrada) y nunca registra valores.
 */
public interface NavegadorOvz {

    /** Abre la entrada y hace login. Lanza {@link LoginCertificadoException}, {@link CredencialesOvzInvalidasException},
     *  {@link SesionCaducadaException} o {@link NavegacionOvzException}. */
    void iniciarSesion(String url, String usuario, CredencialOvzEnClaro password);

    /** Explotación bovina, menú Trámites, Bóvidos, pestaña «Baja de Bóvidos», crotal, Buscar y pantalla 2 cargada. */
    void prepararBaja(String crotal);

    String leerCodigoRiia();

    String leerExplotacionUbicacion();

    void rellenarFechaMuerte(String ddMMyyyy);

    /** No-op si {@code mer} es null o blanco. */
    void rellenarMer(String mer);

    /**
     * SOLO modo REAL. Resuelve el botón, comprueba su id (sufijo {@code _btn_Aceptar}) Y su texto/valor «Aceptar»
     * ANTES de pulsar; lanza {@link AceptarException} (sin pulsar) si no cuadra.
     */
    void pulsarAceptar();

    /** Documentos, tipo «Baja Animal», Buscar: todas las filas (con paginación). */
    List<DocumentoCandidato> listarDocumentosBajaAnimal();

    byte[] descargarDocumento(DocumentoCandidato candidato);

    /** Click en Salir y cierre del navegador. Best-effort: nunca lanza. */
    void salir();
}
