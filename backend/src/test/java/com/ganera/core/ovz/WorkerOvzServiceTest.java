package com.ganera.core.ovz;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ganera.core.explotacion.Explotacion;
import com.ganera.core.explotacion.ExplotacionRepository;
import com.ganera.core.ganadero.DescifradoCredencialOvz;
import com.ganera.core.ganadero.Ganadero;
import com.ganera.core.ganadero.GanaderoRepository;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.shared.auditoria.LimpiezaAuditoria;
import com.ganera.core.shared.crypto.CifradorAesGcm;
import com.ganera.core.tramite.EstadoTramite;
import com.ganera.core.tramite.ResolucionCrotal;
import com.ganera.core.tramite.TipoTramite;
import com.ganera.core.tramite.TipoTrabajoOvz;
import com.ganera.core.tramite.EstadoTrabajoOvz;
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
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Orquestador del worker de OVZ (T4) con un {@link NavegadorOvzFalso} escrito a mano. WorkerOvzService no es un bean
 * en el contexto de test (solo con ganera.ovz.worker.activo=true), asi que se construye a mano con los colaboradores
 * reales. Lo esencial: en ENSAYO NUNCA se pulsa «Aceptar»; un error ANTES de «Aceptar» es SEGURO; un error DESPUES
 * es INCIERTO salvo que Documentos confirme la baja, y nunca se reintenta la presentacion.
 */
@SpringBootTest
class WorkerOvzServiceTest {

    private static final String CROTAL = "ES340000009999";
    private static final String REGA = "ES340000000001";
    private static final String PASSWORD = "password-ovz-worker-service-inventada";

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
        gestoria = gestoriaRepository.save(new Gestoria("Worker OVZ"));
        ganadero = new Ganadero();
        ganadero.setGestoria(gestoria);
        ganadero.setNombre("Ganadero worker");
        ganadero.setOvzUsuario("usuario-ovz-worker");
        ganadero.setOvzPasswordCifrada(cifrador.cifrar(PASSWORD));
        ganaderoRepository.save(ganadero);
        Explotacion explotacion = new Explotacion();
        explotacion.setGestoria(gestoria);
        explotacion.setGanadero(ganadero);
        explotacion.setCodigoRega(REGA);
        explotacion.setNombre("Finca worker");
        explotacionRepository.save(explotacion);
        tramite = new Tramite();
        tramite.setGestoria(gestoria);
        tramite.setExplotacion(explotacion);
        tramite.setEstado(EstadoTramite.APROBADO);
        tramite.setTipoTramite(TipoTramite.BAJA_MUERTE);
        tramite.setFechaHecho(LocalDate.of(2026, 11, 10));
        tramite.setNumeroMer("MER-1");
        tramiteRepository.save(tramite);
        TramiteCrotal tc = new TramiteCrotal();
        tc.setGestoria(gestoria);
        tc.setTramite(tramite);
        tc.setCrotalIndicado(CROTAL);
        tc.setCrotal(CROTAL);
        tc.setResolucion(ResolucionCrotal.EN_INVENTARIO);
        tramiteCrotalRepository.save(tc);
        Instant ahora = Instant.now();
        trabajo = new TrabajoOvz();
        trabajo.setGestoria(gestoria);
        trabajo.setTramite(tramite);
        trabajo.setGanadero(ganadero);
        trabajo.setTipo(TipoTrabajoOvz.BAJA_MUERTE);
        trabajo.setEstado(EstadoTrabajoOvz.PENDIENTE);
        trabajo.setCreatedAt(ahora);
        trabajo.setActualizadoEn(ahora);
        trabajoOvzRepository.saveAndFlush(trabajo);

        // Por defecto la pantalla 2 de OVZ cuadra con el trámite.
        navegador.codigoRiia = CROTAL;
        navegador.explotacionUbicacion = REGA + " - Finca worker";
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

    // ------------------------------------------------------------------ ENSAYO

