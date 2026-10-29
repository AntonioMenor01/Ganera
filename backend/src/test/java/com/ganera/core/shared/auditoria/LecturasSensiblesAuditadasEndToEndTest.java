package com.ganera.core.shared.auditoria;

import com.ganera.core.auth.LoginRequest;
import com.ganera.core.auth.LoginResponse;
import com.ganera.core.contacto.Contacto;
import com.ganera.core.contacto.ContactoRepository;
import com.ganera.core.explotacion.Explotacion;
import com.ganera.core.explotacion.ExplotacionRepository;
import com.ganera.core.ganadero.Ganadero;
import com.ganera.core.ganadero.GanaderoRepository;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.gestoria.Usuario;
import com.ganera.core.gestoria.UsuarioRepository;
import com.ganera.core.tramite.EstadoTramite;
import com.ganera.core.tramite.Tramite;
import com.ganera.core.tramite.TramiteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Auditoria base — T5 (plan 2026-10-21, D4). Lecturas sensibles auditadas: una fila {@code LECTURA_*} por GET SERVIDO
 * (recurso propio existente), con el actor USUARIO del JWT, ip 127.0.0.1 y request_id = la cabecera X-Request-Id de
 * ESA respuesta. Los listados que no traen texto ni telefonos NO dejan fila; {@code GET /contactos} si
 * ({@code LECTURA_CONTACTOS}, sin id). Un GET a un recurso de otra gestoria deja {@code ACCESO_OTRA_GESTORIA} (D3) y
 * NO {@code LECTURA_*}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LecturasSensiblesAuditadasEndToEndTest {

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private AuditoriaRepository auditoriaRepository;
    @Autowired
    private TramiteRepository tramiteRepository;
    @Autowired
    private ContactoRepository contactoRepository;
    @Autowired
    private ExplotacionRepository explotacionRepository;
    @Autowired
    private GanaderoRepository ganaderoRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private GestoriaRepository gestoriaRepository;

    private Gestoria gestoriaA;
    private Gestoria gestoriaB;
    private Usuario usuarioA;
    private String tokenA;
    private Explotacion explotacionA;
    private Ganadero ganaderoA;
    private Tramite tramiteA;
    private long tramiteBId;

    @BeforeEach
    void preparar() {
        gestoriaA = gestoriaRepository.save(new Gestoria("Lecturas A"));
        gestoriaB = gestoriaRepository.save(new Gestoria("Lecturas B"));
        usuarioA = crearUsuario(gestoriaA, "lecturasA@test.com");
        tokenA = login("lecturasA@test.com");
        explotacionA = nuevaExplotacion(gestoriaA, "ES930000000001");
        ganaderoA = explotacionA.getGanadero();
        Contacto contactoA = nuevoContacto(gestoriaA, "+34600930001");
        tramiteA = nuevoTramite(gestoriaA, contactoA);
        Contacto contactoB = nuevoContacto(gestoriaB, "+34600930002");
        tramiteBId = nuevoTramite(gestoriaB, contactoB).getId();
        LimpiezaAuditoria.borrar(jdbcTemplate);
    }

    @AfterEach
    void limpiar() {
        LimpiezaAuditoria.borrar(jdbcTemplate);
        tramiteRepository.deleteAll();
        contactoRepository.deleteAll();
        explotacionRepository.deleteAll();
        ganaderoRepository.deleteAll();
        usuarioRepository.deleteAll();
        gestoriaRepository.deleteAll();
    }

    @Test
    void getTramiteServidoDejaUnaFilaLecturaTramite() {
        ResponseEntity<String> r = get("/tramites/" + tramiteA.getId());
        assertThat(r.getStatusCode().value()).isEqualTo(200);

        List<Auditoria> filas = filas(gestoriaA);
        assertThat(filas).hasSize(1);
        comun(filas.get(0), AccionAuditoria.LECTURA_TRAMITE, EntidadAuditada.TRAMITE, tramiteA.getId(), r);
    }

    @Test
    void getGanaderoServidoDejaUnaFilaLecturaGanadero() {
        ResponseEntity<String> r = get("/ganaderos/" + ganaderoA.getId());
        assertThat(r.getStatusCode().value()).isEqualTo(200);

        List<Auditoria> filas = filas(gestoriaA);
        assertThat(filas).hasSize(1);
        comun(filas.get(0), AccionAuditoria.LECTURA_GANADERO, EntidadAuditada.GANADERO, ganaderoA.getId(), r);
    }

    @Test
    void getContactosDejaUnaFilaLecturaContactosSinId() {
        ResponseEntity<String> r = get("/contactos");
        assertThat(r.getStatusCode().value()).isEqualTo(200);

        List<Auditoria> filas = filas(gestoriaA);
        assertThat(filas).hasSize(1);
        comun(filas.get(0), AccionAuditoria.LECTURA_CONTACTOS, EntidadAuditada.CONTACTO, null, r);
    }

    @Test
    void losListadosNoSensiblesNoDejanFila() {
        assertThat(get("/tramites").getStatusCode().value()).isEqualTo(200);
        assertThat(get("/ganaderos").getStatusCode().value()).isEqualTo(200);
        assertThat(get("/explotaciones").getStatusCode().value()).isEqualTo(200);
        assertThat(get("/explotaciones/" + explotacionA.getId()).getStatusCode().value()).isEqualTo(200);
        assertThat(get("/explotaciones/" + explotacionA.getId() + "/animales").getStatusCode().value()).isEqualTo(200);

        assertThat(filas(gestoriaA)).isEmpty();
    }

    @Test
    void getDeUnTramiteAjenoDejaAccesoOtraGestoriaYNoLectura() {
        ResponseEntity<String> r = get("/tramites/" + tramiteBId);
        assertThat(r.getStatusCode().value()).isEqualTo(404);

        List<Auditoria> filas = filas(gestoriaA);
        assertThat(filas).extracting(Auditoria::getAccion).containsExactly(AccionAuditoria.ACCESO_OTRA_GESTORIA);
        assertThat(filas.get(0).getEntidadId()).isEqualTo(tramiteBId);
        assertThat(filas(gestoriaB)).isEmpty();
    }

    // --------------------------------------------------------------- helpers

    private void comun(Auditoria fila, AccionAuditoria accion, EntidadAuditada tipo, Long entidadId,
                       ResponseEntity<String> respuesta) {
        assertThat(fila.getAccion()).isEqualTo(accion);
        assertThat(fila.getActorTipo()).isEqualTo(ActorTipo.USUARIO);
        assertThat(fila.getUsuarioId()).isEqualTo(usuarioA.getId());
        assertThat(fila.getGestoria().getId()).isEqualTo(gestoriaA.getId());
        assertThat(fila.getEntidadTipo()).isEqualTo(tipo);
        assertThat(fila.getEntidadId()).isEqualTo(entidadId);
        assertThat(fila.getAntes()).isNull();
        assertThat(fila.getDespues()).isNull();
        assertThat(fila.getIp()).isIn("127.0.0.1", "0:0:0:0:0:0:0:1");
        assertThat(fila.getRequestId()).isNotBlank().isEqualTo(respuesta.getHeaders().getFirst("X-Request-Id"));
    }

    private ResponseEntity<String> get(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenA);
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private List<Auditoria> filas(Gestoria g) {
        return auditoriaRepository.findByGestoriaIdOrderByIdAsc(g.getId());
    }

    private Explotacion nuevaExplotacion(Gestoria gestoria, String codigoRega) {
        Ganadero ganadero = new Ganadero();
        ganadero.setGestoria(gestoria);
        ganadero.setNombre("Ganadero " + codigoRega);
        ganaderoRepository.save(ganadero);
        Explotacion explotacion = new Explotacion();
        explotacion.setGestoria(gestoria);
        explotacion.setGanadero(ganadero);
        explotacion.setCodigoRega(codigoRega);
        explotacion.setNombre("Finca " + codigoRega);
        return explotacionRepository.save(explotacion);
    }

    private Contacto nuevoContacto(Gestoria gestoria, String telefono) {
        Contacto contacto = new Contacto();
        contacto.setTelefono(telefono);
        contacto.setNombre("Contacto lecturas");
        contacto.setGestoria(gestoria);
        return contactoRepository.save(contacto);
    }

    private Tramite nuevoTramite(Gestoria gestoria, Contacto contacto) {
        Tramite tramite = new Tramite();
        tramite.setGestoria(gestoria);
        tramite.setContacto(contacto);
        tramite.setEstado(EstadoTramite.PENDIENTE_REVISION);
        return tramiteRepository.save(tramite);
    }

    private Usuario crearUsuario(Gestoria gestoria, String email) {
        Usuario usuario = new Usuario();
        usuario.setGestoria(gestoria);
        usuario.setEmail(email);
        usuario.setPasswordHash(passwordEncoder.encode("password123"));
        usuario.setNombre("Usuario lecturas");
        usuario.setActivo(true);
        return usuarioRepository.save(usuario);
    }

    private String login(String email) {
        ResponseEntity<LoginResponse> respuesta = restTemplate.postForEntity(
                "/auth/login", new LoginRequest(email, "password123"), LoginResponse.class);
        return respuesta.getBody().token();
    }
}
