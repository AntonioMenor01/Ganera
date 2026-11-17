package com.ganera.core.ovz.spike;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Frame;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * SPIKE (prueba técnica, NO producción) del escalón 2 de OVZ: ¿puede Ganera, desde el servidor, abrir OVZnet,
 * iniciar sesión con las credenciales de un ganadero, elegir la explotación bovina, navegar SOLO por menús
 * hasta la baja por muerte, buscar un crotal, leer el Código RIIA y la explotación de ubicación y comparar con
 * el .env, y salir — SIN presentar nada?
 *
 * <p><b>No presenta nada.</b> Bloqueo duro: {@link #pulsar} y {@link #pulsarPorTexto} pasan por
 * {@link #prohibirAceptar}, que lanza si el selector/etiqueta apunta a «Aceptar» ({@code P_btn_Aceptar}) o
 * «Solicitar». Ese botón solo se LEE (para confirmar la pantalla), nunca se pulsa.
 *
 * <p><b>Mecanismo único de localización:</b> igual que el diagnóstico, todos los pasos buscan el elemento en
 * TODOS los frames por JS (sin exigir que sea visible si está oculto por estilos) y actúan por JS
 * (foco+value+change para escribir, {@code element.click()} para pulsar, {@code select.value}+change para
 * elegir). Así no dependemos de la visibilidad ni del frame principal.
 *
 * <p><b>Desactivado por defecto</b> (patrón de {@code ExtraccionSinTramiteEvalTest}): solo corre con
 * {@code OVZ_SPIKE=true} y las variables abajo, que Antonio exporta desde un <b>.env fuera del repo</b> (nunca
 * en el chat, logs ni commits). No se toma ninguna captura. Los logs solo dicen «PASO N OK/FALLO» y estructura;
 * nunca el crotal, el usuario, la contraseña ni el contenido de la página.
 *
 * <p>Variables: {@code OVZ_URL}, {@code OVZ_USUARIO}, {@code OVZ_PASSWORD}, {@code OVZ_CROTAL} (obligatorias) y
 * {@code OVZ_REGA} (opcional, para comparar la explotación de ubicación).
 *
 * <p>Ejecutar: {@code OVZ_SPIKE=true ./mvnw test -Dtest=OvzSpikeBajaMuerteTest} (con las variables exportadas).
 */
@EnabledIfEnvironmentVariable(named = "OVZ_SPIKE", matches = "true")
class OvzSpikeBajaMuerteTest {

    /** El botón que NUNCA se pulsa. */
    private static final String SUFIJO_ACEPTAR = "P_btn_Aceptar";
    private static final String ID_SALIR = "ctl00_ucBarraUsuario1_btnSalir";
    private static final double TIMEOUT_MS = 20_000;

    /** Último selector buscado, para que el diagnóstico diga en qué se estaba al fallar. */
    private String ultimoSelector = "(ninguno)";

    @Test
    void preparaLaBajaYSaleSinPresentar() {
        String url = requerida("OVZ_URL");
        String usuario = requerida("OVZ_USUARIO");
        String password = requerida("OVZ_PASSWORD");
        String crotal = requerida("OVZ_CROTAL");

        try (Playwright playwright = Playwright.create()) {
            Browser navegador = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = navegador.newContext().newPage();
            page.setDefaultTimeout(TIMEOUT_MS);
            boolean cerrada = false;
            try {
                // PASO 1 — abrir la entrada de OVZnet (único goto permitido: la entrada, no una interna).
                page.navigate(url);
                ok(1, "OVZnet abierto");

                // PASO 2 — login con los ids reales de Inicio.aspx (todo por el mecanismo cross-frame).
                escribir(page, "[id$='_txtUsuario']", usuario, "usuario");
                escribir(page, "[id$='_txtClave']", password, "clave");
                pulsar(page, "[id$='_btnLinkEntrar']", "enlace Entrar");
                try {
                    page.waitForFunction(
                            "() => location.pathname.toLowerCase().endsWith('/presentacion.aspx')"
                                    + " || document.getElementById('ctl00_ucMenu1_ddlDelegado') !== null",
                            null, new Page.WaitForFunctionOptions().setTimeout(TIMEOUT_MS));
                } catch (RuntimeException e) {
                    if (page.url().toLowerCase().contains("inicio.aspx")) {
                        imprimirErroresOvz(page);
                    }
                    throw e;
                }
                ok(2, "sesion iniciada");

                // PASO 3 — elegir la explotación bovina en el desplegable, por value (sin exigir visibilidad).
                Frame fSel = frameCon(page, "#ctl00_ucMenu1_ddlDelegado", "desplegable de explotaciones");
                Object opcionesDelegado = fSel.evaluate(JS_OPCIONES_DELEGADO);
                if (!(opcionesDelegado instanceof List<?> opciones)) {
                    throw new IllegalStateException("el desplegable no devolvió opciones");
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
                seleccionarValor(fSel, "#ctl00_ucMenu1_ddlDelegado", valoresBovinos.get(0), "explotacion bovina");
                frameCon(page, "a[href*='wgTramites.aspx']", "menu cargado tras elegir explotacion");
                ok(3, "explotacion bovina elegida, menu cargado");

                // PASO 4 — menú Trámites por href (sin escribir URLs).
                pulsar(page, "a[href*='wgTramites.aspx']", "menu Tramites (wgTramites.aspx)");
                ok(4, "menu Tramites");

                // PASO 5 — enlace «Bóvidos» de la tabla de especies (por texto, sin acentos ni mayúsculas).
                pulsarPorTexto(page, "Bóvidos", "enlace Bovidos");
                ok(5, "entrada a Bovidos");

                // PASO 6 — pestaña «Baja de Bóvidos» (por texto).
                pulsarPorTexto(page, "Baja de Bóvidos", "pestana Baja de Bovidos");
                // Confirmar que «Aceptar» existe (pantalla correcta) pero sin pulsarlo JAMÁS: solo se LEE.
                frameCon(page, "[id$='" + SUFIJO_ACEPTAR + "']", "formulario de baja (Aceptar presente)");
                ok(6, "pantalla de baja (Aceptar presente, no se pulsa)");

                // PASO 7 — crotal en _ctl05_..._TxtCrotal y «Buscar».
                escribir(page, "[id*='_ctl05_'][id$='TxtCrotal']", crotal, "campo crotal");
                pulsar(page, "[id*='_ctl05_'][id$='P_LinkBuscar']", "boton Buscar");
                ok(7, "crotal buscado");

                // PASO 8 — leer _txtCodigoRIIA y _txtExplotacionUbicacion y COMPARAR con el .env (nunca valores).
                String codigoRiia = leer(page, "[id$='_txtCodigoRIIA']", "Codigo RIIA");
                String explotacionUbic = leer(page, "[id$='_txtExplotacionUbicacion']", "explotacion de ubicacion");
                if (codigoRiia == null) {
                    throw new IllegalStateException("no se pudo leer el Codigo RIIA");
                }
                if (explotacionUbic == null) {
                    throw new IllegalStateException("no se pudo leer la explotacion de ubicacion");
                }
                boolean crotalCuadra = normalizaId(codigoRiia).equals(normalizaId(crotal));
                String regaEnv = System.getenv("OVZ_REGA");
                String verifExplotacion;
                if (regaEnv == null || regaEnv.isBlank()) {
                    verifExplotacion = "explotacion leida (define OVZ_REGA para comparar)";
                } else {
                    verifExplotacion = normalizaId(explotacionUbic).contains(normalizaId(regaEnv))
                            ? "explotacion COINCIDE con OVZ_REGA" : "explotacion NO coincide con OVZ_REGA";
                }
                ok(8, "verificacion: Codigo RIIA " + (crotalCuadra ? "COINCIDE" : "NO coincide")
                        + " con el crotal; " + verifExplotacion);

                // PASO 9 — salir con «Salir». NUNCA «Aceptar».
                pulsar(page, "#" + ID_SALIR, "boton Salir");
                cerrada = true;
                ok(9, "sesion cerrada con Salir, sin presentar nada");
            } catch (ClicProhibidoException e) {
                System.out.println("BLOQUEO DURO: se intentó pulsar «Aceptar». " + e.getMessage());
                salirBestEffort(page);
                cerrada = true;
                fail("BLOQUEO DURO: se intentó pulsar «Aceptar» — abortado sin presentar. " + e.getMessage());
            } catch (Throwable e) {
                System.out.println("PASO FALLO: " + e.getClass().getSimpleName() + " — " + primeraLinea(e.getMessage()));
                System.out.println("Selector que se buscaba: " + ultimoSelector);
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

    // --- Mecanismo único de localización (cross-frame, por JS, sin exigir visibilidad) --------------------

    /**
     * Busca el selector CSS en TODOS los frames (como el diagnóstico), esperando hasta {@link #TIMEOUT_MS} a que
     * aparezca. Devuelve el Frame que lo contiene; lanza nombrando el selector si no aparece.
     */
    private Frame frameCon(Page page, String css, String etiqueta) {
        ultimoSelector = "css=\"" + css + "\" (" + etiqueta + ")";
        long fin = System.currentTimeMillis() + (long) TIMEOUT_MS;
        do {
            for (Frame f : page.frames()) {
                try {
                    Object n = f.evaluate("(sel) => document.querySelectorAll(sel).length", css);
                    if (n instanceof Number num && num.intValue() > 0) {
                        return f;
                    }
                } catch (Throwable ignored) {
                    // frame aún navegando: se reintenta
                }
            }
            page.waitForTimeout(200);
        } while (System.currentTimeMillis() < fin);
        throw new IllegalStateException("no se encontró el selector " + css + " (" + etiqueta + ")");
    }

    /** Escribe en el primer elemento que casa (foco + value + input/change), sin exigir visibilidad. */
    private void escribir(Page page, String css, String valor, String etiqueta) {
        Frame f = frameCon(page, css, etiqueta);
        f.evaluate("([sel, v]) => { const e = document.querySelector(sel); if (e.focus) e.focus();"
                + " e.value = v; e.dispatchEvent(new Event('input', {bubbles:true}));"
                + " e.dispatchEvent(new Event('change', {bubbles:true})); }", List.of(css, valor));
    }

    /** Pulsa el primer elemento que casa (element.click()), sin exigir visibilidad. Bloqueo duro de «Aceptar». */
    private void pulsar(Page page, String css, String etiqueta) {
        prohibirAceptar(css);
        prohibirAceptar(etiqueta);
        Frame f = frameCon(page, css, etiqueta);
        f.evaluate("(sel) => { const e = document.querySelector(sel); e.click(); }", css);
        esperarQuietud(page);
    }

    /** Elige en un &lt;select&gt; la opción por value y dispara change (postback), sin exigir visibilidad. */
    private void seleccionarValor(Frame f, String css, String value, String etiqueta) {
        ultimoSelector = "css=\"" + css + "\" (" + etiqueta + ")";
        f.evaluate("([sel, v]) => { const s = document.querySelector(sel); s.value = v;"
                + " s.dispatchEvent(new Event('change', {bubbles:true})); }", List.of(css, value));
        esperarQuietud(f.page());
    }

    /** Pulsa el primer clicable cuyo texto/valor normaliza al dado (cross-frame, sin acentos, sin visibilidad). */
    private void pulsarPorTexto(Page page, String texto, String etiqueta) {
        prohibirAceptar(texto);
        prohibirAceptar(etiqueta);
        ultimoSelector = "texto=\"" + texto + "\" (" + etiqueta + ")";
        String objetivo = normaliza(texto);
        long fin = System.currentTimeMillis() + (long) TIMEOUT_MS;
        do {
            for (Frame f : page.frames()) {
                try {
                    Object res = f.evaluate(JS_TEXTOS_CLICABLES);
                    if (res instanceof List<?> textos) {
                        for (int i = 0; i < textos.size(); i++) {
                            if (normaliza(String.valueOf(textos.get(i))).equals(objetivo)) {
                                f.evaluate(JS_CLICK_INDICE, i);
                                esperarQuietud(page);
                                return;
                            }
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
            page.waitForTimeout(200);
        } while (System.currentTimeMillis() < fin);
        throw new IllegalStateException("no se encontró por texto: " + texto + " (" + etiqueta + ")");
    }

    /** Lee el value/textContent del primer elemento que casa; devuelve null si no hay. Nunca loguea el valor. */
    private String leer(Page page, String css, String etiqueta) {
        Frame f = frameCon(page, css, etiqueta);
        Object r = f.evaluate("(sel) => { const e = document.querySelector(sel); if (!e) return null;"
                + " const v = (e.value !== undefined && e.value !== null && e.value !== '')"
                + " ? e.value : (e.textContent || ''); const t = v.trim(); return t ? t : null; }", css);
        return r == null ? null : r.toString();
    }

    private void esperarQuietud(Page page) {
        try {
            page.waitForLoadState();
        } catch (Throwable ignored) {
        }
    }

    /** Todos los clicables de un frame, en orden estable; para &lt;input&gt; se usa el value/alt. */
    private static final String JS_TEXTOS_CLICABLES = """
            () => {
              const els = Array.from(document.querySelectorAll(
                "a, button, input[type=submit], input[type=button], input[type=image], [onclick]"));
              return els.map(e => e.tagName === 'INPUT'
                ? (e.getAttribute('value') || e.getAttribute('alt') || '').trim()
                : (e.textContent || '').trim());
            }
            """;

    /** Pulsa el i-ésimo clicable (mismo orden que JS_TEXTOS_CLICABLES). */
    private static final String JS_CLICK_INDICE = """
            (i) => {
              const els = Array.from(document.querySelectorAll(
                "a, button, input[type=submit], input[type=button], input[type=image], [onclick]"));
              if (els[i]) els[i].click();
            }
            """;

    // --- Bloqueo duro de «Aceptar» -----------------------------------------------------------------------

    private void prohibirAceptar(String texto) {
        if (texto != null) {
            String t = texto.toLowerCase();
            if (t.contains(SUFIJO_ACEPTAR.toLowerCase()) || t.contains("aceptar") || t.contains("solicitar")) {
                throw new ClicProhibidoException("selector/etiqueta='" + texto + "'");
            }
        }
    }

    private static final class ClicProhibidoException extends RuntimeException {
        ClicProhibidoException(String msg) {
            super(msg);
        }
    }

    /** Intenta cerrar con «Salir» tras un fallo. Nunca lanza y solo pulsa el botón «Salir» por su id. */
    private void salirBestEffort(Page page) {
        try {
            for (Frame f : page.frames()) {
                Object n = f.evaluate("(sel) => document.querySelectorAll(sel).length", "#" + ID_SALIR);
                if (n instanceof Number num && num.intValue() > 0) {
                    f.evaluate("(sel) => { const e = document.querySelector(sel); e.click(); }", "#" + ID_SALIR);
                    System.out.println("PASO Salir (tras fallo) OK: sesion cerrada");
                    return;
                }
            }
            System.out.println("PASO Salir (tras fallo): no se encontro el boton Salir; la sesion se dejara caducar");
        } catch (Throwable e) {
            System.out.println("PASO Salir (tras fallo): no se pudo pulsar Salir; la sesion se dejara caducar");
        }
    }

    // --- Diagnóstico de estructura (solo estructura, NUNCA valores ni textos de usuario) ------------------

    /**
     * JS que describe la ESTRUCTURA del frame: por cada input/button y enlace con postback su etiqueta, type,
     * id, name y (botones/enlaces) el texto visible; por cada select, su id/name, nº de opciones y SOLO la
     * especie de cada opción (lo que va tras « - »). NUNCA el {@code value} ni el REGA.
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

    /** Vuelca la estructura de la página y de cada frame (los campos podrían estar en un iframe). */
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

    /** JS que recoge el texto visible de validadores/etiquetas de error (textos de la app, no datos). */
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

    /** Imprime los mensajes de error de OVZ en Inicio.aspx tras un login fallido (solo textos de la app). */
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

    // --- Utilidades --------------------------------------------------------------------------------------

    private String requerida(String nombre) {
        String v = System.getenv(nombre);
        if (v == null || v.isBlank()) {
            fail("Falta la variable de entorno " + nombre + " (expórtala desde tu .env fuera del repo).");
        }
        return v;
    }

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

    private String sinParametros(String url) {
        if (url == null) {
            return "(sin url)";
        }
        int q = url.indexOf('?');
        return q >= 0 ? url.substring(0, q) : url;
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
