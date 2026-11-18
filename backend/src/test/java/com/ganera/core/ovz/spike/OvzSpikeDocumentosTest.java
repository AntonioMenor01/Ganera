package com.ganera.core.ovz.spike;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Download;
import com.microsoft.playwright.Frame;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * MINI-SPIKE (prueba técnica, NO producción) de <b>Documentos</b> en OVZnet (escalón 2, plan 2026-11-17): tras
 * iniciar sesión con las credenciales de un ganadero y elegir la explotación bovina, va a
 * <b>Documentos › Documentos</b>, filtra por tipo de trámite «Baja Animal» [3], pulsa «Buscar», lee la
 * ESTRUCTURA de la tabla {@code GVDocumentos} (columnas y nº de documentos, nunca datos) y, si hay algún
 * justificante, <b>descarga el primero a una carpeta temporal FUERA del repo</b> para conocer su formato
 * (extensión) y tamaño (bytes) — <b>sin imprimir su contenido ni su nombre real</b>. Después sale con «Salir».
 *
 * <p><b>Solo lectura.</b> No presenta, no graba, no adjunta nada. Como seguro, {@link #pulsar} y
 * {@link #pulsarPorTexto} pasan por {@link #prohibirAceptar}, que aborta si el selector/etiqueta apunta a
 * «Aceptar»/«Solicitar»/«Añadir»/«Grabar». En Documentos no hay ninguno de esos botones; es una defensa.
 *
 * <p><b>Mecanismo de localización:</b> igual que {@link OvzSpikeBajaMuerteTest} — se busca el elemento en TODOS
 * los frames por JS (sin exigir visibilidad) y se actúa por JS. El único {@code goto} permitido es la entrada
 * ({@code Inicio.aspx}); dentro se navega solo por menús (evita el detector de «varias instancias» de OVZ).
 *
 * <p><b>Desactivado por defecto</b> (patrón de {@code OvzSpikeBajaMuerteTest}): solo corre con
 * {@code OVZ_SPIKE_DOCUMENTOS=true} y las variables de abajo, que Antonio exporta desde un <b>.env fuera del
 * repo</b> (nunca en el chat, logs ni commits). No se toma ninguna captura. Los logs solo dicen «PASO N OK/FALLO»,
 * selectores, estructura (etiquetas de columna y nº) y, del justificante, SOLO extensión + bytes + carpeta temporal.
 *
 * <p>Variables: {@code OVZ_URL}, {@code OVZ_USUARIO}, {@code OVZ_PASSWORD} (obligatorias). No usa crotal ni REGA.
 *
 * <p>Ejecutar (Git Bash, con el .env exportado):
 * <pre>
 * set -a; source /c/Users/Antonio/Desktop/ganera-ovz-spike/.env; set +a
 * cd backend
 * JAVA_HOME='C:\Users\Antonio\.jdks\temurin-21\jdk-21.0.12.1+1' OVZ_SPIKE_DOCUMENTOS=true \
 *   ./mvnw test -Dtest=OvzSpikeDocumentosTest
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "OVZ_SPIKE_DOCUMENTOS", matches = "true")
class OvzSpikeDocumentosTest {

    /** «Baja Animal» en el desplegable «Tipo Trámite» de Documentos (valor del catálogo, mapa §E3). */
    private static final String VALOR_BAJA_ANIMAL = "3";
    private static final String ID_SALIR = "ctl00_ucBarraUsuario1_btnSalir";
    private static final double TIMEOUT_MS = 20_000;
    private static final double TIMEOUT_DESCARGA_MS = 15_000;

    private String ultimoSelector = "(ninguno)";

    @Test
    void listaDocumentosBajaAnimalYDescargaUnoSinPresentar() {
        String url = requerida("OVZ_URL");
        String usuario = requerida("OVZ_USUARIO");
        String password = requerida("OVZ_PASSWORD");

        try (Playwright playwright = Playwright.create()) {
            Browser navegador = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = navegador.newContext(new Browser.NewContextOptions().setAcceptDownloads(true)).newPage();
            page.setDefaultTimeout(TIMEOUT_MS);
            boolean cerrada = false;
            try {
                // PASO 1 — abrir la entrada de OVZnet (único goto permitido).
                page.navigate(url);
                ok(1, "OVZnet abierto");

                // PASO 2 — login con los ids reales de Inicio.aspx.
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

                // PASO 3 — elegir la explotación bovina (por value, sin exigir visibilidad).
                Frame fSel = frameCon(page, "#ctl00_ucMenu1_ddlDelegado", "desplegable de explotaciones");
                Object opcionesDelegado = fSel.evaluate(JS_OPCIONES_DELEGADO);
                if (!(opcionesDelegado instanceof List<?> opciones)) {
                    throw new IllegalStateException("el desplegable no devolvió opciones");
                }
                List<String> valoresBovinos = new java.util.ArrayList<>();
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
                frameCon(page, "a[href*='wgDocumentos.aspx']", "menu cargado tras elegir explotacion");
                ok(3, "explotacion bovina elegida, menu cargado");

                // PASO 4 — menú Documentos › Documentos por su href (sin escribir URLs).
                pulsar(page, "a[href*='wgDocumentos.aspx']", "menu Documentos (wgDocumentos.aspx)");
                frameCon(page, "[id$='_DDLTipoTramite']", "pagina de Documentos (DDLTipoTramite)");
                ok(4, "pagina Documentos");

                // PASO 5 — filtrar por tipo de trámite «Baja Animal» [3] (por value, mapa §E3).
                Frame fTipo = frameCon(page, "[id$='_DDLTipoTramite']", "desplegable Tipo Tramite");
                seleccionarValor(fTipo, "[id$='_DDLTipoTramite']", VALOR_BAJA_ANIMAL, "tipo Baja Animal");
                ok(5, "tipo de tramite = Baja Animal");

                // PASO 6 — «Buscar» (postback). Tras él aparece GVDocumentos (o el pie «0 documentos»).
                pulsar(page, "[id$='_btnLinkBuscar']", "boton Buscar");
                ok(6, "Buscar pulsado");

                // PASO 7 — leer la ESTRUCTURA de GVDocumentos: columnas y nº de documentos (texto de la app, nunca datos).
                Frame fGrid;
                try {
                    fGrid = frameCon(page, "[id$='_GVDocumentos']", "tabla GVDocumentos");
                } catch (RuntimeException e) {
                    imprimirErroresOvz(page);
                    throw new IllegalStateException("tras «Buscar» no apareció la tabla GVDocumentos (ver mensajes de OVZ)");
                }
                Object estructura = fGrid.evaluate(JS_ESTRUCTURA_GRID);
                System.out.println("ESTRUCTURA GVDocumentos (solo etiquetas/estructura, sin datos): " + estructura);
                String pie = leer(page, "[id$='_LblPieGrid']", "pie del grid");
                if (pie != null) {
                    System.out.println("Pie del grid (texto de la app): " + pie);
                }
                Object nEnlaces = fGrid.evaluate("() => document.querySelectorAll(\"[id$='_GVDocumentos'] a\").length");
                int enlaces = (nEnlaces instanceof Number n) ? n.intValue() : 0;
                ok(7, "estructura de GVDocumentos leida; enlaces en la tabla=" + enlaces);

                // PASO 8 — si hay algún enlace de documento, descargar el PRIMERO a una carpeta temporal FUERA del
                // repo. Solo se imprime extensión + bytes + carpeta; nunca el nombre real ni el contenido.
                if (enlaces == 0) {
                    ok(8, "no hay documentos de tipo «Baja Animal»: nada que descargar");
                } else {
                    try {
                        Download descarga = page.waitForDownload(
                                new Page.WaitForDownloadOptions().setTimeout(TIMEOUT_DESCARGA_MS),
                                () -> fGrid.evaluate(
                                        "() => { const a = document.querySelector(\"[id$='_GVDocumentos'] a\");"
                                                + " if (a) a.click(); }"));
                        String sugerido = descarga.suggestedFilename();
                        String ext = (sugerido != null && sugerido.contains("."))
                                ? sugerido.substring(sugerido.lastIndexOf('.')) : "";
                        Path dir = Files.createTempDirectory("ovz-spike-docs-");
                        Path destino = dir.resolve("justificante-baja" + ext);
                        descarga.saveAs(destino);
                        long bytes = Files.size(destino);
                        ok(8, "justificante descargado — extension=" + (ext.isEmpty() ? "(sin extension)" : ext)
                                + " bytes=" + bytes + " carpeta=" + dir);
                    } catch (RuntimeException e) {
                        System.out.println("PASO 8: el enlace no disparó una descarga en "
                                + (long) TIMEOUT_DESCARGA_MS + " ms (quizá se muestra embebido o abre pestaña nueva); "
                                + "estructura ya registrada. " + e.getClass().getSimpleName());
                    }
                }

                // PASO 9 — salir con «Salir». NUNCA se presenta nada.
                pulsar(page, "#" + ID_SALIR, "boton Salir");
                cerrada = true;
                ok(9, "sesion cerrada con Salir, sin presentar nada");
            } catch (ClicProhibidoException e) {
                System.out.println("BLOQUEO DURO: se intentó pulsar un boton de presentacion. " + e.getMessage());
                salirBestEffort(page);
                cerrada = true;
                fail("BLOQUEO DURO: se intentó pulsar un boton de presentacion — abortado. " + e.getMessage());
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

    // --- Mecanismo de localización (cross-frame, por JS, sin exigir visibilidad) --------------------------

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

    private void escribir(Page page, String css, String valor, String etiqueta) {
        Frame f = frameCon(page, css, etiqueta);
        f.evaluate("([sel, v]) => { const e = document.querySelector(sel); if (e.focus) e.focus();"
                + " e.value = v; e.dispatchEvent(new Event('input', {bubbles:true}));"
                + " e.dispatchEvent(new Event('change', {bubbles:true})); }", List.of(css, valor));
    }

    private void pulsar(Page page, String css, String etiqueta) {
        prohibirAceptar(css);
        prohibirAceptar(etiqueta);
        Frame f = frameCon(page, css, etiqueta);
        f.evaluate("(sel) => { const e = document.querySelector(sel); e.click(); }", css);
        esperarQuietud(page);
    }

    private void seleccionarValor(Frame f, String css, String value, String etiqueta) {
        ultimoSelector = "css=\"" + css + "\" (" + etiqueta + ")";
        f.evaluate("([sel, v]) => { const s = document.querySelector(sel); s.value = v;"
                + " s.dispatchEvent(new Event('change', {bubbles:true})); }", List.of(css, value));
        esperarQuietud(f.page());
    }

    private String leer(Page page, String css, String etiqueta) {
        Frame f;
        try {
            f = frameCon(page, css, etiqueta);
        } catch (RuntimeException e) {
            return null;
        }
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

    // --- Bloqueo duro de botones de presentación (defensa; en Documentos no hay ninguno) ------------------

    private void prohibirAceptar(String texto) {
        if (texto != null) {
            String t = texto.toLowerCase();
            if (t.contains("_btn_aceptar") || t.contains("aceptar") || t.contains("solicitar")
                    || t.contains("btngrabar") || t.contains("añadir") || t.contains("anadir")) {
                throw new ClicProhibidoException("selector/etiqueta='" + texto + "'");
            }
        }
    }

    private static final class ClicProhibidoException extends RuntimeException {
        ClicProhibidoException(String msg) {
            super(msg);
        }
    }

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

    // --- Diagnóstico y utilidades (solo estructura/etiquetas, NUNCA datos) --------------------------------

    /**
     * Estructura de GVDocumentos: nº de filas (incluye cabecera y paginador) y SOLO las etiquetas de columna de
     * la fila de cabecera (th). Nunca el contenido de las celdas de datos (nombres de fichero, fechas…).
     */
    private static final String JS_ESTRUCTURA_GRID = """
            () => {
              const t = document.querySelector("[id$='_GVDocumentos']");
              if (!t) return 'GVDocumentos no encontrada';
              const filas = t.querySelectorAll('tr').length;
              const ths = Array.from(t.querySelectorAll('th')).map(e => (e.textContent || '').trim());
              const anchors = t.querySelectorAll('a').length;
              return { filas: filas, columnas: ths, enlaces: anchors };
            }
            """;

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
                out.push('select id=' + id + ' name=' + name + ' opciones=' + e.options.length);
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

    private void diagnosticoEstructura(Page page) {
        try {
            System.out.println("--- DIAGNOSTICO DE ESTRUCTURA (solo estructura, sin valores) ---");
            System.out.println("URL: " + sinParametros(page.url()));
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

    private void imprimirErroresOvz(Page page) {
        System.out.println("MENSAJES DE OVZ (textos de la aplicacion, no datos personales):");
        boolean alguno = false;
        for (Frame f : page.frames()) {
            try {
                Object res = f.evaluate(JS_ERRORES);
                if (res instanceof List<?> lista) {
                    for (Object t : lista) {
                        System.out.println("   • " + t);
                        alguno = true;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        if (!alguno) {
            System.out.println("   (ninguno visible)");
        }
    }

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

    private String requerida(String nombre) {
        String v = System.getenv(nombre);
        if (v == null || v.isBlank()) {
            fail("Falta la variable de entorno " + nombre + " (expórtala desde tu .env fuera del repo).");
        }
        return v;
    }

    private String normaliza(String s) {
        if (s == null) {
            return "";
        }
        String n = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase().trim();
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
