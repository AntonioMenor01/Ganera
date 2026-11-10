package com.ganera.core.demo;

import com.ganera.core.explotacion.AnimalRepository;
import com.ganera.core.explotacion.ExplotacionRepository;
import com.ganera.core.ganadero.GanaderoRepository;
import com.ganera.core.gestoria.UsuarioRepository;
import com.ganera.core.tramite.EstadoTramite;
import com.ganera.core.tramite.OrigenTramite;
import com.ganera.core.tramite.TramiteRepository;
import com.ganera.core.shared.auditoria.LimpiezaAuditoria;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Semilla demo: escribe muchas filas sin auditoría (solo demos locales). Sin GuardiaDml a
 * propósito — no es un camino de producto.
 */
@SpringBootTest
class DemoSemillaServiceTest {

    @Autowired
    private DemoSemillaService semillaService;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private GanaderoRepository ganaderoRepository;
    @Autowired
    private ExplotacionRepository explotacionRepository;
    @Autowired
    private AnimalRepository animalRepository;
    @Autowired
    private TramiteRepository tramiteRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void limpiar() {
        LimpiezaAuditoria.borrar(jdbcTemplate);
        jdbcTemplate.update("delete from tramite_evento");
        jdbcTemplate.update("delete from tramite_crotal");
        jdbcTemplate.update("delete from mensaje_campo");
        jdbcTemplate.update("delete from respuesta_whatsapp");
        jdbcTemplate.update("delete from tramite");
        jdbcTemplate.update("delete from contacto_explotacion");
        jdbcTemplate.update("delete from contacto");
        jdbcTemplate.update("delete from animal");
        jdbcTemplate.update("delete from explotacion");
        jdbcTemplate.update("delete from ganadero");
        jdbcTemplate.update("delete from preferencia_ovz");
        jdbcTemplate.update("delete from suscripcion");
        jdbcTemplate.update("delete from usuario");
        jdbcTemplate.update("delete from gestoria");
    }

    @Test
    void siembraDatosDeTodosLosTiposYEsIdempotente() {
        DemoSemillaService.Resultado primero = semillaService.sembrarSiHaceFalta();
        assertThat(primero.creada()).isTrue();
        assertThat(primero.gestoriaId()).isPositive();

        assertThat(usuarioRepository.findByEmail(DemoSemillaService.EMAIL_DEMO)).isPresent();
        assertThat(ganaderoRepository.count()).isGreaterThanOrEqualTo(4);
        assertThat(explotacionRepository.count()).isGreaterThanOrEqualTo(5);
        assertThat(animalRepository.count()).isGreaterThanOrEqualTo(15);
        assertThat(tramiteRepository.count()).isGreaterThanOrEqualTo(10);

        assertThat(tramiteRepository.findAll()).anyMatch(t -> t.getEstado() == EstadoTramite.PENDIENTE_REVISION);
        assertThat(tramiteRepository.findAll()).anyMatch(t -> t.getEstado() == EstadoTramite.APROBADO);
        assertThat(tramiteRepository.findAll()).anyMatch(t -> t.getEstado() == EstadoTramite.HECHO_EN_OVZ);
        assertThat(tramiteRepository.findAll()).anyMatch(t -> t.getOrigen() == OrigenTramite.MANUAL);
        assertThat(tramiteRepository.findAll()).anyMatch(t -> t.getOrigen() == OrigenTramite.WHATSAPP);

        long tramitesTrasPrimera = tramiteRepository.count();
        DemoSemillaService.Resultado segundo = semillaService.sembrarSiHaceFalta();
        assertThat(segundo.creada()).isFalse();
        assertThat(tramiteRepository.count()).isEqualTo(tramitesTrasPrimera);
    }
}
