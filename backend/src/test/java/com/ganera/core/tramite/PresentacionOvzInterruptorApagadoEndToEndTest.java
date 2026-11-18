package com.ganera.core.tramite;

import com.ganera.core.auth.LoginRequest;
import com.ganera.core.auth.LoginResponse;
import com.ganera.core.explotacion.Explotacion;
import com.ganera.core.explotacion.ExplotacionRepository;
import com.ganera.core.facturacion.EstadoSuscripcion;
import com.ganera.core.facturacion.Suscripcion;
import com.ganera.core.facturacion.SuscripcionRepository;
import com.ganera.core.ganadero.Ganadero;
import com.ganera.core.ganadero.GanaderoRepository;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.gestoria.Usuario;
import com.ganera.core.gestoria.UsuarioRepository;
import com.ganera.core.shared.auditoria.GuardiaDmlExtension;
import com.ganera.core.shared.auditoria.LimpiezaAuditoria;
import com.ganera.core.shared.crypto.CifradorAesGcm;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Posición APAGADA del interruptor maestro {@code ganera.ovz.presentacion-automatica.activa} (T3): es el valor POR
 * DEFECTO (sin {@code @TestPropertySource}), así que al aprobar un BAJA_MUERTE con credenciales válidas NO se encola
 * ningún trabajo (vía manual). La posición encendida se prueba en {@link PresentacionOvzEndToEndTest}.
 *
 * <p>Es la mutación «encolar con el interruptor apagado»: quitar el gate de {@code encolarSiProcede} pondría este
 * test en rojo (encolaría con el interruptor apagado).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(GuardiaDmlExtension.class)
class PresentacionOvzInterruptorApagadoEndToEndTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private CifradorAesGcm cifrador;
    @Autowired private GestoriaRepository gestoriaRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private GanaderoRepository ganaderoRepository;
    @Autowired private ExplotacionRepository explotacionRepository;
    @Autowired private TramiteRepository tramiteRepository;
    @Autowired private TrabajoOvzRepository trabajoOvzRepository;
    @Autowired private SuscripcionRepository suscripcionRepository;

    private Gestoria gestoria;
    private String token;

    @BeforeEach
    void preparar() {
        gestoria = gestoriaRepository.save(new Gestoria("Interruptor apagado"));
        Usuario usuario = new Usuario();
        usuario.setGestoria(gestoria);
        usuario.setEmail("apagado@test.com");
        usuario.setPasswordHash(passwordEncoder.encode("password123"));
        usuario.setNombre("Usuario apagado");
        usuario.setActivo(true);
        usuarioRepository.save(usuario);
        Suscripcion suscripcion = new Suscripcion();
        suscripcion.setGestoria(gestoria);
        suscripcion.setEstado(EstadoSuscripcion.ACTIVA);
        suscripcionRepository.save(suscripcion);
        token = login();
        LimpiezaAuditoria.borrar(jdbcTemplate);
    }

    @AfterEach
    void limpiar() {
        LimpiezaAuditoria.borrar(jdbcTemplate);
        jdbcTemplate.update("delete from trabajo_ovz");
        jdbcTemplate.update("delete from tramite_crotal");
        tramiteRepository.deleteAll();
        explotacionRepository.deleteAll();
        ganaderoRepository.deleteAll();
        suscripcionRepository.deleteAll();
        usuarioRepository.deleteAll();
        gestoriaRepository.deleteAll();
    }

    @Test
    void aprobarBajaMuerteConCredencialesNoEncolaConElInterruptorApagado() {
        Ganadero ganadero = new Ganadero();
        ganadero.setGestoria(gestoria);
        ganadero.setNombre("Ganadero con credenciales");
        ganadero.setOvzUsuario("ovzuser");
        ganadero.setOvzPasswordCifrada(cifrador.cifrar("ovzpass"));
        ganaderoRepository.save(ganadero);
        Explotacion explotacion = new Explotacion();
        explotacion.setGestoria(gestoria);
        explotacion.setGanadero(ganadero);
        explotacion.setCodigoRega("ES990000000001");
        explotacion.setNombre("Finca apagado");
        explotacionRepository.save(explotacion);
        Tramite tramite = new Tramite();
        tramite.setGestoria(gestoria);
        tramite.setExplotacion(explotacion);
        tramite.setEstado(EstadoTramite.PENDIENTE_REVISION);
        tramite.setTipoTramite(TipoTramite.BAJA_MUERTE);
        tramiteRepository.save(tramite);

        long version = jdbcTemplate.queryForObject("select version from tramite where id = ?", Long.class,
                tramite.getId());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> r = restTemplate.postForEntity("/tramites/" + tramite.getId() + "/aprobar",
                new HttpEntity<>(Map.of("version", version), headers), String.class);

        // El trámite se aprueba igual (vía manual), pero NO se encola nada con el interruptor apagado.
        assertThat(r.getStatusCode().value()).isEqualTo(200);
        assertThat(trabajoOvzRepository.existsByTramiteId(tramite.getId())).isFalse();
        assertThat(acciones()).contains("TRAMITE_APROBADO").doesNotContain("OVZ_TRABAJO_ENCOLADO");
    }

    private List<String> acciones() {
        return jdbcTemplate.queryForList(
                "select accion from auditoria where gestoria_id = ? order by id asc", String.class, gestoria.getId());
    }

    private String login() {
        ResponseEntity<LoginResponse> respuesta = restTemplate.postForEntity(
                "/auth/login", new LoginRequest("apagado@test.com", "password123"), LoginResponse.class);
        return respuesta.getBody().token();
    }
}