    @Test
    void ensayoFelizLlegaHastaAntesDeAceptarYNuncaPulsaAceptar() {
        worker("ENSAYO").procesarUno();

        assertThat(navegador.veces("pulsarAceptar")).isZero();
        assertThat(navegador.llamadas).contains("iniciarSesion", "prepararBaja", "rellenarFechaMuerte", "salir");
        assertThat(navegador.ultimaClave).isEqualTo(PASSWORD);
        assertThat(estadoTrabajo()).isEqualTo("ENSAYO_OK");
        assertThat(estadoTramite()).isEqualTo("APROBADO");
        assertThat(filas("OVZ_BAJA_ENSAYO")).isEqualTo(1);
        assertThat(filas("OVZ_BAJA_PRESENTADA")).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from documento_ovz", Integer.class)).isZero();
    }

    @Test
    void sinModoOModoDesconocidoSeComportaComoEnsayo() {
        worker("").procesarUno();
        assertThat(navegador.veces("pulsarAceptar")).isZero();
        assertThat(estadoTrabajo()).isEqualTo("ENSAYO_OK");
    }

    @Test
    void enEnsayoRegistraPaso7SesionCerradaSinValoresSensibles() {
        Logger log = (Logger) LoggerFactory.getLogger(WorkerOvzService.class);
        ListAppender<ILoggingEvent> eventos = new ListAppender<>();
        eventos.start();
        log.addAppender(eventos);
        try {
            worker("ENSAYO").procesarUno();
        } finally {
            log.detachAppender(eventos);
            eventos.stop();
        }

        // La sesión de OVZ se cierra también en ensayo (igual que en real) y queda constancia en el log.
        assertThat(navegador.llamadas).contains("salir");
        assertThat(eventos.list).anySatisfy(evento -> {
            assertThat(evento.getLevel()).isEqualTo(Level.INFO);
            assertThat(evento.getFormattedMessage()).contains("PASO 7 OK").contains("sesión de OVZ cerrada");
        });
        // La confirmación de cierre nunca lleva crotal, REGA ni la contraseña.
        assertThat(eventos.list.toString()).doesNotContain(CROTAL).doesNotContain(REGA).doesNotContain(PASSWORD);
    }

    @Test
    void unTrabajoEnsayadoNoSeReclamaOtraVez() {
        worker("ENSAYO").procesarUno();
        navegador.llamadas.clear();

        worker("ENSAYO").procesarUno();

        assertThat(navegador.llamadas).isEmpty();
    }

    // ------------------------------------------------------------------ REAL

    @Test
    void realFelizConJustificanteDejaEjecutadoYGuardaElDocumento() {
        navegador.documentos = List.of(new DocumentoCandidato("doc1", Instant.now().plusSeconds(3600)));
        navegador.pdfs = Map.of("doc1", PdfDePrueba.conLineas("Justificante", "Crotal " + CROTAL));

        worker("REAL").procesarUno();

        assertThat(navegador.veces("pulsarAceptar")).isEqualTo(1);
        assertThat(estadoTrabajo()).isEqualTo("EJECUTADO");
        assertThat(estadoTramite()).isEqualTo("EJECUTADO_OVZ");
        assertThat(jdbcTemplate.queryForObject("select count(*) from documento_ovz", Integer.class)).isEqualTo(1);
        assertThat(filas("OVZ_BAJA_PRESENTADA")).isEqualTo(1);
        assertThat(filas("OVZ_DOCUMENTO_GUARDADO")).isEqualTo(1);
        assertThat(intentos()).isEqualTo(1);
    }

    @Test
    void realSinJustificanteEncontradoQuedaEjecutadoConElDocumentoPendiente() {
        worker("REAL").procesarUno();

        assertThat(navegador.veces("pulsarAceptar")).isEqualTo(1);
        assertThat(estadoTrabajo()).isEqualTo("EJECUTADO");
        assertThat(estadoTramite()).isEqualTo("EJECUTADO_OVZ");
        assertThat(jdbcTemplate.queryForObject("select count(*) from documento_ovz", Integer.class)).isZero();
    }

