package com.ganera.core.ovz;

import com.ganera.core.ganadero.CredencialOvzEnClaro;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Download;
import com.microsoft.playwright.Frame;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Navegador REAL de OVZnet para el worker (escalón 2, T4): Playwright (Chromium headless) con el mecanismo que
 * validaron los spikes ({@code OvzSpikeBajaMuerteTest}, {@code OvzSpikeDocumentosTest}): cada elemento se localiza en
 * TODOS los frames por JS (sufijo de id o texto, nunca un id absoluto) y se actúa por JS (foco + value + change para
 * escribir, {@code element.click()} para pulsar). Se navega SOLO por menús: el único {@code goto} es la entrada.
 *
 * <p>Una sesión por trabajo: {@link #iniciarSesion} crea Playwright y el navegador; {@link #salir()} los cierra.
 * Solo existe en el perfil {@code worker}. NO se prueba con tests unitarios (necesita OVZnet real): lo ejerce a mano
 * {@code OvzWorkerEnsayoManualTest} (desactivado por entorno).
 *
 * <p><b>Logs: solo pasos</b> («PASO x OK/FALLO»), nunca un valor (crotal, REGA, usuario, contraseña, contenido).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ganera.ovz.worker.activo", havingValue = "true")
public class PlaywrightNavegadorOvz implements NavegadorOvz {

    private static final double TIMEOUT_MS = 20_000;
    private static final double TIMEOUT_DESCARGA_MS = 30_000;
    private static final int MAX_PAGINAS_DOCUMENTOS = 10;
    private static final String ID_SALIR = "ctl00_ucBarraUsuario1_btnSalir";
    private static final String SUFIJO_ACEPTAR = "_btn_Aceptar";
    private static final String SELECTOR_ACEPTAR = "[id*='_ctl05_'][id$='" + SUFIJO_ACEPTAR + "']";
    private static final String VALOR_BAJA_ANIMAL = "3";
    // Alta por nacimiento (plan 2026-11-23): pestaña «Alta Individual», prefijo ctl02. Su «Aceptar» es P_btnAceptar
    // (sufijo _btnAceptar, distinto del _btn_Aceptar de la baja). «Alta Bovino» es el tipo [1] en Documentos.
    private static final String SUFIJO_ACEPTAR_ALTA = "_btnAceptar";
    private static final String SELECTOR_ACEPTAR_ALTA = "[id*='_ctl02_'][id$='" + SUFIJO_ACEPTAR_ALTA + "']";
    private static final String VALOR_ALTA_BOVINO = "1";
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    private Playwright playwright;
    private Browser browser;
    private BrowserContext contexto;
    private Page page;
    private int paginaDocumentos = 1;

    // ------------------------------------------------------------------ NavegadorOvz

    @Override
    public void iniciarSesion(String url, String usuario, CredencialOvzEnClaro password) {
        if (url == null || url.isBlank()) {
            throw new NavegacionOvzException("Falta configurar la dirección de OVZ en el worker.");
        }
        abrirNavegador();
        try {
            page.navigate(url);
            paso(1, "OVZnet abierto");

            if (!existeEnAlgunFrame("[id$='_txtUsuario']", 5_000)) {
                if (existeEnAlgunFrame("[id$='_btnAccesoCertificado']", 1_000)) {
                    throw new LoginCertificadoException();
                }
                throw new NavegacionOvzException("No se encontró el formulario de acceso de OVZ.");
            }
            escribir("[id$='_txtUsuario']", usuario);
            escribir("[id$='_txtClave']", password.valor());
            pulsar("[id$='_btnLinkEntrar']");
            try {
                page.waitForFunction(
                        "() => location.pathname.toLowerCase().endsWith('/presentacion.aspx')"
                                + " || document.getElementById('ctl00_ucMenu1_ddlDelegado') !== null",
                        null, new Page.WaitForFunctionOptions().setTimeout(TIMEOUT_MS));
            } catch (RuntimeException e) {
                if (page.url().toLowerCase(Locale.ROOT).contains("inicio.aspx")) {
                    throw new CredencialesOvzInvalidasException();
                }
                throw new NavegacionOvzException("OVZ no respondió tras iniciar sesión.");
            }
            comprobarSesion();
            paso(2, "sesión iniciada");
        } catch (RuntimeException e) {
            pasoFallo(2, e);
            throw e;
        }
    }

    @Override
    public void prepararBaja(String crotal) {
        try {
            Frame fSel = frameCon("#ctl00_ucMenu1_ddlDelegado");
            Object opcionesDelegado = fSel.evaluate(JS_OPCIONES_DELEGADO);
            if (!(opcionesDelegado instanceof List<?> opciones)) {
                throw new NavegacionOvzException("OVZ no devolvió la lista de explotaciones.");
            }
            List<String> bovinas = new ArrayList<>();
            for (Object o : opciones) {
                Map<?, ?> m = (Map<?, ?>) o;
                if (normaliza(String.valueOf(m.get("especie"))).endsWith("bovidos")) {
                    bovinas.add(String.valueOf(m.get("value")));
                }
            }
            if (bovinas.size() > 1) {
                throw new NavegacionOvzException("La cuenta de OVZ tiene varias explotaciones bovinas; la baja se hace a mano.");
            }
            if (bovinas.isEmpty()) {
                throw new NavegacionOvzException("La cuenta de OVZ no tiene explotación bovina.");
            }
            seleccionarValor(fSel, "#ctl00_ucMenu1_ddlDelegado", bovinas.get(0));
            frameCon("a[href*='wgTramites.aspx']");
            paso(3, "explotación bovina elegida");

            pulsar("a[href*='wgTramites.aspx']");
            pulsarPorTexto("Bóvidos");
            pulsarPorTexto("Baja de Bóvidos");
            paso(4, "pestaña Baja de Bóvidos");

            escribir("[id*='_ctl05_'][id$='_TxtCrotal']", crotal);
            pulsar("[id*='_ctl05_'][id$='_LinkBuscar']");
            try {
                frameCon("[id*='_ctl05_'][id$='_txtCodigoRIIA']");
            } catch (RuntimeException e) {
                throw new NavegacionOvzException("OVZ no encontró el animal al buscar el crotal.");
            }
            paso(5, "crotal buscado; pantalla 2 cargada");
        } catch (RuntimeException e) {
            pasoFallo(5, e);
            throw e;
        }
    }

    @Override
    public String leerCodigoRiia() {
        return leer("[id*='_ctl05_'][id$='_txtCodigoRIIA']");
    }

    @Override
    public String leerExplotacionUbicacion() {
        return leer("[id*='_ctl05_'][id$='_txtExplotacionUbicacion']");
    }

    @Override
    public void rellenarFechaMuerte(String ddMMyyyy) {
        escribir("[id*='_ctl05_'][id$='_TextFechaMuerte']", ddMMyyyy);
        paso(6, "fecha de muerte rellenada");
    }

    @Override
    public void rellenarMer(String mer) {
        if (mer == null || mer.isBlank()) {
            return;
        }
        escribir("[id*='_ctl05_'][id$='_TxtMer']", mer);
        paso(7, "MER rellenado");
    }

    /**
     * El ÚNICO sitio que pulsa «Aceptar». Resuelve el botón, LEE su id y su texto/valor y solo si el id acaba en
     * {@code _btn_Aceptar} Y el texto es «Aceptar» hace el clic. Si no cuadra, lanza {@link AceptarException} SIN
     * pulsar nada (hallazgo #4 de T0).
     */
    @Override
    public void pulsarAceptar() {
        Object descripcion;
        try {
            Frame f = frameCon(SELECTOR_ACEPTAR);
            descripcion = f.evaluate("(sel) => { const e = document.querySelector(sel); if (!e) return null;"
                    + " const t = (e.tagName === 'INPUT') ? (e.getAttribute('value') || '') : (e.textContent || '');"
                    + " return { id: e.id || '', texto: t.trim() }; }", SELECTOR_ACEPTAR);
        } catch (RuntimeException e) {
            throw new AceptarException();
        }
        if (!(descripcion instanceof Map<?, ?> d)) {
            throw new AceptarException();
        }
        String id = String.valueOf(d.get("id"));
        String texto = String.valueOf(d.get("texto"));
        if (!id.endsWith(SUFIJO_ACEPTAR) || !normaliza(texto).equals("aceptar")) {
            throw new AceptarException();
        }
        Frame f = frameCon("[id='" + id + "']");
        f.evaluate("(id) => { const e = document.getElementById(id); e.click(); }", id);
        esperarQuietud();
        paso(8, "«Aceptar» pulsado");
        comprobarSesion();
    }

    @Override
    public List<DocumentoCandidato> listarDocumentosBajaAnimal() {
        try {
            if (existeEnAlgunFrame("a[href*='wgDocumentos.aspx']", 5_000)) {
                pulsar("a[href*='wgDocumentos.aspx']");
            } else {
                throw new NavegacionOvzException("No se encontró el menú Documentos de OVZ.");
            }
            Frame fTipo = frameCon("[id$='_DDLTipoTramite']");
            seleccionarValor(fTipo, "[id$='_DDLTipoTramite']", VALOR_BAJA_ANIMAL);
            pulsar("[id$='_btnLinkBuscar']");
            try {
                frameCon("[id$='_GVDocumentos']");
            } catch (RuntimeException e) {
                // Sin documentos de ese tipo OVZ puede no pintar la tabla: lista vacía.
                paso(9, "Documentos sin tabla");
                paginaDocumentos = 1;
                return List.of();
            }
            paginaDocumentos = 1;
            List<DocumentoCandidato> todos = new ArrayList<>();
            for (int pagina = 1; pagina <= MAX_PAGINAS_DOCUMENTOS; pagina++) {
                if (pagina > 1 && !irAPagina(pagina)) {
                    break;
                }
                todos.addAll(leerFilasPagina(pagina));
            }
            paso(9, "Documentos listados");
            return todos;
        } catch (RuntimeException e) {
            pasoFallo(9, e);
            throw e;
        }
    }

    @Override
    public byte[] descargarDocumento(DocumentoCandidato candidato) {
        String[] partes = candidato.referencia().split(":");
        int pagina = Integer.parseInt(partes[0]);
        int fila = Integer.parseInt(partes[1]);
        Path temporal = null;
        try {
            if (!irAPagina(pagina)) {
                throw new NavegacionOvzException("No se pudo volver a la página de Documentos.");
            }
            Frame fGrid = frameCon("[id$='_GVDocumentos']");
            Download descarga = page.waitForDownload(
                    new Page.WaitForDownloadOptions().setTimeout(TIMEOUT_DESCARGA_MS),
                    () -> fGrid.evaluate("(i) => { " + JS_FILAS_DATOS
                            + " const f = filasDatos(); if (f[i]) { f[i].querySelector('a').click(); } }", fila));
            temporal = Files.createTempFile("ovz-justificante-", ".pdf");
            descarga.saveAs(temporal);
            return Files.readAllBytes(temporal);
        } catch (IOException e) {
            throw new NavegacionOvzException("No se pudo descargar el justificante de OVZ.");
        } finally {
            if (temporal != null) {
                try {
                    Files.deleteIfExists(temporal);
                } catch (IOException ignored) {
                    // best-effort
                }
            }
        }
    }

    @Override
    public void salir() {
        try {
            if (page != null) {
                try {
                    for (Frame f : page.frames()) {
                        Object n = f.evaluate("(sel) => document.querySelectorAll(sel).length", "#" + ID_SALIR);
                        if (n instanceof Number num && num.intValue() > 0) {
                            f.evaluate("(sel) => { const e = document.querySelector(sel); e.click(); }", "#" + ID_SALIR);
                            esperarQuietudSinComprobar();
                            break;
                        }
                    }
                } catch (RuntimeException e) {
                    log.warn("PASO Salir: no se pudo pulsar Salir; la sesión de OVZ se dejará caducar");
                }
            }
        } finally {
            cerrarNavegador();
        }
    }

    // ------------------------------------------------------------------ alta por nacimiento (plan 2026-11-23, T5)
    // Selectores del mapa (sección B, prefijo ctl02). NO validados en vivo todavía: el modo REAL del alta queda
    // pendiente de una sesión con credenciales, como lo estuvo la baja. En ENSAYO el worker recorre hasta antes de
    // «Aceptar».

    @Override
    public void prepararAlta(String crotalTernero) {
        try {
            elegirExplotacionBovina();
            pulsar("a[href*='wgTramites.aspx']");
            pulsarPorTexto("Bóvidos");
            pulsarPorTexto("Alta Individual");
            frameCon("[id*='_ctl02_'][id$='_gvCrotalesRepartidos']");
            paso(4, "pestaña Alta Individual");

            // El crotal del ternero debe figurar «Libre» en la tabla de repartidos; si no, SEGURO (no se presenta).
            Frame fTabla = frameCon("[id*='_ctl02_'][id$='_gvCrotalesRepartidos']");
            Object estado = fTabla.evaluate("(objetivo) => { " + JS_CROTAL_REPARTIDO + " return seleccionar(objetivo); }",
                    normaliza(crotalTernero));
            if ("NO_ENCONTRADO".equals(String.valueOf(estado))) {
                throw new NavegacionOvzException("El crotal del ternero no está entre los crotales repartidos en OVZ.");
            }
            if ("NO_LIBRE".equals(String.valueOf(estado))) {
                throw new NavegacionOvzException("El crotal del ternero no está «Libre» en OVZ; no se puede dar el alta.");
            }
            esperarQuietud();
            try {
                frameCon("[id*='_ctl02_'][id$='_lblcrotal']");
            } catch (RuntimeException e) {
                throw new NavegacionOvzException("OVZ no cargó el formulario del alta tras elegir el crotal.");
            }
            paso(5, "crotal del ternero elegido; pantalla 2 cargada");
        } catch (RuntimeException e) {
            pasoFallo(5, e);
            throw e;
        }
    }

    @Override
    public String leerCrotalSeleccionado() {
        return leer("[id*='_ctl02_'][id$='_lblcrotal']");
    }

    @Override
    public void rellenarFormularioAlta(DatosFormularioAlta datos) {
        escribir("[id*='_ctl02_'][id$='_TxtMadre']", datos.crotalMadre());
        escribir("[id*='_ctl02_'][id$='_TxtFNac']", datos.fechaNacimientoDdMmAaaa());
        seleccionarValor(frameCon("[id*='_ctl02_'][id$='_DDLSexo']"), "[id*='_ctl02_'][id$='_DDLSexo']",
                datos.sexoCodigo());
        seleccionarValor(frameCon("[id*='_ctl02_'][id$='_DDLTipoIdentificador']"),
                "[id*='_ctl02_'][id$='_DDLTipoIdentificador']", datos.tipoIdentificadorCodigo());
        escribir("[id*='_ctl02_'][id$='_TxtFId']", datos.fechaIdentificacionDdMmAaaa());
        seleccionarValor(frameCon("[id*='_ctl02_'][id$='_DDLRazas']"), "[id*='_ctl02_'][id$='_DDLRazas']",
                datos.razaCodigo());
        // Emisión del DI (radios P_rblImpDI_0 = OVZ, P_rblImpDI_1 = OVZNET). La oficina solo si la emisión es en la OVZ.
        if (ListasOvz.CODIGO_EMISION_DI_OVZ.equals(datos.emisionDiCodigo())) {
            pulsar("[id*='_ctl02_'][id$='_rblImpDI_0']");
            if (datos.oficinaDiCodigo() != null && !datos.oficinaDiCodigo().isBlank()) {
                seleccionarValor(frameCon("[id*='_ctl02_'][id$='_DDLOVZ']"), "[id*='_ctl02_'][id$='_DDLOVZ']",
                        datos.oficinaDiCodigo());
            }
        } else {
            pulsar("[id*='_ctl02_'][id$='_rblImpDI_1']");
        }
        paso(6, "formulario del alta rellenado");
    }

    @Override
    public void pulsarAceptarAlta() {
        Object descripcion;
        try {
            Frame f = frameCon(SELECTOR_ACEPTAR_ALTA);
            descripcion = f.evaluate("(sel) => { const e = document.querySelector(sel); if (!e) return null;"
                    + " const t = (e.tagName === 'INPUT') ? (e.getAttribute('value') || '') : (e.textContent || '');"
                    + " return { id: e.id || '', texto: t.trim() }; }", SELECTOR_ACEPTAR_ALTA);
        } catch (RuntimeException e) {
            throw new AceptarException();
        }
        if (!(descripcion instanceof Map<?, ?> d)) {
            throw new AceptarException();
        }
        String id = String.valueOf(d.get("id"));
        String texto = String.valueOf(d.get("texto"));
        if (!id.endsWith(SUFIJO_ACEPTAR_ALTA) || !normaliza(texto).equals("aceptar")) {
            throw new AceptarException();
        }
        Frame f = frameCon("[id='" + id + "']");
        f.evaluate("(id) => { const e = document.getElementById(id); e.click(); }", id);
        esperarQuietud();
        paso(8, "«Aceptar» del alta pulsado");
        comprobarSesion();
    }

    @Override
    public List<DocumentoCandidato> listarDocumentosAltaBovino() {
        return listarDocumentos(VALOR_ALTA_BOVINO);
    }

    /** Elige la única explotación bovina del desplegable (como la baja); lanza si hay varias o ninguna. */
    private void elegirExplotacionBovina() {
        Frame fSel = frameCon("#ctl00_ucMenu1_ddlDelegado");
        Object opcionesDelegado = fSel.evaluate(JS_OPCIONES_DELEGADO);
        if (!(opcionesDelegado instanceof List<?> opciones)) {
            throw new NavegacionOvzException("OVZ no devolvió la lista de explotaciones.");
        }
        List<String> bovinas = new ArrayList<>();
        for (Object o : opciones) {
            Map<?, ?> m = (Map<?, ?>) o;
            if (normaliza(String.valueOf(m.get("especie"))).endsWith("bovidos")) {
                bovinas.add(String.valueOf(m.get("value")));
            }
        }
        if (bovinas.size() > 1) {
            throw new NavegacionOvzException("La cuenta de OVZ tiene varias explotaciones bovinas; el alta se hace a mano.");
        }
        if (bovinas.isEmpty()) {
            throw new NavegacionOvzException("La cuenta de OVZ no tiene explotación bovina.");
        }
        seleccionarValor(fSel, "#ctl00_ucMenu1_ddlDelegado", bovinas.get(0));
        frameCon("a[href*='wgTramites.aspx']");
        paso(3, "explotación bovina elegida");
    }

    /** Documentos › Documentos, filtra por el tipo dado y lee todas las filas (con paginación). Compartido baja/alta. */
    private List<DocumentoCandidato> listarDocumentos(String valorTipoTramite) {
        try {
            if (existeEnAlgunFrame("a[href*='wgDocumentos.aspx']", 5_000)) {
                pulsar("a[href*='wgDocumentos.aspx']");
            } else {
                throw new NavegacionOvzException("No se encontró el menú Documentos de OVZ.");
            }
            Frame fTipo = frameCon("[id$='_DDLTipoTramite']");
            seleccionarValor(fTipo, "[id$='_DDLTipoTramite']", valorTipoTramite);
            pulsar("[id$='_btnLinkBuscar']");
            try {
                frameCon("[id$='_GVDocumentos']");
            } catch (RuntimeException e) {
                paso(9, "Documentos sin tabla");
                paginaDocumentos = 1;
                return List.of();
            }
            paginaDocumentos = 1;
            List<DocumentoCandidato> todos = new ArrayList<>();
            for (int pagina = 1; pagina <= MAX_PAGINAS_DOCUMENTOS; pagina++) {
                if (pagina > 1 && !irAPagina(pagina)) {
                    break;
                }
                todos.addAll(leerFilasPagina(pagina));
            }
            paso(9, "Documentos listados");
            return todos;
        } catch (RuntimeException e) {
            pasoFallo(9, e);
            throw e;
        }
    }

    /**
     * JS de la tabla de crotales repartidos (pantalla 1 del alta): busca la fila cuyo crotal (1ª celda, normalizado)
     * es el objetivo; si su estado (2ª celda) contiene «libre», pulsa su enlace y devuelve «OK»; si no, «NO_LIBRE»;
     * si no está, «NO_ENCONTRADO».
     */
    private static final String JS_CROTAL_REPARTIDO = """
            const norm = (s) => (s || '').normalize('NFD').replace(/[\\u0300-\\u036f]/g, '')
              .replace(/\\s+/g, '').toUpperCase();
            const seleccionar = (objetivo) => {
              const tabla = document.querySelector("[id*='_ctl02_'][id$='_gvCrotalesRepartidos']");
              if (!tabla) return 'NO_ENCONTRADO';
              const filas = Array.from(tabla.querySelectorAll('tr'));
              for (const tr of filas) {
                const celdas = Array.from(tr.children).filter(c => c.tagName === 'TD');
                if (celdas.length < 2) continue;
                if (norm(celdas[0].textContent) === objetivo) {
                  const estado = norm(celdas[1].textContent);
                  if (estado.indexOf('LIBRE') < 0) return 'NO_LIBRE';
                  const enlace = celdas[0].querySelector('a') || tr.querySelector('a');
                  if (!enlace) return 'NO_ENCONTRADO';
                  enlace.click();
                  return 'OK';
                }
              }
              return 'NO_ENCONTRADO';
            };
            """;

    // ------------------------------------------------------------------ mecanismo cross-frame (de los spikes)

    private void abrirNavegador() {
        cerrarNavegador();
        playwright = Playwright.create();
        // --no-sandbox/--disable-dev-shm-usage: el worker corre como root en un contenedor Docker de un solo uso.
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true)
                .setArgs(List.of("--no-sandbox", "--disable-dev-shm-usage")));
        contexto = browser.newContext(new Browser.NewContextOptions().setAcceptDownloads(true));
        page = contexto.newPage();
        page.setDefaultTimeout(TIMEOUT_MS);
        paginaDocumentos = 1;
    }

    private void cerrarNavegador() {
        try {
            if (contexto != null) {
                contexto.close();
            }
        } catch (RuntimeException ignored) {
            // best-effort
        }
        try {
            if (browser != null) {
                browser.close();
            }
        } catch (RuntimeException ignored) {
            // best-effort
        }
        try {
            if (playwright != null) {
                playwright.close();
            }
        } catch (RuntimeException ignored) {
            // best-effort
        }
        page = null;
        contexto = null;
        browser = null;
        playwright = null;
    }

    private Frame frameCon(String css) {
        long fin = System.currentTimeMillis() + (long) TIMEOUT_MS;
        do {
            Frame f = frameConOnce(css);
            if (f != null) {
                return f;
            }
            page.waitForTimeout(200);
        } while (System.currentTimeMillis() < fin);
        throw new NavegacionOvzException("No se encontró un elemento esperado en la pantalla de OVZ.");
    }

    private Frame frameConOnce(String css) {
        for (Frame f : page.frames()) {
            try {
                Object n = f.evaluate("(sel) => document.querySelectorAll(sel).length", css);
                if (n instanceof Number num && num.intValue() > 0) {
                    return f;
                }
            } catch (RuntimeException ignored) {
                // frame aún navegando: se reintenta
            }
        }
        return null;
    }

    private boolean existeEnAlgunFrame(String css, long esperaMs) {
        long fin = System.currentTimeMillis() + esperaMs;
        do {
            if (frameConOnce(css) != null) {
                return true;
            }
            page.waitForTimeout(200);
        } while (System.currentTimeMillis() < fin);
        return false;
    }

    private void escribir(String css, String valor) {
        Frame f = frameCon(css);
        f.evaluate("([sel, v]) => { const e = document.querySelector(sel); if (e.focus) e.focus();"
                + " e.value = v; e.dispatchEvent(new Event('input', {bubbles:true}));"
                + " e.dispatchEvent(new Event('change', {bubbles:true})); }", List.of(css, valor));
    }

    private void pulsar(String css) {
        if (css.toLowerCase(Locale.ROOT).contains(SUFIJO_ACEPTAR.toLowerCase(Locale.ROOT))) {
            // «Aceptar» solo se pulsa en pulsarAceptar(), con su doble comprobación.
            throw new AceptarException();
        }
        Frame f = frameCon(css);
        f.evaluate("(sel) => { const e = document.querySelector(sel); e.click(); }", css);
        esperarQuietud();
    }

    private void seleccionarValor(Frame f, String css, String value) {
        Object seleccionada = f.evaluate("([sel, v]) => { const s = document.querySelector(sel); if (!s) return false;"
                + " if (!Array.from(s.options).some(o => o.value === v)) return false;"
                + " s.value = v; s.dispatchEvent(new Event('change', {bubbles:true})); return true; }",
                List.of(css, value));
        if (!(seleccionada instanceof Boolean ok) || !ok) {
            throw new NavegacionOvzException("Un desplegable de OVZ no tiene la opción esperada; ¿ha cambiado OVZ?");
        }
        esperarQuietud();
    }

    private void pulsarPorTexto(String texto) {
        String objetivo = normaliza(texto);
        long fin = System.currentTimeMillis() + (long) TIMEOUT_MS;
        do {
            for (Frame f : page.frames()) {
                try {
                    Object res = f.evaluate(JS_TEXTOS_CLICABLES);
                    if (res instanceof List<?> textos) {
                        for (int i = 0; i < textos.size(); i++) {
                            String candidato = normaliza(String.valueOf(textos.get(i)));
                            if (candidato.equals(objetivo)) {
                                if (candidato.equals("aceptar")) {
                                    throw new AceptarException();
                                }
                                f.evaluate(JS_CLICK_INDICE, i);
                                esperarQuietud();
                                return;
                            }
                        }
                    }
                } catch (AceptarException e) {
                    throw e;
                } catch (RuntimeException ignored) {
                    // frame aún navegando
                }
            }
            page.waitForTimeout(200);
        } while (System.currentTimeMillis() < fin);
        throw new NavegacionOvzException("No se encontró un enlace esperado en OVZ.");
    }

    private String leer(String css) {
        Frame f = frameCon(css);
        Object r = f.evaluate("(sel) => { const e = document.querySelector(sel); if (!e) return null;"
                + " const v = (e.value !== undefined && e.value !== null && e.value !== '')"
                + " ? e.value : (e.textContent || ''); const t = v.trim(); return t ? t : null; }", css);
        return r == null ? null : r.toString();
    }

    private void esperarQuietud() {
        esperarQuietudSinComprobar();
        comprobarSesion();
    }

    private void esperarQuietudSinComprobar() {
        try {
            page.waitForLoadState();
        } catch (RuntimeException ignored) {
            // la navegación sigue; los siguientes pasos esperan por su elemento
        }
    }

    /** OVZ avisa de una sesión duplicada o caducada con AvisoSesion.aspx. */
    private void comprobarSesion() {
        try {
            if (page != null && page.url().toLowerCase(Locale.ROOT).contains("avisosesion.aspx")) {
                throw new SesionCaducadaException();
            }
        } catch (SesionCaducadaException e) {
            throw e;
        } catch (RuntimeException ignored) {
            // page cerrada: lo notará el siguiente paso
        }
    }

    // ------------------------------------------------------------------ Documentos (paginación y filas)

    private boolean irAPagina(int pagina) {
        if (pagina == paginaDocumentos) {
            return true;
        }
        String css = "[id$='_GVDocumentos'] a[href*='Page$" + pagina + "']";
        if (!existeEnAlgunFrame(css, 3_000)) {
            return false;
        }
        pulsar(css);
        frameCon("[id$='_GVDocumentos']");
        paginaDocumentos = pagina;
        return true;
    }

    private List<DocumentoCandidato> leerFilasPagina(int pagina) {
        Frame fGrid = frameCon("[id$='_GVDocumentos']");
        Object res = fGrid.evaluate("() => { " + JS_FILAS_DATOS
                + " return filasDatos().map(f => ({ fecha: textoFecha(f) })); }");
        List<DocumentoCandidato> filas = new ArrayList<>();
        if (res instanceof List<?> lista) {
            for (int i = 0; i < lista.size(); i++) {
                Map<?, ?> m = (Map<?, ?>) lista.get(i);
                filas.add(new DocumentoCandidato(pagina + ":" + i, parsearFecha(m.get("fecha"))));
            }
        }
        return filas;
    }

    /**
     * Fecha de la tabla de Documentos de OVZ (hora de Madrid). Con hora: ese instante. Solo fecha: el FINAL de ese día
     * (así un justificante del día no queda fuera de la ventana «posterior al clic»; lo decide el crotal del PDF). Si
     * no se entiende, «ahora»: se descarga y se decide por el crotal (nunca se descarta un justificante por su fecha).
     */
    static Instant parsearFecha(Object texto) {
        if (texto == null) {
            return Instant.now();
        }
        String t = texto.toString().trim();
        for (String patron : List.of("dd/MM/yyyy HH:mm:ss", "dd/MM/yyyy H:mm:ss", "dd/MM/yyyy HH:mm", "dd/MM/yyyy H:mm")) {
            try {
                return LocalDateTime.parse(t, DateTimeFormatter.ofPattern(patron)).atZone(MADRID).toInstant();
            } catch (DateTimeParseException ignored) {
                // se prueba el siguiente patrón
            }
        }
        try {
            return LocalDate.parse(t, DateTimeFormatter.ofPattern("dd/MM/yyyy")).plusDays(1).atStartOfDay(MADRID)
                    .toInstant().minusSeconds(1);
        } catch (DateTimeParseException e) {
            return Instant.now();
        }
    }

    /**
     * JS compartido: {@code filasDatos()} = filas de datos de GVDocumentos (con enlace en la primera celda y tantas
     * celdas como columnas de cabecera; descarta cabecera y paginador) y {@code textoFecha(fila)} = texto de la celda de
     * «Fecha Creacion».
     */
    private static final String JS_FILAS_DATOS = """
            const tabla = document.querySelector("[id$='_GVDocumentos']");
            const cabecera = tabla ? Array.from(tabla.querySelectorAll('th')).map(e => (e.textContent || '').trim()) : [];
            const idxFecha = (() => {
              const i = cabecera.findIndex(h => h.toLowerCase().normalize('NFD').replace(/[\\u0300-\\u036f]/g, '').indexOf('fecha') >= 0);
              return i >= 0 ? i : cabecera.length - 1;
            })();
            const filasDatos = () => tabla ? Array.from(tabla.querySelectorAll('tr')).filter(tr => {
              const celdas = Array.from(tr.children).filter(c => c.tagName === 'TD');
              return celdas.length === cabecera.length && cabecera.length > 0 && celdas[0].querySelector('a');
            }) : [];
            const textoFecha = (tr) => {
              const celdas = Array.from(tr.children).filter(c => c.tagName === 'TD');
              return (celdas[idxFecha] ? celdas[idxFecha].textContent : '').trim();
            };
            """;

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

    private static final String JS_TEXTOS_CLICABLES = """
            () => {
              const els = Array.from(document.querySelectorAll(
                "a, button, input[type=submit], input[type=button], input[type=image], [onclick]"));
              return els.map(e => e.tagName === 'INPUT'
                ? (e.getAttribute('value') || e.getAttribute('alt') || '').trim()
                : (e.textContent || '').trim());
            }
            """;

    private static final String JS_CLICK_INDICE = """
            (i) => {
              const els = Array.from(document.querySelectorAll(
                "a, button, input[type=submit], input[type=button], input[type=image], [onclick]"));
              if (els[i]) els[i].click();
            }
            """;

    // ------------------------------------------------------------------ utilidades

    /** Minúsculas y sin acentos (para casar «Bóvidos»/«bovidos»). */
    private static String normaliza(String s) {
        if (s == null) {
            return "";
        }
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }

    private static void paso(int n, String que) {
        log.info("PASO {} OK: {}", n, que);
    }

    private static void pasoFallo(int n, RuntimeException e) {
        log.warn("PASO {} FALLO: {}", n, e.getClass().getSimpleName());
    }
}
