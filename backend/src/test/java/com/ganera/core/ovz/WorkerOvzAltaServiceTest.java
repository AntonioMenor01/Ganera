package com.ganera.core.ovz;

import com.ganera.core.explotacion.Explotacion;
import com.ganera.core.explotacion.ExplotacionRepository;
import com.ganera.core.ganadero.DescifradoCredencialOvz;
import com.ganera.core.ganadero.Ganadero;
import com.ganera.core.ganadero.GanaderoRepository;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.shared.auditoria.LimpiezaAuditoria;
import com.ganera.core.shared.crypto.CifradorAesGcm;
import com.ganera.core.tramite.EstadoTrabajoOvz;
import com.ganera.core.tramite.EstadoTramite;
import com.ganera.core.tramite.ResolucionCrotal;
import com.ganera.core.tramite.TipoTramite;
import com.ganera.core.tramite.TipoTrabajoOvz;
import com.ganera.core.tramite.TrabajoOvz;
import com.ganera.core.tramite.TrabajoOvzRepository;
import com.ganera.core.tramite.TrabajoOvzService;
import com.ganera.core.tramite.Tramite;
import com.ganera.core.tramite.TramiteCrotal;
import com.ganera.core.tramite.TramiteCrotalRepository;
import com.ganera.core.tramite.TramiteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Worker de OVZ para el ALTA por nacimiento (plan 2026-11-23, T4) con un {@link NavegadorOvzFalso} a mano. Mismo
 * contrato que la baja: en ENSAYO NUNCA se pulsa «Aceptar»; error ANTES de «Aceptar» = SEGURO; error DESPUÉS =
 * INCIERTO salvo que Documentos confirme el alta, y la presentación nunca se reintenta. La auditoría usa OVZ_ALTA_*.
 */
@SpringBootTest
class WorkerOvzAltaServiceTest {

    private static final String TERNERO = "ES340000001111";
    private static final String MADRE = "ES340000002222";
    private static final String REGA = "ES340000000009";
    private static final String PASSWORD = "password-ovz-worker-alta-inventada";

    @Autowired private TrabajoOvzService trabajoOvzService;
    @Autowired private TrabajoOvzRepository trabajoOvzRepository;
    @Autowired private TramiteRepository tramiteRepository;
    @Autowired private TramiteCrotalRepository tramiteCrotalRepository;
    @Autowired private GanaderoRepository ganaderoRepository;
    @Autowired private ExplotacionRepository explotacionRepository;
    @Autowired private GestoriaRepository gestoriaRepository;
    @Autowired private DescifradoCredencialOvz descifradoCredencialOvz;
    @Autowired private CifradorAesGcm cifrador;
    @Autowired private Clock clock;
    @Autowired private JdbcTemplate jdbcTemplate;

    private final NavegadorOvzFalso navegador = new NavegadorOvzFalso();
    private Gestoria gestoria;
    private Ganadero ganadero;
    private Tramite tramite;
    private TrabajoOvz trabajo;

    @BeforeEach
    void preparar() {
        LimpiezaAuditoria.borrar(jdbcTemplate);
        gestoria = gestoriaRepository.save(new Gestoria("Worker OVZ alta"));
        ganadero = new Ganadero();
        ganadero.setGestoria(gestoria);
        ganadero.setNombre("Ganadero alta");
        ganadero.setOvzUsuario("usuario-ovz-alta");
        ganadero.setOvzPasswordCifrada(cifrador.cifrar(PASSWORD));
        ganaderoRepository.save(ganadero);
        Explotacion explotacion = new Explotacion();
        explotacion.setGestoria(gestoria);
        explotacion.setGanadero(ganadero);
        explotacion.setCodigoRega(REGA);
        explotacion.setNombre("Finca alta");
        explotacionRepository.save(explotacion);
        tramite = new Tramite();
        tramite.setGestoria(gestoria);
        tramite.setExplotacion(explotacion);
        tramite.setEstado(EstadoTramite.APROBADO);
        tramite.setTipoTramite(TipoTramite.ALTA_NACIMIENTO);
        tramite.setFechaNacimiento(LocalDate.of(2026, 11, 1));
        tramite.setFechaIdentificacion(LocalDate.of(2026, 11, 3));
        tramite.setSexo("H");
        tramite.setCrotalMadre(MADRE);
        tramite.setRaza("14"); // RETINTA
        tramiteRepository.save(tramite);
        TramiteCrotal tc = new TramiteCrotal();
        tc.setGestoria(gestoria);
        tc.setTramite(tramite);
        tc.setCrotalIndicado(TERNERO);
        tc.setCrotal(TERNERO);
        tc.setResolucion(ResolucionCrotal.EN_INVENTARIO);
        tramiteCrotalRepository.save(tc);
        Instant ahora = Instant.now();
        trabajo = new TrabajoOvz();
        trabajo.setGestoria(gestoria);
        trabajo.setTramite(tramite);
        trabajo.setGanadero(ganadero);
        trabajo.setTipo(TipoTrabajoOvz.ALTA_NACIMIENTO);
        trabajo.setEstado(EstadoTrabajoOvz.PENDIENTE);
        trabajo.setCreatedAt(ahora);
        trabajo.setActualizadoEn(ahora);
        trabajoOvzRepository.saveAndFlush(trabajo);

        // Por defecto la pantalla 2 muestra el crotal del ternero (la verificación cuadra).
        navegador.crotalSeleccionado = TERNERO;
    }