    // ------------------------------------------------------------------ errores ANTES de «Aceptar» = SEGURO

    @Test
    void siLaVerificacionNoCuadraEsErrorSeguroYNoSePulsaAceptar() {
        navegador.codigoRiia = "ES340000000001-OTRO";

        worker("REAL").procesarUno();

        assertThat(navegador.veces("pulsarAceptar")).isZero();
        assertThat(estadoTrabajo()).isEqualTo("ERROR");
        assertThat(claseError()).isEqualTo("SEGURO");
        assertThat(estadoTramite()).isEqualTo("ERROR_OVZ");
        assertThat(motivoTrabajo()).isEqualTo(VerificacionBaja.MOTIVO_CROTAL);
        assertThat(navegador.llamadas).contains("salir");
    }

    @Test
    void loginConCertificadoEsErrorSeguroConSuMotivo() {
        navegador.fallos.put("iniciarSesion", new LoginCertificadoException());

        worker("REAL").procesarUno();

        assertThat(estadoTrabajo()).isEqualTo("ERROR");
        assertThat(claseError()).isEqualTo("SEGURO");
        assertThat(motivoTrabajo()).isEqualTo(new LoginCertificadoException().getMessage());
        assertThat(navegador.veces("pulsarAceptar")).isZero();
    }

    @Test
    void cualquierFalloDeNavegacionAntesDeAceptarEsSeguro() {
        navegador.fallos.put("prepararBaja", new NavegacionOvzException("No se encontró la pestaña."));

        worker("REAL").procesarUno();

        assertThat(claseError()).isEqualTo("SEGURO");
        assertThat(estadoTramite()).isEqualTo("ERROR_OVZ");
        assertThat(navegador.veces("pulsarAceptar")).isZero();
    }

    @Test
    void siElBotonAceptarNoCuadraNoSePulsoYEsErrorSeguro() {
        navegador.fallos.put("pulsarAceptar", new AceptarException());

        worker("REAL").procesarUno();

        assertThat(claseError()).isEqualTo("SEGURO");
        assertThat(estadoTramite()).isEqualTo("ERROR_OVZ");
        // No hubo consulta de Documentos: no se presentó nada.
        assertThat(navegador.veces("listarDocumentosBajaAnimal")).isZero();
    }

    @Test
    void unGanaderoConCertificadoEsErrorSeguroSinAbrirOvz() {
        jdbcTemplate.update("update ganadero set usa_certificado = true where id = ?", ganadero.getId());

        worker("REAL").procesarUno();

        assertThat(navegador.llamadas).isEmpty();
        assertThat(estadoTrabajo()).isEqualTo("ERROR");
        assertThat(claseError()).isEqualTo("SEGURO");
    }

    @Test
    void unaCredencialCorruptaEsErrorSeguroSinAbrirOvz() {
        String corrupto = Base64.getEncoder().encodeToString(
                "ciphertext-corrupto-inventado-para-el-test-worker-svc".getBytes(StandardCharsets.UTF_8));
        jdbcTemplate.update("update ganadero set ovz_password_cifrada = ? where id = ?", corrupto, ganadero.getId());

        worker("REAL").procesarUno();

        assertThat(navegador.llamadas).isEmpty();
        assertThat(estadoTrabajo()).isEqualTo("ERROR");
        assertThat(claseError()).isEqualTo("SEGURO");
    }

    @Test
    void sinFechaDeMuerteEsErrorSeguroSinAbrirOvz() {
        jdbcTemplate.update("update tramite set fecha_hecho = null where id = ?", tramite.getId());

        worker("REAL").procesarUno();

        assertThat(navegador.llamadas).isEmpty();
        assertThat(claseError()).isEqualTo("SEGURO");
    }

    // ------------------------------------------------------------------ errores DESPUES de «Aceptar» = INCIERTO

