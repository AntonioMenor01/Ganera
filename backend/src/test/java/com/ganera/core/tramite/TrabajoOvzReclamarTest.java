package com.ganera.core.tramite;

import com.ganera.core.explotacion.Explotacion;
import com.ganera.core.explotacion.ExplotacionRepository;
import com.ganera.core.ganadero.Ganadero;
import com.ganera.core.ganadero.GanaderoRepository;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * «Reclamar trabajo» del worker (T2, D3) y la restricción de idempotencia de la cola. Sin la guardia DML: reclamar
 * es un paso interno del worker que no deja fila de auditoría (los eventos auditados de la presentación los deja el
 * worker al terminar, T4), y los escenarios siembran trabajos directamente.
 */
@SpringBootTest
class TrabajoOvzReclamarTest {

    @Autowired private TrabajoOvzService trabajoOvzService;
    @Autowired private TrabajoOvzRepository trabajoOvzRepository;
    @Autowired private TramiteRepository tramiteRepository;
    @Autowired private GanaderoRepository ganaderoRepository;
    @Autowired private ExplotacionRepository explotacionRepository;
    @Autowired private GestoriaRepository gestoriaRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void limpiar() {
        jdbcTemplate.update("delete from trabajo_ovz");
        tramiteRepository.deleteAll();
        explotacionRepository.deleteAll();
        ganaderoRepository.deleteAll();
        gestoriaRepository.deleteAll();
    }

    @Test
    void reclamarTomaElPendienteMasAntiguoYLoPasaAEnProceso() {
        Gestoria g = gestoriaRepository.save(new Gestoria("Reclamar A"));
        TrabajoOvz viejo = trabajoPendiente(g, "ES010000000100", Instant.now().minusSeconds(120));
        TrabajoOvz nuevo = trabajoPendiente(g, "ES010000000101", Instant.now());

        Optional<TrabajoOvz> reclamado = trabajoOvzService.reclamarSiguiente();

        assertThat(reclamado).isPresent();
        assertThat(reclamado.get().getId()).isEqualTo(viejo.getId());
        // El trabajo y SU trámite pasan a EN_PROCESO; el otro sigue PENDIENTE.
        assertThat(estadoTrabajo(viejo.getId())).isEqualTo("EN_PROCESO");
        assertThat(estadoTramite(viejo.getTramite().getId())).isEqualTo("EN_PROCESO");
        assertThat(estadoTrabajo(nuevo.getId())).isEqualTo("PENDIENTE");
        assertThat(estadoTramite(nuevo.getTramite().getId())).isEqualTo("APROBADO");
    }

    @Test
    void reclamarSoloTomaPendientes() {
        Gestoria g = gestoriaRepository.save(new Gestoria("Reclamar B"));
        TrabajoOvz enError = trabajoPendiente(g, "ES010000000102", Instant.now());
        enError.setEstado(EstadoTrabajoOvz.ERROR);
        enError.setClaseError(TipoErrorOvz.SEGURO);
        trabajoOvzRepository.saveAndFlush(enError);

        assertThat(trabajoOvzService.reclamarSiguiente()).isEmpty();
    }

    @Test
    void reclamarDevuelveVacioSinTrabajos() {
        assertThat(trabajoOvzService.reclamarSiguiente()).isEmpty();
    }

    @Test
    void noSePuedeCrearUnSegundoTrabajoParaElMismoTramite() {
        Gestoria g = gestoriaRepository.save(new Gestoria("Idempotencia"));
        TrabajoOvz primero = trabajoPendiente(g, "ES010000000103", Instant.now());

        TrabajoOvz segundo = new TrabajoOvz();
        segundo.setGestoria(g);
        segundo.setTramite(primero.getTramite());
        segundo.setGanadero(primero.getGanadero());
        segundo.setTipo(TipoTrabajoOvz.BAJA_MUERTE);
        segundo.setEstado(EstadoTrabajoOvz.PENDIENTE);
        segundo.setCreatedAt(Instant.now());
        segundo.setActualizadoEn(Instant.now());

        // UNIQUE(tramite_id): un segundo trabajo para el mismo trámite es imposible (clave de idempotencia).
        assertThatThrownBy(() -> trabajoOvzRepository.saveAndFlush(segundo))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private TrabajoOvz trabajoPendiente(Gestoria g, String rega, Instant creado) {
        Ganadero ganadero = new Ganadero();
        ganadero.setGestoria(g);
        ganadero.setNombre("Ganadero " + rega);
        ganaderoRepository.save(ganadero);
        Explotacion explotacion = new Explotacion();
        explotacion.setGestoria(g);
        explotacion.setGanadero(ganadero);
        explotacion.setCodigoRega(rega);
        explotacion.setNombre("Finca " + rega);
        explotacionRepository.save(explotacion);
        Tramite tramite = new Tramite();
        tramite.setGestoria(g);
        tramite.setExplotacion(explotacion);
        tramite.setEstado(EstadoTramite.APROBADO);
        tramite.setTipoTramite(TipoTramite.BAJA_MUERTE);
        tramiteRepository.save(tramite);
        TrabajoOvz trabajo = new TrabajoOvz();
        trabajo.setGestoria(g);
        trabajo.setTramite(tramite);
        trabajo.setGanadero(ganadero);
        trabajo.setTipo(TipoTrabajoOvz.BAJA_MUERTE);
        trabajo.setEstado(EstadoTrabajoOvz.PENDIENTE);
        trabajo.setCreatedAt(creado);
        trabajo.setActualizadoEn(creado);
        return trabajoOvzRepository.saveAndFlush(trabajo);
    }

    private String estadoTrabajo(long id) {
        return jdbcTemplate.queryForObject("select estado from trabajo_ovz where id = ?", String.class, id);
    }

    private String estadoTramite(long id) {
        return jdbcTemplate.queryForObject("select estado from tramite where id = ?", String.class, id);
    }
}