    @AfterEach
    void limpiar() {
        LimpiezaAuditoria.borrar(jdbcTemplate);
        jdbcTemplate.update("delete from documento_ovz_contenido");
        jdbcTemplate.update("delete from documento_ovz");
        jdbcTemplate.update("delete from trabajo_ovz");
        tramiteCrotalRepository.deleteAll();
        tramiteRepository.deleteAll();
        explotacionRepository.deleteAll();
        ganaderoRepository.deleteAll();
        gestoriaRepository.deleteAll();
    }

    private WorkerOvzService worker(String modo) {
        return new WorkerOvzService(navegador, trabajoOvzService, ganaderoRepository, descifradoCredencialOvz, clock,
                "http://ovz.invalid/entrada", modo, 1, Duration.ZERO);
    }

    // ---- datosPresentacionAlta ----

    @Test
    void datosPresentacionAltaLlevaLosCamposDelAlta() {
        DatosPresentacionAlta datos = trabajoOvzService.datosPresentacionAlta(trabajo.getId(), gestoria.getId());
        assertThat(datos.crotalTernero()).isEqualTo(TERNERO);
        assertThat(datos.crotalMadre()).isEqualTo(MADRE);
        assertThat(datos.rega()).isEqualTo(REGA);
        assertThat(datos.fechaNacimientoDdMmAaaa()).isEqualTo("01/11/2026");
        assertThat(datos.fechaIdentificacionDdMmAaaa()).isEqualTo("03/11/2026");
        assertThat(datos.sexoCodigo()).isEqualTo("H");
        assertThat(datos.razaCodigo()).isEqualTo("14");
        assertThat(datos.tipoIdentificadorCodigo()).isEqualTo("04");
        assertThat(datos.emisionDiCodigo()).isEqualTo("OVZNET"); // sin preferencia: imprimir (sin tasas)
        assertThat(datos.oficinaDiCodigo()).isNull();
        assertThat(datos.usaCertificado()).isFalse();
        assertThat(datos.toString()).isEqualTo("DatosPresentacionAlta[REDACTADO]");
    }

    @Test
    void sinCrotalDeLaMadreEsErrorSeguroSinAbrirOvz() {
        jdbcTemplate.update("update tramite set crotal_madre = null where id = ?", tramite.getId());

        worker("REAL").procesarUno();

        assertThat(navegador.llamadas).isEmpty();
        assertThat(claseError()).isEqualTo("SEGURO");
        assertThat(estadoTramite()).isEqualTo("ERROR_OVZ");
    }

    // ---- ENSAYO ----

    @Test
    void ensayoFelizLlegaHastaAntesDeAceptarYNuncaPulsaAceptarDelAlta() {
        worker("ENSAYO").procesarUno();

        assertThat(navegador.veces("pulsarAceptarAlta")).isZero();
        assertThat(navegador.llamadas).contains("iniciarSesion", "prepararAlta", "rellenarFormularioAlta", "salir");
        assertThat(navegador.ultimaClave).isEqualTo(PASSWORD);
        assertThat(navegador.datosAlta.crotalMadre()).isEqualTo(MADRE);
        assertThat(navegador.datosAlta.sexoCodigo()).isEqualTo("H");
        assertThat(navegador.datosAlta.razaCodigo()).isEqualTo("14");
        assertThat(estadoTrabajo()).isEqualTo("ENSAYO_OK");
        assertThat(estadoTramite()).isEqualTo("APROBADO");
        assertThat(filas("OVZ_ALTA_ENSAYO")).isEqualTo(1);
        assertThat(filas("OVZ_ALTA_PRESENTADA")).isZero();
    }

    // ---- REAL ----

    @Test
    void realFelizConJustificanteDejaEjecutadoYGuardaElDocumentoAltaBovino() {
        navegador.documentosAlta = List.of(new DocumentoCandidato("doc1", Instant.now().plusSeconds(3600)));
        navegador.pdfs = Map.of("doc1", PdfDePrueba.conLineas("Justificante alta", "Crotal " + TERNERO));

        worker("REAL").procesarUno();

        assertThat(navegador.veces("pulsarAceptarAlta")).isEqualTo(1);
        assertThat(estadoTrabajo()).isEqualTo("EJECUTADO");
        assertThat(estadoTramite()).isEqualTo("EJECUTADO_OVZ");
        assertThat(jdbcTemplate.queryForObject("select count(*) from documento_ovz where tipo = 'ALTA_BOVINO'",
                Integer.class)).isEqualTo(1);
        assertThat(filas("OVZ_ALTA_PRESENTADA")).isEqualTo(1);
    }