    @Test
    void unErrorTrasAceptarSinConfirmarEnDocumentosEsIncierto() {
        navegador.fallos.put("pulsarAceptar", new IllegalStateException("timeout tras el clic"));

        worker("REAL").procesarUno();

        assertThat(estadoTrabajo()).isEqualTo("ERROR");
        assertThat(claseError()).isEqualTo("INCIERTO");
        assertThat(estadoTramite()).isEqualTo("ERROR_OVZ");
        // Nunca se reintenta la presentacion.
        assertThat(navegador.veces("pulsarAceptar")).isEqualTo(1);
        assertThat(filas("OVZ_BAJA_ERROR")).isEqualTo(1);
    }

    @Test
    void unErrorTrasAceptarConElJustificanteEnDocumentosEsEjecutado() {
        navegador.fallos.put("pulsarAceptar", new IllegalStateException("timeout tras el clic"));
        navegador.documentos = List.of(new DocumentoCandidato("doc1", Instant.now().plusSeconds(3600)));
        navegador.pdfs = Map.of("doc1", PdfDePrueba.conLineas("Justificante", "Crotal " + CROTAL));

        worker("REAL").procesarUno();

        assertThat(navegador.veces("pulsarAceptar")).isEqualTo(1);
        assertThat(estadoTrabajo()).isEqualTo("EJECUTADO");
        assertThat(estadoTramite()).isEqualTo("EJECUTADO_OVZ");
        assertThat(jdbcTemplate.queryForObject("select count(*) from documento_ovz", Integer.class)).isEqualTo(1);
    }

    @Test
    void siDocumentosTambienFallaTrasAceptarEsIncierto() {
        navegador.fallos.put("pulsarAceptar", new IllegalStateException("timeout tras el clic"));
        navegador.fallos.put("listarDocumentosBajaAnimal", new NavegacionOvzException("Documentos no responde."));

        worker("REAL").procesarUno();

        assertThat(claseError()).isEqualTo("INCIERTO");
        assertThat(navegador.veces("pulsarAceptar")).isEqualTo(1);
    }

    // ------------------------------------------------------------------ auditoria y cola

    @Test
    void laAuditoriaNuncaLlevaElMotivoNiElCrotalNiElRega() {
        navegador.fallos.put("prepararBaja", new NavegacionOvzException("Motivo-especifico-para-el-gestor"));

        worker("REAL").procesarUno();

        List<Map<String, Object>> filas = jdbcTemplate.queryForList(
                "select accion, antes, despues from auditoria where accion like 'OVZ_BAJA%'");
        assertThat(filas).hasSize(1);
        String todo = filas.toString();
        assertThat(todo).doesNotContain("Motivo-especifico").doesNotContain(CROTAL).doesNotContain(REGA)
                .doesNotContain(PASSWORD);
        assertThat(motivoTrabajo()).isEqualTo("Motivo-especifico-para-el-gestor");
    }

    @Test
    void sinTrabajosPendientesNoHaceNada() {
        jdbcTemplate.update("update trabajo_ovz set estado = 'EJECUTADO'");

        worker("REAL").procesarUno();

        assertThat(navegador.llamadas).isEmpty();
    }

    // ------------------------------------------------------------------ helpers

    private String estadoTrabajo() {
        return jdbcTemplate.queryForObject("select estado from trabajo_ovz where id = ?", String.class, trabajo.getId());
    }

    private String claseError() {
        return jdbcTemplate.queryForObject("select clase_error from trabajo_ovz where id = ?", String.class, trabajo.getId());
    }

    private String motivoTrabajo() {
        return jdbcTemplate.queryForObject("select motivo_error from trabajo_ovz where id = ?", String.class, trabajo.getId());
    }

    private int intentos() {
        return jdbcTemplate.queryForObject("select intentos from trabajo_ovz where id = ?", Integer.class, trabajo.getId());
    }

    private String estadoTramite() {
        return jdbcTemplate.queryForObject("select estado from tramite where id = ?", String.class, tramite.getId());
    }

    private int filas(String accion) {
        return jdbcTemplate.queryForObject("select count(*) from auditoria where accion = ?", Integer.class, accion);
    }
}
