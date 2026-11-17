package com.ganera.core.ovz.spike;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Frame;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.ArrayList;
import java.util.List;

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

            // PASO 2 — login en Inicio.aspx con los ids reales (sufijos _txtUsuario, _txtClave, _btnLinkEntrar).
            page.locator("[id$='_txtUsuario']").first().fill(usuario);
            page.locator("[id$='_txtClave']").first().fill(password);
            // «Entrar» es un ENLACE (<a>) con postback, NO un botón, y NO «Entrar con Certificado».
            clicSeguro(page, page.locator("[id$='_btnLinkEntrar']").first(), "enlace Entrar");
            // Tras el postback: la URL pasa a Presentacion.aspx o aparece el desplegable de delegado del menú.
            try {
                page.waitForFunction(
                        "() => location.pathname.toLowerCase().endsWith('/presentacion.aspx')"
                                + " || document.getElementById('ctl00_ucMenu1_ddlDelegado') !== null",
                        null, new Page.WaitForFunctionOptions().setTimeout(TIMEOUT_MS));
            } catch (RuntimeException e) {
                // Si seguimos en Inicio.aspx, el login no entró: volcar los mensajes de error de OVZ (textos de
                // la aplicación, no datos personales) para saber si es usuario/contraseña u otra cosa.
                if (page.url().toLowerCase().contains("inicio.aspx")) {
                    imprimirErroresOvz(page);
                }
                throw e;
            }
            ok(2, "sesion iniciada (Presentacion.aspx / menu delegado)");

            // PASO 3 — elegir la explotación en el desplegable del menú: la opción cuya ESPECIE termina en
            // «Bóvidos» (sin acentos ni mayúsculas), por su value. El menú principal solo aparece después.
            Object opcionesDelegado = page.evaluate(JS_OPCIONES_DELEGADO);
            if (!(opcionesDelegado instanceof List<?> opciones)) {
                throw new IllegalStateException("no se encontró el desplegable de explotaciones (ddlDelegado)");
            }
            List<String> valoresBovinos = new ArrayList<>();
            for (Object o : opciones) {
                java.util.Map<?, ?> m = (java.util.Map<?, ?>) o;
                if (normaliza(String.valueOf(m.get("especie"))).endsWith("bovidos")) {
                    valoresBovinos.add(String.valueOf(m.get("value")));
                }
            }
            if (valoresBovinos.size() > 1) {
                throw new IllegalStateException("varias explotaciones bovinas");
            }
            if (valoresBovinos.isEmpty()) {
                throw new IllegalStateException("no hay explotacion bovina en el desplegable");
            }
            page.locator("#ctl00_ucMenu1_ddlDelegado").selectOption(valoresBovinos.get(0));
            // Postback: la página se recarga y aparece el menú (enlace a wgTramites.aspx en el DOM).
            page.waitForSelector("a[href*='wgTramites.aspx']", new Page.WaitForSelectorOptions()
                    .setState(WaitForSelectorState.ATTACHED).setTimeout(TIMEOUT_MS));
            ok(3, "explotacion bovina elegida, menu cargado");

            // PASO 4 — menú Trámites › Trámites, localizado por href (sin escribir URLs). Puede requerir hover.
            try {
                page.getByText("Trámites", new Page.GetByTextOptions().setExact(true)).first().hover();
            } catch (RuntimeException ignorado) {
                // Si no hay un «Trámites» por texto para el hover, seguimos: el enlace por href puede bastar.
            }
            clicSeguro(page, page.locator("a[href*='wgTramites.aspx']").first(), "menu Tramites (wgTramites.aspx)");
            page.waitForLoadState();
            ok(4, "menu Tramites");

            // PASO 5 — enlace «Bóvidos» de la tabla de especies.
            clicSeguro(page, page.getByRole(AriaRole.LINK,
                    new Page.GetByRoleOptions().setName("Bóvidos").setExact(true)).first(), "enlace Bovidos");
            page.waitForLoadState();
            ok(5, "entrada a Bovidos");

            // PASO 6 — pestaña «Baja de Bóvidos».
            clicSeguro(page, page.getByText("Baja de Bóvidos", new Page.GetByTextOptions().setExact(true)).first(),
                    "pestana Baja de Bovidos");
            page.waitForLoadState();
            // Confirmar que el «Aceptar» existe (pantalla correcta) pero sin pulsarlo JAMAS.
            assertNotNull(page.locator("[id$='" + SUFIJO_ACEPTAR + "']"), "no se localizó el formulario de baja");
            ok(6, "pantalla de baja (Aceptar presente, no se pulsa)");

            // PASO 7 — escribir el crotal (no se loguea) en _TxtCrotal (dentro de _ctl05_) y pulsar «Buscar».
            page.locator("[id*='_ctl05_'][id$='TxtCrotal']").first().fill(crotal);
            clicSeguro(page, page.locator("[id*='_ctl05_'][id$='P_LinkBuscar']").first(), "boton Buscar");
            page.waitForLoadState();
            ok(7, "crotal buscado");

            // PASO 8 — leer _txtCodigoRIIA y _txtExplotacionUbicacion y COMPARAR con el .env (nunca los valores).
            String codigoRiia = textoDe(page, "[id$='_txtCodigoRIIA']");
            String explotacionUbic = textoDe(page, "[id$='_txtExplotacionUbicacion']");
            assertNotNull(codigoRiia, "no se pudo leer el Código RIIA");
            assertNotNull(explotacionUbic, "no se pudo leer la explotación de ubicación");
            boolean crotalCuadra = normalizaId(codigoRiia).equals(normalizaId(crotal));
            String regaEnv = System.getenv("OVZ_REGA"); // opcional
            String verifExplotacion;
            if (regaEnv == null || regaEnv.isBlank()) {
                verifExplotacion = "explotacion leida (define OVZ_REGA para comparar)";
            } else {
                verifExplotacion = normalizaId(explotacionUbic).contains(normalizaId(regaEnv))
                        ? "explotacion COINCIDE con OVZ_REGA" : "explotacion NO coincide con OVZ_REGA";
            }
            ok(8, "verificacion: Codigo RIIA " + (crotalCuadra ? "COINCIDE" : "NO coincide")
                    + " con el crotal; " + verifExplotacion);

            // PASO 9 — salir con «Salir» (cierra sesión). NUNCA «Aceptar».
            clicSeguro(page, page.locator("#" + ID_SALIR), "boton Salir");
            page.waitForLoadState();
            cerrada = true;
            ok(9, "sesion cerrada con Salir, sin presentar nada");
            } catch (ClicProhibidoException e) {
                // El bloqueo duro saltó: alguien intentó pulsar «Aceptar». Nunca un envío. Cerrar con «Salir».
                salirBestEffort(page);
                cerrada = true;
                fail("BLOQUEO DURO: se intentó pulsar «Aceptar» — abortado sin presentar. " + e.getMessage());
            } catch (Throwable e) {
                // Cualquier fallo a mitad (incluida una aserción): volcar la ESTRUCTURA de la página (sin valores)
                // para afinar los selectores, y luego intentar cerrar con «Salir» igualmente.
                System.out.println("PASO FALLO: " + e.getClass().getSimpleName() + " — " + primeraLinea(e.getMessage()));
                diagnosticoEstructura(page);
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

    // --- Diagnóstico de estructura (solo estructura, NUNCA valores ni textos de usuario) --------------------

    /**
     * JS que devuelve una lista de líneas describiendo la ESTRUCTURA del frame: por cada input/select/button y
     * por cada enlace con postback, su etiqueta, type, id, name y (solo botones/enlaces) el texto visible.
     * NUNCA el {@code value} de un campo ni el contenido de celdas: solo estructura y etiquetas de controles.
     */
    private static final String JS_ESTRUCTURA = """
            () => {
              const esc = s => (s || '').toString().trim().slice(0, 40);
              const out = [];
              const line = (el, conTexto) => {
                const tag = el.tagName.toLowerCase();
                const type = el.getAttribute('type') || '';
                const id = el.id || '';
                const name = el.getAttribute('name') || '';
                let s = tag + ' type=' + type + ' id=' + id + ' name=' + name;
                if (conTexto) s += ' text="' + esc(el.textContent) + '"';
                return s;
              };
              document.querySelectorAll('input').forEach(e => out.push(line(e, false)));
              document.querySelectorAll('select').forEach(e => {
                const id = e.id || '';
                const name = e.getAttribute('name') || '';
                const especies = [];
                for (const o of e.options) {
                  const t = (o.textContent || '').trim();
                  const i = t.lastIndexOf(' - ');
                  especies.push(i >= 0 ? t.slice(i + 3) : t);
                }
                out.push('select id=' + id + ' name=' + name + ' opciones=' + e.options.length
                  + ' especies=[' + especies.join('; ') + ']');
              });
              document.querySelectorAll('button').forEach(e => out.push(line(e, true)));
              document.querySelectorAll('a').forEach(e => {
                const h = e.getAttribute('href') || '';
                const o = e.getAttribute('onclick') || '';
                if (h.indexOf('__doPostBack') >= 0 || o.indexOf('__doPostBack') >= 0 || h.indexOf('javascript:') === 0) {
                  out.push(line(e, true));
                }
              });
              return out;
            }
            """;

    /** Vuelca la estructura de la página y de cada frame (los campos de login podrían estar en un iframe). */
    private void diagnosticoEstructura(Page page) {
        try {
            System.out.println("--- DIAGNOSTICO DE ESTRUCTURA (solo estructura, sin valores) ---");
            System.out.println("URL: " + sinParametros(page.url()));
            try {
                System.out.println("Titulo: " + page.title());
            } catch (Throwable ignored) {
                System.out.println("Titulo: (no disponible)");
            }
            List<Frame> frames = page.frames();
            System.out.println("frames (incluido el principal): " + frames.size());
            for (int i = 0; i < frames.size(); i++) {
                Frame f = frames.get(i);
                System.out.println("[frame " + i + "] name=" + f.name() + " url=" + sinParametros(f.url()));
                try {
                    Object res = f.evaluate(JS_ESTRUCTURA);
                    if (res instanceof List<?> lista) {
                        for (Object linea : lista) {
                            System.out.println("   " + linea);
                        }
                    }
                } catch (Throwable e) {
                    System.out.println("   (no se pudo inspeccionar el frame: " + e.getClass().getSimpleName() + ")");
                }
            }
            System.out.println("--- FIN DIAGNOSTICO ---");
        } catch (Throwable e) {
            System.out.println("(no se pudo generar el diagnostico: " + e.getClass().getSimpleName() + ")");
        }
    }

    /** URL sin la query (los parámetros pueden llevar claves internas). */
    private String sinParametros(String url) {
        if (url == null) {
            return "(sin url)";
        }
        int q = url.indexOf('?');
        return q >= 0 ? url.substring(0, q) : url;
    }

    /** JS que recoge el texto visible de validadores/etiquetas de error (textos de la app, no datos personales). */
    private static final String JS_ERRORES = """
            () => {
              const out = [];
              const vistos = new Set();
              const sel = "span[id*='Validator'], div[id*='ValidationSummary'], [id*='lblError'], "
                + "[id*='lblMensaje'], [id*='Error'], .error, [class*='error']";
              document.querySelectorAll(sel).forEach(e => {
                const t = ((e.innerText || e.textContent) || '').trim();
                if (t && !vistos.has(t)) { vistos.add(t); out.push(t.slice(0, 200)); }
              });
              return out;
            }
            """;

    /** Imprime los mensajes de error que OVZ muestre en Inicio.aspx tras un login fallido (solo textos de la app). */
    private void imprimirErroresOvz(Page page) {
        try {
            Object res = page.evaluate(JS_ERRORES);
            System.out.println("MENSAJES DE OVZ EN INICIO.ASPX (textos de la aplicacion, no datos personales):");
            if (res instanceof List<?> lista && !lista.isEmpty()) {
                for (Object t : lista) {
                    System.out.println("   • " + t);
                }
            } else {
                System.out.println("   (ninguno visible)");
            }
        } catch (Throwable e) {
            System.out.println("   (no se pudieron leer los mensajes: " + e.getClass().getSimpleName() + ")");
        }
    }

    /** JS: opciones del desplegable de explotaciones como [{especie, value}] (especie = lo que va tras « - »). */
    private static final String JS_OPCIONES_DELEGADO = """
            () => {
              const sel = document.getElementById('ctl00_ucMenu1_ddlDelegado');
              if (!sel) return null;
              const out = [];
              for (const o of sel.options) {
                const txt = (o.textContent || '').trim();
                const i = txt.lastIndexOf(' - ');
                out.push({ especie: i >= 0 ? txt.slice(i + 3) : txt, value: o.value });
              }
              return out;
            }
            """;

    /** Minúsculas y sin acentos (para casar «Bóvidos»/«bovidos»). */
    private String normaliza(String s) {
        if (s == null) {
            return "";
        }
        String n = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase().trim();
    }

    /** Sin espacios y en mayúsculas (para comparar crotal/REGA sin importar separadores). */
    private String normalizaId(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("\\s", "").toUpperCase();
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