    @Test
    void realSinJustificanteSigueEjecutadoElAltaNoFallaPorFaltaDeDi() {
        // D3: el DI puede emitirlo la OVZ o imprimirlo el ganadero; su ausencia NO marca el alta como fallida.
        worker("REAL").procesarUno();

        assertThat(navegador.veces("pulsarAceptarAlta")).isEqualTo(1);
        assertThat(estadoTrabajo()).isEqualTo("EJECUTADO");
        assertThat(estadoTramite()).isEqualTo("EJECUTADO_OVZ");
        assertThat(jdbcTemplate.queryForObject("select count(*) from documento_ovz", Integer.class)).isZero();
    }

    // ---- SEGURO (antes de «Aceptar») ----

    @Test
    void siElCrotalSeleccionadoNoCuadraEsErrorSeguroYNoSePulsaAceptar() {
        navegador.crotalSeleccionado = "ES340000009999";

        worker("REAL").procesarUno();

        assertThat(navegador.veces("pulsarAceptarAlta")).isZero();
        assertThat(claseError()).isEqualTo("SEGURO");
        assertThat(estadoTramite()).isEqualTo("ERROR_OVZ");
        assertThat(motivoTrabajo()).isEqualTo(VerificacionAlta.MOTIVO_CROTAL);
        assertThat(navegador.llamadas).contains("salir");
    }

    @Test
    void siElBotonAceptarDelAltaNoCuadraNoSePulsoYEsSeguro() {
        navegador.fallos.put("pulsarAceptarAlta", new AceptarException());

        worker("REAL").procesarUno();

        assertThat(claseError()).isEqualTo("SEGURO");
        assertThat(estadoTramite()).isEqualTo("ERROR_OVZ");
        assertThat(navegador.veces("listarDocumentosAltaBovino")).isZero();
    }

    // ---- INCIERTO (después de «Aceptar») ----

    @Test
    void unErrorTrasAceptarSinConfirmarEnDocumentosEsIncierto() {
        navegador.fallos.put("pulsarAceptarAlta", new IllegalStateException("timeout tras el clic"));

        worker("REAL").procesarUno();

        assertThat(claseError()).isEqualTo("INCIERTO");
        assertThat(estadoTramite()).isEqualTo("ERROR_OVZ");
        assertThat(navegador.veces("pulsarAceptarAlta")).isEqualTo(1);
        assertThat(filas("OVZ_ALTA_ERROR")).isEqualTo(1);
    }

    @Test
    void unErrorTrasAceptarConElJustificanteEnDocumentosEsEjecutado() {
        navegador.fallos.put("pulsarAceptarAlta", new IllegalStateException("timeout tras el clic"));
        navegador.documentosAlta = List.of(new DocumentoCandidato("doc1", Instant.now().plusSeconds(3600)));
        navegador.pdfs = Map.of("doc1", PdfDePrueba.conLineas("Justificante alta", "Crotal " + TERNERO));

        worker("REAL").procesarUno();

        assertThat(navegador.veces("pulsarAceptarAlta")).isEqualTo(1);
        assertThat(estadoTrabajo()).isEqualTo("EJECUTADO");
        assertThat(estadoTramite()).isEqualTo("EJECUTADO_OVZ");
    }

    // ---- auditoría ----

    @Test
    void laAuditoriaDelAltaNuncaLlevaCrotalNiRegaNiContrasena() {
        navegador.fallos.put("prepararAlta", new NavegacionOvzException("Motivo-alta-para-el-gestor"));

        worker("REAL").procesarUno();

        List<Map<String, Object>> filas = jdbcTemplate.queryForList(
                "select accion, antes, despues from auditoria where accion like 'OVZ_ALTA%'");
        assertThat(filas).hasSize(1);
        String todo = filas.toString();
        assertThat(todo).doesNotContain("Motivo-alta").doesNotContain(TERNERO).doesNotContain(MADRE)
                .doesNotContain(REGA).doesNotContain(PASSWORD);
        assertThat(motivoTrabajo()).isEqualTo("Motivo-alta-para-el-gestor");
    }

    // ---- helpers ----

    private String estadoTrabajo() {
        return jdbcTemplate.queryForObject("select estado from trabajo_ovz where id = ?", String.class, trabajo.getId());
    }

    private String claseError() {
        return jdbcTemplate.queryForObject("select clase_error from trabajo_ovz where id = ?", String.class, trabajo.getId());
    }

    private String motivoTrabajo() {
        return jdbcTemplate.queryForObject("select motivo_error from trabajo_ovz where id = ?", String.class, trabajo.getId());
    }

    private String estadoTramite() {
        return jdbcTemplate.queryForObject("select estado from tramite where id = ?", String.class, tramite.getId());
    }

    private int filas(String accion) {
        return jdbcTemplate.queryForObject("select count(*) from auditoria where accion = ?", Integer.class, accion);
    }
}
