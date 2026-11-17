package com.ganera.core.ovz.spike;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * SPIKE (prueba técnica, NO producción) del escalón 2 de OVZ: ¿puede Ganera, desde el servidor, abrir OVZnet,
 * iniciar sesión con las credenciales de un ganadero, navegar SOLO por menús hasta la baja por muerte, buscar
 * un crotal y leer el Código RIIA y la explotación, y salir — SIN presentar nada?
 *
 * <p><b>No presenta nada.</b> Hay un <b>bloqueo duro</b>: todo clic pasa por {@link #clicSeguro}, que lanza si
 * el selector apunta al botón «Aceptar» de OVZ ({@code P_btn_Aceptar}). Este script nunca puede pulsarlo.
 *
 * <p><b>Desactivado por defecto</b> (patrón de {@code ExtraccionSinTramiteEvalTest}): solo corre con
 * {@code OVZ_SPIKE=true} y las variables de entorno abajo, que Antonio exporta desde un <b>.env fuera del
 * repo</b> (nunca en el chat, nunca en logs, nunca commiteado). No se toma ninguna captura. Los logs solo dicen
 * «PASO N OK» o «PASO N FALLO: …»; nunca el crotal, el usuario ni la contraseña, y nunca el contenido de la
 * página (solo etiquetas de paso y, como mucho, el selector o el tipo de error, que no contienen datos reales).
 *
 * <p>Variables de entorno (todas obligatorias para este test):
 * <ul>
 *   <li>{@code OVZ_URL} — URL base de OVZnet (p. ej. la de la página de entrada). El mapa no la guarda.</li>
 *   <li>{@code OVZ_USUARIO}, {@code OVZ_PASSWORD} — credenciales del ganadero (usuario/contraseña, NO certificado).</li>
 *   <li>{@code OVZ_CROTAL} — crotal completo a buscar en la baja.</li>
 * </ul>
 *
 * <p>Ejecutar (Git Bash, con las variables ya exportadas desde el .env):
 * {@code JAVA_HOME=... OVZ_SPIKE=true ./mvnw test -Dtest=OvzSpikeBajaMuerteTest}
 *
 * <p>Es exploratorio: si un paso falla por un selector, el log dice en cuál, para afinarlo. Los ids de login y
 * de los campos de solo lectura NO están en el mapa; aquí se localizan por heurística (tipo de input, texto,
 * sufijo de id) y pueden necesitar ajuste tras la primera ejecución real.
 */
@EnabledIfEnvironmentVariable(named = "OVZ_SPIKE", matches = "true")
class OvzSpikeBajaMuerteTest {

    /** El botón que NUNCA se pulsa. El bloqueo duro vive en {@link #clicSeguro}. */
    private static final String SUFIJO_ACEPTAR = "P_btn_Aceptar";
    private static final String ID_SALIR = "ctl00_ucBarraUsuario1_btnSalir";
    private static final double TIMEOUT_MS = 20_000;

    @Test
    void preparaLaBajaYSaleSinPresentar() {
        String url = requerida("OVZ_URL");
        String usuario = requerida("OVZ_USUARIO");
        String password = requerida("OVZ_PASSWORD");
        String crotal = requerida("OVZ_CROTAL");

        try (Playwright playwright = Playwright.create()) {
            Browser navegador = playwright.chromium()
                    .launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = navegador.newContext().newPage();
            page.setDefaultTimeout(TIMEOUT_MS);
            boolean cerrada = false;
            try {

            // PASO 1 — abrir la página de entrada de OVZnet (único goto permitido: la entrada, no una interna).
            page.navigate(url);
            ok(1, "OVZnet abierto");

            // PASO 2 — login con usuario y contraseña (NO certificado). Ids no documentados: heurística.
            page.locator("input[type=password]").first().fill(password);
            page.locator("input[type=text]:visible").first().fill(usuario);
            // Botón de entrar por texto/valor exacto, para NO pulsar «Entrar con Certificado».
            clicSeguro(page, page.getByRole(AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName("Entrar").setExact(true)), "boton Entrar");
            page.waitForURL("**/Presentacion.aspx", new Page.WaitForURLOptions().setTimeout(TIMEOUT_MS));
            ok(2, "sesion iniciada (Presentacion.aspx)");

            // PASO 3 — navegar SOLO por menús: Trámites › Trámites. Nada de URLs ni Atrás.
            page.getByText("Trámites", new Page.GetByTextOptions().setExact(true)).first().hover();
            clicSeguro(page, page.getByRole(AriaRole.LINK,
                    new Page.GetByRoleOptions().setName("Trámites").setExact(true)).first(), "menu Tramites");
            page.waitForLoadState();
            ok(3, "menu Tramites");

            // PASO 4 — enlace «Bóvidos» de la tabla de especies.
            clicSeguro(page, page.getByRole(AriaRole.LINK,
                    new Page.GetByRoleOptions().setName("Bóvidos").setExact(true)).first(), "enlace Bovidos");
            page.waitForLoadState();
            ok(4, "entrada a Bovidos");

            // PASO 5 — pestaña «Baja de Bóvidos».
            clicSeguro(page, page.getByText("Baja de Bóvidos", new Page.GetByTextOptions().setExact(true)).first(),
                    "pestana Baja de Bovidos");
            page.waitForLoadState();
            // Confirmar que el «Aceptar» existe (pantalla correcta) pero sin pulsarlo JAMAS.
            assertNotNull(page.locator("[id$='" + SUFIJO_ACEPTAR + "']"), "no se localizó el formulario de baja");
            ok(5, "pantalla de baja (Aceptar presente, no se pulsa)");

            // PASO 6 — escribir el crotal (no se loguea) y pulsar «Buscar» (postback que no guarda).
            page.locator("[id$='TxtCrotal']").first().fill(crotal);
            clicSeguro(page, page.locator("[id$='P_LinkBuscar']").first(), "boton Buscar");
            page.waitForLoadState();
            ok(6, "crotal buscado");

            // PASO 7 — leer el Código RIIA y la explotación (sin mostrarlos). Solo confirmamos que se pueden leer.
            String codigoRiia = textoDe(page, "[id$='Riia'], [id$='RIIA'], [id$='Crotal']");
            String explotacion = textoDe(page, "[id$='Ubicacion'], [id$='Explotacion']");
            assertNotNull(codigoRiia, "no se pudo leer el Código RIIA");
            assertNotNull(explotacion, "no se pudo leer la explotación");
            ok(7, "Codigo RIIA y explotacion leidos (no se muestran)");

            // PASO 8 — salir con «Salir» (cierra sesión). NUNCA «Aceptar».
            clicSeguro(page, page.locator("#" + ID_SALIR), "boton Salir");
            page.waitForLoadState();
            cerrada = true;
            ok(8, "sesion cerrada con Salir, sin presentar nada");
            } catch (ClicProhibidoException e) {
                // El bloqueo duro saltó: alguien intentó pulsar «Aceptar». Nunca un envío. Cerrar con «Salir».
                salirBestEffort(page);
                cerrada = true;
                fail("BLOQUEO DURO: se intentó pulsar «Aceptar» — abortado sin presentar. " + e.getMessage());
            } catch (Throwable e) {
                // Cualquier fallo a mitad (incluida una aserción): intentar cerrar con «Salir» igualmente.
                salirBestEffort(page);
                cerrada = true;
                fail("PASO FALLO: " + e.getClass().getSimpleName() + " — " + primeraLinea(e.getMessage()));
            } finally {
                if (!cerrada) {
                    salirBestEffort(page);
                }
                navegador.close();
            }
        }
    }

    // --- Bloqueo duro de «Aceptar» -------------------------------------------------------------------------

    /** Lanza si el selector/locator apunta al «Aceptar» de OVZ; si no, pulsa. Cada clic pasa por aquí. */
    private void clicSeguro(Page page, com.microsoft.playwright.Locator locator, String etiqueta) {
        prohibirAceptar(locator.toString());
        prohibirAceptar(etiqueta);
        locator.click();
    }

    private void prohibirAceptar(String texto) {
        if (texto != null) {
            String t = texto.toLowerCase();
            if (t.contains(SUFIJO_ACEPTAR.toLowerCase()) || t.contains("aceptar") || t.contains("solicitar")) {
                throw new ClicProhibidoException("selector='" + texto + "'");
            }
        }
    }

    private static final class ClicProhibidoException extends RuntimeException {
        ClicProhibidoException(String msg) {
            super(msg);
        }
    }

    /**
     * Intenta cerrar la sesión con «Salir» tras un fallo. Nunca lanza (ya estamos fallando) y solo pulsa el
     * botón «Salir» por su id — jamás «Aceptar». Si no encuentra «Salir», la sesión se deja caducar.
     */
    private void salirBestEffort(Page page) {
        try {
            com.microsoft.playwright.Locator salir = page.locator("#" + ID_SALIR);
            if (salir.count() > 0) {
                salir.click();
                System.out.println("PASO Salir (tras fallo) OK: sesion cerrada");
            } else {
                System.out.println("PASO Salir (tras fallo): no se encontro el boton Salir; la sesion se dejara caducar");
            }
        } catch (Throwable ignored) {
            System.out.println("PASO Salir (tras fallo): no se pudo pulsar Salir; la sesion se dejara caducar");
        }
    }

    // --- Utilidades ----------------------------------------------------------------------------------------

    private String requerida(String nombre) {
        String v = System.getenv(nombre);
        if (v == null || v.isBlank()) {
            fail("Falta la variable de entorno " + nombre + " (expórtala desde tu .env fuera del repo).");
        }
        return v;
    }

    /** Lee el valor/texto de la primera coincidencia; devuelve null si no hay. Nunca loguea el valor. */
    private String textoDe(Page page, String selector) {
        com.microsoft.playwright.Locator loc = page.locator(selector).first();
        if (loc.count() == 0) {
            return null;
        }
        String valor = loc.inputValue();
        if (valor == null || valor.isBlank()) {
            valor = loc.textContent();
        }
        return (valor == null || valor.isBlank()) ? null : valor;
    }

    private void ok(int paso, String que) {
        System.out.println("PASO " + paso + " OK: " + que);
    }

    private String primeraLinea(String msg) {
        if (msg == null) {
            return "(sin mensaje)";
        }
        String linea = msg.split("\\R", 2)[0];
        return linea.length() > 200 ? linea.substring(0, 200) + "…" : linea;
    }
}
