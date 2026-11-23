package com.ganera.core.ovz;

import com.ganera.core.ganadero.CredencialOvzEnClaro;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Navegador de OVZ escrito a mano para los tests del worker (sin Playwright): registra cada llamada por nombre y puede
 * lanzar en el paso elegido. Nunca guarda valores (ni crotal ni credenciales) en {@link #llamadas}.
 */
class NavegadorOvzFalso implements NavegadorOvz {

    final List<String> llamadas = new ArrayList<>();
    final Map<String, RuntimeException> fallos = new HashMap<>();

    String codigoRiia;
    String explotacionUbicacion;
    List<DocumentoCandidato> documentos = List.of();
    Map<String, byte[]> pdfs = Map.of();
    /** Lo último que recibió iniciarSesion (para comprobar que llega la contraseña descifrada). */
    String ultimaClave;
    /** Alta: crotal que muestra la pantalla 2 ({@code P_lblcrotal}) y los datos del formulario que recibió. */
    String crotalSeleccionado;
    DatosFormularioAlta datosAlta;
    List<DocumentoCandidato> documentosAlta = List.of();

    private void paso(String nombre) {
        llamadas.add(nombre);
        RuntimeException fallo = fallos.get(nombre);
        if (fallo != null) {
            throw fallo;
        }
    }

    long veces(String nombre) {
        return llamadas.stream().filter(nombre::equals).count();
    }

    @Override
    public void iniciarSesion(String url, String usuario, CredencialOvzEnClaro password) {
        ultimaClave = password.valor();
        paso("iniciarSesion");
    }

    @Override
    public void prepararBaja(String crotal) {
        paso("prepararBaja");
    }

    @Override
    public String leerCodigoRiia() {
        paso("leerCodigoRiia");
        return codigoRiia;
    }

    @Override
    public String leerExplotacionUbicacion() {
        paso("leerExplotacionUbicacion");
        return explotacionUbicacion;
    }

    @Override
    public void rellenarFechaMuerte(String ddMMyyyy) {
        paso("rellenarFechaMuerte");
    }

    @Override
    public void rellenarMer(String mer) {
        paso("rellenarMer");
    }

    @Override
    public void pulsarAceptar() {
        paso("pulsarAceptar");
    }

    @Override
    public List<DocumentoCandidato> listarDocumentosBajaAnimal() {
        paso("listarDocumentosBajaAnimal");
        return documentos;
    }

    @Override
    public byte[] descargarDocumento(DocumentoCandidato candidato) {
        paso("descargarDocumento");
        return pdfs.get(candidato.referencia());
    }

    @Override
    public void salir() {
        llamadas.add("salir");
    }

    // ---- alta por nacimiento ----

    @Override
    public void prepararAlta(String crotalTernero) {
        paso("prepararAlta");
    }

    @Override
    public String leerCrotalSeleccionado() {
        paso("leerCrotalSeleccionado");
        return crotalSeleccionado;
    }

    @Override
    public void rellenarFormularioAlta(DatosFormularioAlta datos) {
        datosAlta = datos;
        paso("rellenarFormularioAlta");
    }

    @Override
    public void pulsarAceptarAlta() {
        paso("pulsarAceptarAlta");
    }

    @Override
    public List<DocumentoCandidato> listarDocumentosAltaBovino() {
        paso("listarDocumentosAltaBovino");
        return documentosAlta;
    }
}
