package com.ganera.core.ovz.spike;

import com.ganera.core.ganadero.CredencialOvzEnClaro;
import com.ganera.core.ovz.NavegadorOvz;
import com.ganera.core.ovz.PlaywrightNavegadorOvz;
import com.ganera.core.ovz.ResultadoVerificacion;
import com.ganera.core.ovz.VerificacionBaja;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Prueba MANUAL del navegador de PRODUCCIÓN ({@link PlaywrightNavegadorOvz}) en modo ENSAYO (T4): recorre login, baja,
 * verificación, fecha y MER y sale con «Salir», <b>sin pulsar «Aceptar»</b> (este test no llama a
 * {@code pulsarAceptar()} en ningún caso).
 *
 * <p>Desactivado por defecto (patrón de los spikes): solo corre con {@code OVZ_WORKER_ENSAYO=true} y las variables
 * {@code OVZ_URL}, {@code OVZ_USUARIO}, {@code OVZ_PASSWORD}, {@code OVZ_CROTAL} (obligatorias) y {@code OVZ_REGA}
 * (opcional, para verificar la explotación), exportadas desde un .env fuera del repo. Los logs solo dicen pasos;
 * nunca valores.
 *
 * <p>Ejecutar: {@code OVZ_WORKER_ENSAYO=true ./mvnw clean test -Dtest=OvzWorkerEnsayoManualTest}.
 */
@EnabledIfEnvironmentVariable(named = "OVZ_WORKER_ENSAYO", matches = "true")
class OvzWorkerEnsayoManualTest {

    @Test
    void recorreLaBajaEnEnsayoSinPulsarAceptar() {
        String url = requerida("OVZ_URL");
        String usuario = requerida("OVZ_USUARIO");
        String password = requerida("OVZ_PASSWORD");
        String crotal = requerida("OVZ_CROTAL");
        String rega = System.getenv("OVZ_REGA");

        NavegadorOvz navegador = new PlaywrightNavegadorOvz();
        try {
            navegador.iniciarSesion(url, usuario, new CredencialOvzEnClaro(password));
            navegador.prepararBaja(crotal);
            String riia = navegador.leerCodigoRiia();
            String ubicacion = navegador.leerExplotacionUbicacion();
            if (rega == null || rega.isBlank()) {
                System.out.println("ENSAYO: define OVZ_REGA para verificar también la explotación; se verifica solo el crotal");
                rega = ubicacion; // verifica solo el crotal
            }
            ResultadoVerificacion v = VerificacionBaja.verificar(riia, crotal, ubicacion, rega);
            System.out.println("ENSAYO: verificación " + (v.ok() ? "OK" : "NO coincide (" + v.motivo() + ")"));
            navegador.rellenarFechaMuerte("01/01/2026");
            System.out.println("ENSAYO: fecha rellenada; se sale SIN pulsar «Aceptar»");
        } catch (RuntimeException e) {
            fail("ENSAYO falló: " + e.getClass().getSimpleName() + " — " + e.getMessage());
        } finally {
            navegador.salir();
        }
    }

    private static String requerida(String nombre) {
        String v = System.getenv(nombre);
        if (v == null || v.isBlank()) {
            fail("Falta la variable de entorno " + nombre + " (expórtala desde tu .env fuera del repo).");
        }
        return v;
    }
}
